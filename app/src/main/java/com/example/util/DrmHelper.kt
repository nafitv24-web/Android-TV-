package com.example.util

import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@OptIn(UnstableApi::class)
object DrmHelper {

    data class DrmConfig(
        val schemeUuid: UUID,
        val licenseUrl: String? = null,
        val localKeyBytes: ByteArray? = null,
        val rawKeyString: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val manifestType: String? = null
    )

    data class StreamParsedInfo(
        val cleanUrl: String,
        val drmConfig: DrmConfig?,
        val headers: Map<String, String>
    )

    /**
     * Resolves stream URL, DRM configuration, and HTTP request headers from metadata or URL parameters
     * Supports |, ?|, %7C, ?%7C, and query parameter syntax (?drmScheme=...&drmLicense=...)
     */
    fun extractStreamInfo(
        rawUrl: String,
        itemScheme: String? = null,
        itemLicenseUrl: String? = null,
        itemLicenseKey: String? = null,
        itemHeaders: Map<String, String>? = null,
        itemManifestType: String? = null
    ): StreamParsedInfo {
        var cleanUrl = rawUrl.trim()
        var schemeStr = itemScheme?.takeIf { it.isNotBlank() }
        var licenseKeyOrUrl = itemLicenseUrl?.takeIf { it.isNotBlank() } ?: itemLicenseKey?.takeIf { it.isNotBlank() }
        var manifestType = itemManifestType?.takeIf { it.isNotBlank() }
        val dynamicHeaders = mutableMapOf<String, String>()
        itemHeaders?.let { dynamicHeaders.putAll(it) }

        // Normalize URL-encoded pipes (?%7C, %7C) and ?| to standard |
        val normalized = cleanUrl
            .replace("?%7C", "|", ignoreCase = true)
            .replace("%7C", "|", ignoreCase = true)
            .replace("?|", "|")

        var paramString: String? = null
        if (normalized.contains("|")) {
            val parts = normalized.split("|", limit = 2)
            cleanUrl = parts[0].trim().removeSuffix("?").trim()
            paramString = parts[1].trim()
        } else if (cleanUrl.contains("?") && (
                    cleanUrl.contains("drmScheme=", ignoreCase = true) ||
                    cleanUrl.contains("drmLicense=", ignoreCase = true) ||
                    cleanUrl.contains("license_key=", ignoreCase = true) ||
                    cleanUrl.contains("license_type=", ignoreCase = true) ||
                    cleanUrl.contains("clearkey=", ignoreCase = true)
                )) {
            val base = cleanUrl.substringBefore("?")
            paramString = cleanUrl.substringAfter("?")
            cleanUrl = base.trim()
        }

        if (!paramString.isNullOrBlank()) {
            val pairs = paramString.split("&")
            for (pair in pairs) {
                val kv = pair.split("=", limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].trim().lowercase()
                    val rawV = kv[1].trim()
                    val v = try {
                        java.net.URLDecoder.decode(rawV, "UTF-8")
                    } catch (_: Exception) {
                        rawV
                    }
                    when {
                        k == "drmscheme" || k == "drm_scheme" || k == "license_type" || k == "drm_type" || k == "scheme" -> {
                            if (schemeStr.isNullOrBlank()) {
                                schemeStr = if (v.isNotBlank()) v else "clearkey"
                            }
                        }
                        k == "drmlicense" || k == "drm_license" || k == "license_key" || k == "drm_key" || k == "clearkey" || k == "license_data" || k == "key" || k == "license" -> {
                            if (licenseKeyOrUrl.isNullOrBlank()) {
                                licenseKeyOrUrl = v
                            }
                            if (schemeStr.isNullOrBlank()) {
                                schemeStr = "clearkey"
                            }
                        }
                        k == "manifest_type" || k == "stream_type" || k == "type" -> {
                            if (manifestType.isNullOrBlank()) {
                                manifestType = v
                            }
                        }
                        k == "user-agent" || k == "http-user-agent" -> dynamicHeaders["User-Agent"] = v
                        k == "referer" || k == "referrer" || k == "http-referer" || k == "http-referrer" -> dynamicHeaders["Referer"] = v
                        k == "origin" || k == "http-origin" -> dynamicHeaders["Origin"] = v
                        k == "cookie" || k == "http-cookie" -> dynamicHeaders["Cookie"] = v
                        k.startsWith("drm_header_") -> {
                            val headerName = k.removePrefix("drm_header_")
                            dynamicHeaders[headerName] = v
                        }
                        else -> dynamicHeaders[kv[0].trim()] = v
                    }
                }
            }
        }

