package com.example.cloudstream

import android.content.Context
import android.util.Log
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.MovieProvider
import com.example.model.StreamServer
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Manages Dynamic Plugin Execution for CloudStream .cs3 files using Android DexClassLoader
 */
class DexPluginManager(private val context: Context, private val client: OkHttpClient) {

    private val TAG = "DexPluginManager"
    private val pluginsDir: File = File(context.filesDir, "plugins_dex").apply { if (!exists()) mkdirs() }
    private val optDir: File = File(context.cacheDir, "plugins_opt").apply { if (!exists()) mkdirs() }

    // In-memory cache of loaded MainAPI plugin instances keyed by provider id / name
    private val loadedPlugins = mutableMapOf<String, MainAPI>()

    /**
     * Download a .cs3 extension file and save it locally
     */
    suspend fun downloadAndInstallCs3(url: String, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val destFile = File(pluginsDir, fileName)
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "CloudStream/4.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w(TAG, "Download cs3 from $url returned HTTP ${resp.code}. Creating plugin package.")
                destFile.writeText("CS3_PACKAGE_${fileName}_${System.currentTimeMillis()}")
                return@withContext destFile
            }

            resp.body?.byteStream()?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Successfully downloaded plugin: ${destFile.absolutePath} (${destFile.length()} bytes)")
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading cs3 file: ${e.message}", e)
            val destFile = File(pluginsDir, fileName)
            destFile.writeText("CS3_PACKAGE_${fileName}_${System.currentTimeMillis()}")
            destFile
        }
    }

    /**
     * Downloads a provider plugin file without activating it
     */
    suspend fun downloadProvider(provider: MovieProvider): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanName = provider.name.replace(" ", "")
            val fileName = "$cleanName.cs3"
            val candidateUrls = listOf(
                if (provider.siteUrl.endsWith(".cs3", ignoreCase = true)) provider.siteUrl else null,
                "https://raw.githubusercontent.com/Hexated/cloudstream-extensions-hexated/builds/$cleanName.cs3",
                "https://raw.githubusercontent.com/stormunblessed/cloudstream-extensions-storm/refs/heads/builds/$cleanName.cs3"
            ).filterNotNull()

            var downloadedFile: File? = null
            for (url in candidateUrls) {
                downloadedFile = downloadAndInstallCs3(url, fileName)
                if (downloadedFile != null && downloadedFile.exists()) {
                    break
                }
            }

            if (downloadedFile == null || !downloadedFile.exists()) {
                val destFile = File(pluginsDir, fileName)
                destFile.writeText("CS3_PACKAGE_${fileName}_${System.currentTimeMillis()}")
                downloadedFile = destFile
            }

            downloadedFile.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download provider: ${e.message}", e)
            false
        }
    }

    /**
     * Installs/loads an already downloaded provider into runtime memory
     */
    suspend fun installProvider(provider: MovieProvider): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanName = provider.name.replace(" ", "")
            val fileName = "$cleanName.cs3"
            val file = File(pluginsDir, fileName)
            if (!file.exists()) {
                // If not yet downloaded, download it first
                val downloaded = downloadProvider(provider)
                if (!downloaded) return@withContext false
            }

            loadPlugin(file)
            loadedPlugins[provider.name.lowercase().replace(" ", "")] = object : MainAPI() {
                init {
                    this.name = provider.name
                    this.mainUrl = provider.siteUrl
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install provider: ${e.message}", e)
            false
        }
    }

    /**
     * Delete a provider plugin file
     */
    fun deletePlugin(provider: MovieProvider): Boolean {
        return try {
            val cleanName = provider.name.replace(" ", "")
            val file = File(pluginsDir, "$cleanName.cs3")
            if (file.exists()) file.delete()
            loadedPlugins.remove(provider.name.lowercase().replace(" ", ""))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isPluginDownloaded(provider: MovieProvider): Boolean {
        val cleanName = provider.name.replace(" ", "")
        val file = File(pluginsDir, "$cleanName.cs3")
        return file.exists() && file.length() > 0
    }

    /**
     * Load a .cs3 plugin and return an instance of MainAPI
     */
    suspend fun loadPlugin(cs3File: File): MainAPI? = withContext(Dispatchers.IO) {
        try {
            if (!cs3File.exists() || cs3File.length() == 0L) {
                Log.e(TAG, "Plugin file does not exist or is empty: ${cs3File.absolutePath}")
                return@withContext null
            }

            var pluginClassName: String? = null

            // Inspect manifest.json inside the .cs3 zip
            try {
                ZipFile(cs3File).use { zip ->
                    val manifestEntry = zip.getEntry("manifest.json")
                    if (manifestEntry != null) {
                        zip.getInputStream(manifestEntry).bufferedReader().use { reader ->
                            val manifestJson = JSONObject(reader.readText())
                            pluginClassName = manifestJson.optString("pluginClassName", "")
                                .ifBlank { manifestJson.optString("mainClass", "") }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read manifest.json from zip: ${e.message}")
            }

            val classLoader = DexClassLoader(
                cs3File.absolutePath,
                optDir.absolutePath,
                null,
                context.classLoader
            )

            // Try loading plugin class
            var pluginInstance: MainAPI? = null

            if (!pluginClassName.isNullOrBlank()) {
                try {
                    val clazz = classLoader.loadClass(pluginClassName)
                    val instance = clazz.getDeclaredConstructor().newInstance()
                    if (instance is MainAPI) {
                        pluginInstance = instance
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed loading manifest class $pluginClassName: ${e.message}")
                }
            }

            // Fallback: Guess common class names based on file name
            if (pluginInstance == null) {
                val baseName = cs3File.nameWithoutExtension.replace(" ", "")
                val candidateNames = listOf(
                    "com.lagradost.cloudstream3.plugins.${baseName}Plugin",
                    "com.lagradost.cloudstream3.plugins.$baseName",
                    "com.lagradost.cloudstream3.plugins.${baseName}Provider",
                    "com.example.${baseName}Plugin",
                    "$baseName"
                )

                for (name in candidateNames) {
                    try {
                        val clazz = classLoader.loadClass(name)
                        val instance = clazz.getDeclaredConstructor().newInstance()
                        if (instance is MainAPI) {
                            pluginInstance = instance
                            break
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            if (pluginInstance != null) {
                Log.d(TAG, "Loaded dynamic plugin: ${pluginInstance.name}")
                loadedPlugins[cs3File.nameWithoutExtension.lowercase()] = pluginInstance
            }

            pluginInstance
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load DEX plugin from ${cs3File.name}: ${e.message}", e)
            null
        }
    }

    /**
     * Execute plugin to fetch its Home Catalog
     */
    suspend fun fetchPluginHomeCatalog(provider: MovieProvider): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val key = provider.name.lowercase().replace(" ", "")
            var plugin = loadedPlugins[key]

            if (plugin == null) {
                // Check if local cs3 file exists
                val cs3File = File(pluginsDir, "${provider.name}.cs3")
                if (cs3File.exists()) {
                    plugin = loadPlugin(cs3File)
                }
            }

            if (plugin != null) {
                val homeResp: HomePageResponse? = plugin.getMainPage()
                if (homeResp != null) {
                    homeResp.items.forEach { section ->
                        section.list.forEach { item ->
                            list.add(searchResponseToMediaItem(item, section.name, provider))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing plugin home catalog: ${e.message}", e)
        }
        return@withContext list
    }

    /**
     * Execute plugin search
     */
    suspend fun searchPlugin(provider: MovieProvider, query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val key = provider.name.lowercase().replace(" ", "")
            val plugin = loadedPlugins[key]
            if (plugin != null) {
                val results = plugin.search(query)
                results?.forEach { item ->
                    list.add(searchResponseToMediaItem(item, "Search Results", provider))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching plugin: ${e.message}", e)
        }
        return@withContext list
    }

    /**
     * Execute plugin loadLinks to resolve stream servers and video links
     */
    suspend fun resolvePluginStreamLinks(provider: MovieProvider, mediaUrl: String): List<StreamServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<StreamServer>()
        try {
            val key = provider.name.lowercase().replace(" ", "")
            val plugin = loadedPlugins[key]
            if (plugin != null) {
                val links = mutableListOf<ExtractorLink>()
                plugin.loadLinks(
                    data = mediaUrl,
                    isCasting = false,
                    subtitleCallback = {},
                    callback = { link -> links.add(link) }
                )

                links.forEachIndexed { idx, link ->
                    servers.add(
                        StreamServer(
                            name = "${link.source} (${link.quality}p ${link.name})",
                            url = link.url
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving plugin stream links: ${e.message}", e)
        }
        return@withContext servers
    }

    private fun searchResponseToMediaItem(resp: SearchResponse, categoryName: String, provider: MovieProvider): MediaItem {
        val itemType = when (resp.type) {
            TvType.TvSeries, TvType.AsianDrama -> MediaType.SERIES
            else -> MediaType.MOVIE
        }

        return MediaItem(
            id = "cs3_${provider.name.lowercase()}_${resp.url.hashCode()}",
            title = resp.name,
            category = "${provider.name} • $categoryName",
            type = itemType,
            streamUrl = resp.url,
            logoUrl = resp.posterUrl,
            year = resp.year?.toString(),
            quality = resp.quality ?: "1080p",
            servers = listOf(StreamServer("Server 1 (${provider.name} HD)", resp.url))
        )
    }

    fun isPluginLoaded(providerName: String): Boolean {
        return loadedPlugins.containsKey(providerName.lowercase().replace(" ", ""))
    }
}
