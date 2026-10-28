package com.v2ray.ang.handler

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class RemoteBanner(val id: Int, val text: String, val url: String?, val label: String?)

data class RemoteAnnouncement(val id: Int, val title: String, val text: String, val url: String?)

data class RemoteSponsor(val title: String, val desc: String, val url: String?)

data class RemoteFeatures(val autoFastest: Boolean = false, val onboarding: Boolean = false)

data class RemoteDefaultSub(val name: String, val url: String)

data class RemoteUpdate(val versionCode: Int, val versionName: String, val apkUrl: String, val changelog: String)

data class RemoteLinkItem(val label: String, val url: String)

data class RemoteLinks(
    val channel: String? = null,
    val site: String? = null,
    val privacy: String? = null,
    val source: String? = null,
    val releases: String? = null,
)

data class RemoteSlide(val title: String, val text: String)

/** Content and switches published by the admin bot's Worker (`/config.json`). */
data class RemoteConfig(
    val banner: RemoteBanner? = null,
    val announcement: RemoteAnnouncement? = null,
    val sponsorsTitle: String = "",
    val sponsorsIntro: String = "",
    val sponsors: List<RemoteSponsor> = emptyList(),
    val features: RemoteFeatures = RemoteFeatures(),
    val defaultSub: RemoteDefaultSub? = null,
    val update: RemoteUpdate? = null,
    val support: RemoteLinkItem? = null,
    val links: RemoteLinks = RemoteLinks(),
    val onboarding: List<RemoteSlide> = emptyList(),
)

/** Ids the user already closed or read, so the same banner or announcement is not shown again. */
data class RemoteDismissed(
    val bannerId: Int = 0,
    val announcementId: Int = 0,
    val onboardingSeen: Boolean = false,
)

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
        val feat = root.obj("features")
        val sub = root.obj("defaultSub")?.let { d ->
            val url = d.str("url", 500)
            if (d.bool("enabled") && SubscriptionInfoParser.sanitizeLink(url)?.startsWith("http", true) == true) {
                RemoteDefaultSub(d.str("name", MAX_TITLE).ifEmpty { "SabetNet" }, url)
            } else null
        }
        val upd = root.obj("update")?.let { u ->
            val code = u.int("versionCode")
            val apk = link(u.str("apkUrl", 500))
            if (code > 0 && apk != null) RemoteUpdate(code, u.str("versionName", 40), apk, u.str("changelog", MAX_TEXT)) else null
        }
        val sup = root.obj("support")?.let { s ->
            link(s.str("url", 500))?.let { RemoteLinkItem(s.str("label", MAX_TITLE), it) }
        }
        val lk = root.obj("links")
        val slides = (root.get("onboarding") as? com.google.gson.JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { o ->
                val title = o.str("title", MAX_TITLE)
                if (title.isEmpty()) null else RemoteSlide(title, o.str("text", MAX_TEXT))
            }
            ?.take(8)
            .orEmpty()
        return RemoteConfig(
            banner = banner,
            announcement = announcement,
            sponsorsTitle = page?.str("title", MAX_TITLE).orEmpty(),
            sponsorsIntro = page?.str("intro", MAX_TEXT).orEmpty(),
            sponsors = sponsors,
            features = RemoteFeatures(feat?.bool("autoFastest") == true, feat?.bool("onboarding") == true),
            defaultSub = sub,
            update = upd,
            support = sup,
            links = RemoteLinks(
                channel = lk?.str("channel", 500)?.let { link(it) },
                site = lk?.str("site", 500)?.let { link(it) },
                privacy = lk?.str("privacy", 500)?.let { link(it) },
                source = lk?.str("source", 500)?.let { link(it) },
                releases = lk?.str("releases", 500)?.let { link(it) },
            ),
            onboarding = slides,
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
    private const val PREF_ONBOARDING_SEEN = "pref_remote_onboarding_seen"
    private const val PREF_DEFAULT_SUB_URL = "pref_remote_default_sub_url"
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
                    MmkvManager.decodeSettingsBool(PREF_ONBOARDING_SEEN, false),
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

    fun markOnboardingSeen() {
        MmkvManager.encodeSettings(PREF_ONBOARDING_SEEN, true)
        _dismissed.update { it.copy(onboardingSeen = true) }
    }

    /**
     * Adds the admin's default subscription once per distinct URL. Returns the new subscription so the
     * caller can download it, or null when nothing was added (disabled, already present, or already
     * offered once, so a subscription the user deleted does not come back).
     */
    suspend fun ensureDefaultSubscription(): Pair<String, SubscriptionItem>? = withContext(Dispatchers.IO) {
        val sub = _config.value.defaultSub ?: return@withContext null
        try {
            if (MmkvManager.decodeSettingsString(PREF_DEFAULT_SUB_URL) == sub.url) return@withContext null
            MmkvManager.encodeSettings(PREF_DEFAULT_SUB_URL, sub.url)
            if (MmkvManager.decodeSubscriptions().any { it.subscription.url == sub.url }) return@withContext null
            val id = Utils.getUuid()
            val item = SubscriptionItem(remarks = sub.name, url = sub.url, autoUpdate = true)
            MmkvManager.encodeSubscription(id, item)
            id to item
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Remote config: default subscription failed", e)
            null
        }
    }
}
