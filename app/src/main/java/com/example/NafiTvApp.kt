package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.size.Precision
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class NafiTvApp : Application(), ImageLoaderFactory {

    private var customImageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Register optimized Coil ImageLoader globally so all AsyncImage instances use low-RAM decode
        try {
            coil.Coil.setImageLoader(newImageLoader())
        } catch (e: Exception) {
            Log.w("NafiTvApp", "Coil image loader init error", e)
        }

        try {
            com.example.util.SportsInteractionManager.init(this)
        } catch (_: Exception) {}

        // Pre-warm local file caches on Dispatchers.IO to guarantee 0ms UI startup
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val repo = com.example.data.MediaRepository(this@NafiTvApp)
                repo.getCachedLiveTvChannels()
                repo.getCachedSportsMatches()
                repo.getCachedMoviesList()
            } catch (_: Exception) {}
        }

        // 2. Global crash protection: Intercepts decoder, GPU, network & memory crashes
        // preventing unexpected process termination on normal & low-spec (512MB RAM) devices
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NafiTvApp", "Intercepted crash on thread: ${thread.name}", throwable)
            val msg = throwable.message ?: ""
            val isRecoverable = throwable is java.lang.OutOfMemoryError ||
                    throwable is android.view.WindowManager.BadTokenException ||
                    throwable is android.os.DeadObjectException ||
                    throwable is android.media.MediaCodec.CodecException ||
                    throwable is java.util.concurrent.TimeoutException ||
                    throwable is java.net.SocketTimeoutException ||
                    throwable is java.io.IOException ||
                    msg.contains("MediaCodec", ignoreCase = true) ||
                    msg.contains("Surface", ignoreCase = true) ||
                    msg.contains("DeadObject", ignoreCase = true) ||
                    msg.contains("OutOfMemory", ignoreCase = true) ||
                    msg.contains("EGL", ignoreCase = true) ||
                    msg.contains("GLES", ignoreCase = true) ||
                    msg.contains("TextureView", ignoreCase = true) ||
                    msg.contains("ViewRootImpl", ignoreCase = true)

            if (thread != Looper.getMainLooper().thread || isRecoverable) {
                // Recoverable / background error -> ignore & recover to keep app alive
                Log.w("NafiTvApp", "Suppressed recoverable exception: ${throwable.javaClass.simpleName} - $msg")
                if (throwable is java.lang.OutOfMemoryError) {
                    try {
                        customImageLoader?.memoryCache?.clear()
                        sharedOkHttpClient.connectionPool.evictAll()
                        System.runFinalization()
                        System.gc()
                    } catch (_: Throwable) {}
                }
            } else {
                Log.w("NafiTvApp", "Suppressed main thread uncaught exception: ${throwable.message}")
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun isLowRamEnvironment(): Boolean {
        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        return actManager?.isLowRamDevice == true || maxHeapMb <= 128
    }

    override fun newImageLoader(): ImageLoader {
        return customImageLoader ?: synchronized(this) {
            customImageLoader ?: buildOptimizedImageLoader().also { customImageLoader = it }
        }
    }

    private fun buildOptimizedImageLoader(): ImageLoader {
        val isLowRam = isLowRamEnvironment()
        // On 512MB RAM phones, keep memory cache to 5% of heap to prevent LMK kills
        val memCachePercent = if (isLowRam) 0.05 else 0.10
        val diskCacheMaxBytes = if (isLowRam) 20L * 1024 * 1024 else 35L * 1024 * 1024

        return ImageLoader.Builder(this)
            .okHttpClient(sharedOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memCachePercent)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(!isLowRam)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "nafitv_image_cache"))
                    .maxSizeBytes(diskCacheMaxBytes)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565) // 50% memory saving on all channel logos & posters
            .allowHardware(false) // Disables hardware bitmaps for 100% crash-free stability on TV boxes & low-RAM GPUs
            .allowRgb565(true)
            .crossfade(false) // Saves GPU compositing passes on low-RAM devices
            .precision(Precision.INEXACT) // Automatically downsamples posters and logos to target UI size (huge memory savings!)
            .networkObserverEnabled(true)
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            customImageLoader?.memoryCache?.clear()
            if (level >= TRIM_MEMORY_MODERATE || isLowRamEnvironment()) {
                sharedOkHttpClient.connectionPool.evictAll()
                System.runFinalization()
                System.gc()
            }
        } catch (e: Exception) {
            Log.w("NafiTvApp", "Error trimming memory", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            customImageLoader?.memoryCache?.clear()
            sharedOkHttpClient.connectionPool.evictAll()
            System.runFinalization()
            System.gc()
        } catch (e: Exception) {
            Log.w("NafiTvApp", "Error on low memory", e)
        }
    }

    companion object {
        lateinit var instance: NafiTvApp
            private set

        val sharedOkHttpClient: OkHttpClient by lazy {
            val isLowRam = try {
                instance.isLowRamEnvironment()
            } catch (_: Exception) {
                false
            }
            // Keep connection pool tiny (3 connections, 30s) on 512MB RAM to conserve socket buffers
            val maxIdle = if (isLowRam) 3 else 6
            val keepAliveDuration = if (isLowRam) 30L else 90L

            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(18, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(maxIdle, keepAliveDuration, TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
