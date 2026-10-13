package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.util.DownloadState
import com.example.util.MovieDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service that keeps high-speed movie downloads running uninterrupted
 * even when the user minimizes the app, locks the device, or switches to another app.
 */
class MovieDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "nafi_movie_download_channel"
        const val CHANNEL_NAME = "মুভি ডাউনলোড নোটিফিকেশন"
        const val FOREGROUND_NOTIF_ID = 998877

        const val ACTION_START_DOWNLOAD = "com.example.service.ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.service.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.example.service.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.example.service.ACTION_RESUME_DOWNLOAD"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_SERVICE"
        const val EXTRA_MOVIE_ID = "extra_movie_id"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, MovieDownloadService::class.java).apply {
                    action = ACTION_START_DOWNLOAD
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, MovieDownloadService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressCollectorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        startForegroundWithNotification(
            title = "মুভি ডাউনলোডার সক্রিয়",
            statusText = "ব্যাকগ্রাউন্ডে দ্রুত ডাউনলোড চলছে...",
            progress = 0
        )
        listenToDownloadProgress()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val movieId = intent?.getStringExtra(EXTRA_MOVIE_ID)

        when (action) {
            ACTION_CANCEL_DOWNLOAD -> {
                if (!movieId.isNullOrBlank()) {
                    MovieDownloadManager.cancelDownload(movieId)
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                if (!movieId.isNullOrBlank()) {
                    MovieDownloadManager.pauseDownload(movieId)
                }
            }
            ACTION_RESUME_DOWNLOAD -> {
                if (!movieId.isNullOrBlank()) {
                    MovieDownloadManager.resumeDownload(applicationContext, movieId)
                }
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundAndSelf()
            }
            else -> {
                acquireWakeLock()
            }
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "NAFITV:MovieDownloadWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(6 * 60 * 60 * 1000L) // 6 hours safety timeout
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun listenToDownloadProgress() {
        progressCollectorJob?.cancel()
        progressCollectorJob = serviceScope.launch {
            MovieDownloadManager.downloadsState.collectLatest { downloadsMap ->
                val activeDownloads = downloadsMap.values.filter {
                    it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PENDING
                }

                if (activeDownloads.isEmpty()) {
                    // No active downloads left, wait slightly then stop service cleanly
                    stopForegroundAndSelf()
                } else {
                    val primary = activeDownloads.first()
                    val count = activeDownloads.size
                    val title = if (count > 1) {
                        "📥 ${primary.title} (+${count - 1} টি মুভি)"
                    } else {
                        "📥 ডাউনলোড চলছে: ${primary.title}"
                    }
                    val statusText = "${primary.downloadedSizeFormatted} / ${primary.totalSizeFormatted} • ⚡ ${primary.speedFormatted} (${primary.progressPercent}%)"

                    updateForegroundNotification(
                        title = title,
                        statusText = statusText,
                        progress = primary.progressPercent,
                        activeMovieId = primary.movieId
                    )
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "মুভি ডাউনলোডের লাইভ প্রগ্রেস ও স্ট্যাটাস"
                enableVibration(false)
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        statusText: String,
        progress: Int,
        activeMovieId: String? = null
    ): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingAppIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(statusText)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingAppIntent)

        if (!activeMovieId.isNullOrBlank()) {
            val pauseIntent = Intent(this, MovieDownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_MOVIE_ID, activeMovieId)
            }
            val pausePendingIntent = PendingIntent.getService(
                this,
                activeMovieId.hashCode() + 10,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.mipmap.ic_launcher, "⏸️ পজ", pausePendingIntent)

            val cancelIntent = Intent(this, MovieDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_MOVIE_ID, activeMovieId)
            }
            val cancelPendingIntent = PendingIntent.getService(
                this,
                activeMovieId.hashCode() + 20,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.mipmap.ic_launcher, "বাতিল", cancelPendingIntent)
        }

        return builder.build()
    }

    private fun startForegroundWithNotification(title: String, statusText: String, progress: Int) {
        val notification = buildNotification(title, statusText, progress)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    FOREGROUND_NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(FOREGROUND_NOTIF_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun updateForegroundNotification(
        title: String,
        statusText: String,
        progress: Int,
        activeMovieId: String? = null
    ) {
        val notification = buildNotification(title, statusText, progress, activeMovieId)
        try {
            NotificationManagerCompat.from(this).notify(FOREGROUND_NOTIF_ID, notification)
        } catch (_: Exception) {}
    }

    private fun stopForegroundAndSelf() {
        releaseWakeLock()
        progressCollectorJob?.cancel()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        progressCollectorJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