        // Auto-detect manifest type if not set
        if (manifestType.isNullOrBlank()) {
            manifestType = when {
                cleanUrl.contains(".mpd", ignoreCase = true) || cleanUrl.contains("dash", ignoreCase = true) -> "mpd"
                cleanUrl.contains(".m3u8", ignoreCase = true) || cleanUrl.contains("hls", ignoreCase = true) -> "hls"
                else -> null
            }
        }

        // Auto-detect DRM scheme if key/license is provided
        if (schemeStr.isNullOrBlank() && !licenseKeyOrUrl.isNullOrBlank()) {
            schemeStr = if (licenseKeyOrUrl.startsWith("http://", ignoreCase = true) || licenseKeyOrUrl.startsWith("https://", ignoreCase = true)) {
                if (licenseKeyOrUrl.contains("widevine", ignoreCase = true)) "widevine" else "clearkey"
            } else {
                "clearkey"
            }
        }

        if (schemeStr.isNullOrBlank() && licenseKeyOrUrl.isNullOrBlank()) {
            return StreamParsedInfo(cleanUrl, null, dynamicHeaders)
        }

        val uuid = when {
            schemeStr?.contains("widevine", ignoreCase = true) == true -> C.WIDEVINE_UUID
            schemeStr?.contains("playready", ignoreCase = true) == true -> C.PLAYREADY_UUID
            else -> C.CLEARKEY_UUID
        }

        val finalLicense = licenseKeyOrUrl?.trim() ?: ""
        if (finalLicense.isBlank()) {
            return StreamParsedInfo(
                cleanUrl,
                DrmConfig(uuid, manifestType = manifestType, headers = dynamicHeaders),
                dynamicHeaders
            )
        }

        // HTTP/HTTPS license server
        if (finalLicense.startsWith("http://", ignoreCase = true) || finalLicense.startsWith("https://", ignoreCase = true)) {
            return StreamParsedInfo(
                cleanUrl,
                DrmConfig(
                    schemeUuid = uuid,
                    licenseUrl = finalLicense,
                    headers = dynamicHeaders,
                    manifestType = manifestType
                ),
                dynamicHeaders
            )
        }

