package com.v2ray.ang.handler

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class RemoteBanner(val id: Int, val text: String, val url: String?, val label: String?)

data class RemoteAnnouncement(val id: Int, val title: String, val text: String, val url: String?)

data class RemoteSponsor(val title: String, val desc: String, val url: String?)

/** Sponsor and announcement content published by the admin bot's Worker (`/config.json`). */
data class RemoteConfig(
    val banner: RemoteBanner? = null,
    val announcement: RemoteAnnouncement? = null,
    val sponsorsTitle: String = "",
    val sponsorsIntro: String = "",
    val sponsors: List<RemoteSponsor> = emptyList(),
)

/** Ids the user already closed or read, so the same banner or announcement is not shown again. */
data class RemoteDismissed(val bannerId: Int = 0, val announcementId: Int = 0)

/**
 * Pure parser for the Worker's public config. Every field is optional and length-limited: the
 * payload is remote input, so anything malformed is dropped instead of failing the whole config.
 */
object RemoteConfigParser {
    private const val MAX_BODY = 64 * 1024
    private const val MAX_TITLE = 80
    private const val MAX_TEXT = 400
    private const val MAX_SPONSORS = 20

    /** Returns null when [json] is not a JSON object (the caller then keeps its previous config). */
    fun parse(json: String?): RemoteConfig? {
        if (json.isNullOrBlank() || json.length > MAX_BODY) return null
        val root = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return null

        val banner = root.obj("banner")?.let { b ->
            val text = b.str("text", MAX_TEXT)
            if (b.bool("enabled") && text.isNotEmpty()) {
                RemoteBanner(b.int("id"), text, link(b.str("url", 500)), b.str("label", MAX_TITLE).ifEmpty { null })
            } else null
        }
        val announcement = root.obj("announcement")?.let { a ->
            val text = a.str("text", MAX_TEXT)
            if (a.bool("enabled") && text.isNotEmpty()) {
                RemoteAnnouncement(a.int("id"), a.str("title", MAX_TITLE), text, link(a.str("url", 500)))
            } else null
        }
        val page = root.obj("sponsorsPage")
        val sponsors = (root.get("sponsors") as? com.google.gson.JsonArray)
            ?.mapNotNull { (it as? JsonObject) }
            ?.mapNotNull { s ->
                val title = s.str("title", MAX_TITLE)
                if (title.isEmpty()) null else RemoteSponsor(title, s.str("desc", MAX_TEXT), link(s.str("url", 500)))
            }
            ?.take(MAX_SPONSORS)
            .orEmpty()
        return RemoteConfig(
            banner = banner,
            announcement = announcement,
            sponsorsTitle = page?.str("title", MAX_TITLE).orEmpty(),
            sponsorsIntro = page?.str("intro", MAX_TEXT).orEmpty(),
            sponsors = sponsors,
        )
    }

    private fun link(raw: String): String? = SubscriptionInfoParser.sanitizeLink(raw)

    private fun JsonObject.obj(key: String): JsonObject? = (get(key) as? JsonElement)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.str(key: String, max: Int): String {
        val e = get(key)
        if (e == null || !e.isJsonPrimitive || !e.asJsonPrimitive.isString) return ""
        return e.asString.trim().take(max)
    }

    private fun JsonObject.bool(key: String): Boolean {
        val e = get(key)
        return e != null && e.isJsonPrimitive && e.asJsonPrimitive.isBoolean && e.asBoolean
    }

    private fun JsonObject.int(key: String): Int {
        val e = get(key)
        return if (e != null && e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asInt else 0
    }
}

/**
 * Owns the remote sponsor/announcement config: downloads it, keeps the last good copy in MMKV and
 * remembers what the user dismissed. A failed download never changes the cached config and never
 * throws, so it cannot affect startup or the VPN service.
 */
object RemoteConfigManager {
    private const val PREF_CACHE = "pref_remote_config_cache"
    private const val PREF_BANNER_DISMISSED = "pref_remote_banner_dismissed"
    private const val PREF_ANNOUNCEMENT_SEEN = "pref_remote_announcement_seen"
    private const val FETCH_TIMEOUT_MS = 6000

    private val _config = MutableStateFlow(RemoteConfig())
    val config: StateFlow<RemoteConfig> = _config.asStateFlow()

    private val _dismissed = MutableStateFlow(RemoteDismissed())
    val dismissed: StateFlow<RemoteDismissed> = _dismissed.asStateFlow()

    @Volatile
    private var loaded = false

    /** Loads the cached config and dismissed ids once; cheap and safe to call repeatedly. */
    suspend fun load() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        loaded = true
        try {
            RemoteConfigParser.parse(MmkvManager.decodeSettingsString(PREF_CACHE))?.let { c -> _config.update { c } }
            _dismissed.update {
                RemoteDismissed(
                    MmkvManager.decodeSettingsInt(PREF_BANNER_DISMISSED, 0),
                    MmkvManager.decodeSettingsInt(PREF_ANNOUNCEMENT_SEEN, 0),
                )
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Remote config: failed to load cache", e)
        }
    }

    /**
     * Downloads the config directly, then through the local proxy (so it also works while the VPN is
     * the only route out). Returns true when a valid config was applied.
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val url = AppConfig.REMOTE_CONFIG_URL
        if (url.isBlank()) return@withContext false
        try {
            var body = HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = FETCH_TIMEOUT_MS))
            if (RemoteConfigParser.parse(body) == null) {
                body = HttpUtil.getUrlContent(
                    UrlContentRequest(
                        url = url,
                        timeout = FETCH_TIMEOUT_MS,
                        httpPort = SettingsManager.getHttpPort(),
                        proxyUsername = SettingsManager.getSocksUsername(),
                        proxyPassword = SettingsManager.getSocksPassword(),
                    )
                )
            }
            val parsed = RemoteConfigParser.parse(body) ?: return@withContext false
            MmkvManager.encodeSettings(PREF_CACHE, body)
            _config.update { parsed }
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Remote config: refresh failed", e)
            false
        }
    }

    fun dismissBanner(id: Int) {
        MmkvManager.encodeSettings(PREF_BANNER_DISMISSED, id)
        _dismissed.update { it.copy(bannerId = id) }
    }

    fun markAnnouncementSeen(id: Int) {
        MmkvManager.encodeSettings(PREF_ANNOUNCEMENT_SEEN, id)
        _dismissed.update { it.copy(announcementId = id) }
    }
}