        // Local ClearKey key pairs (hex or JWK JSON)
        val jwkBytes = buildClearKeyJwkBytes(finalLicense)
        return StreamParsedInfo(
            cleanUrl,
            DrmConfig(
                schemeUuid = uuid,
                localKeyBytes = jwkBytes,
                rawKeyString = finalLicense,
                headers = dynamicHeaders,
                manifestType = manifestType
            ),
            dynamicHeaders
        )
    }

    /**
     * Backward-compatible Pair return of (cleanUrl, DrmConfig?)
     */
    fun extractDrmConfig(
        rawUrl: String,
        itemScheme: String? = null,
        itemLicenseUrl: String? = null,
        itemLicenseKey: String? = null,
        itemHeaders: Map<String, String>? = null,
        itemManifestType: String? = null
    ): Pair<String, DrmConfig?> {
        val info = extractStreamInfo(
            rawUrl = rawUrl,
            itemScheme = itemScheme,
            itemLicenseUrl = itemLicenseUrl,
            itemLicenseKey = itemLicenseKey,
            itemHeaders = itemHeaders,
            itemManifestType = itemManifestType
        )
        return Pair(info.cleanUrl, info.drmConfig)
    }

    /**
     * Converts KeyId:Key pairs (hex, base64, or JSON) into valid W3C ClearKey JWK JSON response
     */
    fun buildClearKeyJwkBytes(keyInput: String): ByteArray? {
        var cleanInput = keyInput.trim()
        if (cleanInput.isBlank()) return null

        // Strip any pipe headers: "key:key|User-Agent=..."
        if (cleanInput.contains("|")) {
            cleanInput = cleanInput.substringBefore("|").trim()
        }

        // 1. If it is already a JWK JSON set: e.g. {"keys":[{"kty":"oct",...}]}
        if (cleanInput.startsWith("{") && cleanInput.contains("\"keys\"")) {
            return cleanInput.toByteArray(Charsets.UTF_8)
        }

        // 2. If it is a key-value JSON dictionary: e.g. {"key_id_hex": "key_hex"}
        if (cleanInput.startsWith("{")) {
            try {
                val json = JSONObject(cleanInput)
                val keysArray = JSONArray()
                val iter = json.keys()
                while (iter.hasNext()) {
                    val kid = iter.next()
                    val key = json.getString(kid)
                    val keyObj = formatJwkKeyObject(kid, key)
                    if (keyObj != null) {
                        keysArray.put(keyObj)
                    }
                }
                if (keysArray.length() > 0) {
                    val jwk = JSONObject()
                    jwk.put("keys", keysArray)
                    jwk.put("type", "temporary")
                    return jwk.toString().toByteArray(Charsets.UTF_8)
                }
            } catch (_: Exception) {}
        }

        // 3. Delimited format: handles single or multiple keys in any of the following formats:
        //    "kid:key"
        //    "kid1:key1,kid2:key2"
        //    "kid1:key1 kid2:key2"
        //    "kid1:key1:kid2:key2"
        //    "kid1\nkey1\nkid2\nkey2"
        try {
            val keysArray = JSONArray()
            val tokens = cleanInput.split(Regex("[,;\\s\\n]+")).filter { it.isNotBlank() }

            val rawParts = mutableListOf<String>()
            for (token in tokens) {
                if (token.contains(":")) {
                    val subParts = token.split(":").filter { it.isNotBlank() }
                    rawParts.addAll(subParts)
                } else {
                    rawParts.add(token)
                }
            }

            // Pair up elements (0,1), (2,3), (4,5) ...
            var i = 0
            while (i < rawParts.size - 1) {
                val kidRaw = rawParts[i].trim()
                val keyRaw = rawParts[i + 1].trim()
                val keyObj = formatJwkKeyObject(kidRaw, keyRaw)
                if (keyObj != null) {
                    keysArray.put(keyObj)
                }
                i += 2
            }

            if (keysArray.length() > 0) {
                val jwk = JSONObject()
                jwk.put("keys", keysArray)
                jwk.put("type", "temporary")
                return jwk.toString().toByteArray(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun formatJwkKeyObject(kidRaw: String, keyRaw: String): JSONObject? {
        val kidB64 = toBase64UrlSafe(kidRaw)
        val keyB64 = toBase64UrlSafe(keyRaw)
        if (kidB64.isBlank() || keyB64.isBlank()) return null

        val obj = JSONObject()
        obj.put("kty", "oct")
        obj.put("k", keyB64)
        obj.put("kid", kidB64)
        return obj
    }

    private fun toBase64UrlSafe(raw: String): String {
        val clean = raw.trim().replace("\"", "").replace("'", "")
        if (clean.isBlank()) return ""

        // If it's a 32-character or standard hex string (with or without dashes)
        val hexClean = clean.replace("-", "").replace("0x", "").replace(":", "")
        if (hexClean.length >= 16 && hexClean.length % 2 == 0 && hexClean.matches(Regex("^[0-9a-fA-F]+$"))) {
            val bytes = hexStringToByteArray(hexClean)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()
        }

        // If it's standard Base64, convert to URL-safe Base64 without padding
        return try {
            val decoded = Base64.decode(clean, Base64.DEFAULT)
            Base64.encodeToString(decoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()
        } catch (_: Exception) {
            clean.replace("+", "-").replace("/", "_").trimEnd('=').trim()
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Builds a DrmSessionManager for ExoPlayer
     */
    fun createDrmSessionManager(
        config: DrmConfig,
        httpDataSourceFactory: DataSource.Factory
    ): DrmSessionManager? {
        return try {
            if (config.localKeyBytes != null) {
                // ClearKey local key callback with JWK response
                val callback = object : androidx.media3.exoplayer.drm.MediaDrmCallback {
                    override fun executeProvisionRequest(
                        uuid: UUID,
                        request: androidx.media3.exoplayer.drm.ExoMediaDrm.ProvisionRequest
                    ): ByteArray {
                        return ByteArray(0)
                    }

                    override fun executeKeyRequest(
                        uuid: UUID,
                        request: androidx.media3.exoplayer.drm.ExoMediaDrm.KeyRequest
                    ): ByteArray {
                        return config.localKeyBytes
                    }
                }
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(config.schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(true)
                    .setPlayClearSamplesWithoutKeys(true)
                    .setUseDrmSessionsForClearContent(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
                    .build(callback)
            } else if (!config.licenseUrl.isNullOrBlank()) {
                // HTTP License callback for online DRM servers
                val callback = HttpMediaDrmCallback(config.licenseUrl, httpDataSourceFactory)
                config.headers.forEach { (k, v) ->
                    callback.setKeyRequestProperty(k, v)
                }
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(config.schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(true)
                    .setPlayClearSamplesWithoutKeys(true)
                    .setUseDrmSessionsForClearContent(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
                    .build(callback)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
