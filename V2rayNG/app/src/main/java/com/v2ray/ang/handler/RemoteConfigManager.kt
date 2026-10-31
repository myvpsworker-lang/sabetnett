package com.v2ray.ang.handler

import com.google.gson.JsonArray
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

/** [colorArgb] is a validated `#RRGGBB` background (opaque ARGB) or null for the theme default. */
data class RemoteBanner(
    val id: Int,
    val text: String,
    val url: String?,
    val label: String?,
    val colorArgb: Int? = null,
    val dismissible: Boolean = true,
    /** Epoch milliseconds; 0 means no limit. */
    val startMs: Long = 0,
    val endMs: Long = 0,
) {
    fun isActiveAt(nowMs: Long): Boolean = (startMs == 0L || startMs <= nowMs) && (endMs == 0L || endMs > nowMs)
}

data class RemoteAnnouncement(val id: Int, val title: String, val text: String, val url: String?)

data class RemoteEmergency(val id: Int, val title: String, val text: String, val url: String?)

data class RemoteWhatsNew(val versionCode: Int, val title: String, val text: String)

data class RemoteSponsor(val id: Int, val title: String, val desc: String, val url: String?)

data class RemoteFeatures(val autoFastest: Boolean = false, val onboarding: Boolean = false, val smartReconnect: Boolean = false)

/** A system notification the admin sends to every install; [id] changes whenever the admin re-sends it. */
data class RemotePush(val id: Int, val title: String, val text: String, val url: String?)

/** [accentArgb] is a validated opaque `#RRGGBB` accent for the app palette, or null for the built-in one. */
data class RemoteTheme(val accentArgb: Int? = null)

data class RemoteWarnings(val enabled: Boolean = false, val expiryDays: Int = 3, val lowPercent: Int = 10)

data class RemoteDefaultSub(val name: String, val url: String)

data class RemoteUpdate(val versionCode: Int, val versionName: String, val apkUrl: String, val changelog: String)

data class RemoteLinkItem(val label: String, val url: String)

data class RemoteLinks(
    val channel: String? = null,
    val site: String? = null,
    val privacy: String? = null,
    val source: String? = null,
    val releases: String? = null,
    /** Replaces the Telegram icon link on the subscription card. */
    val subscriptionTelegram: String? = null,
)

data class RemoteSlide(val emoji: String, val title: String, val text: String)

/** Content and switches published by the admin bot's Worker (`/config.json`). */
data class RemoteConfig(
    val banners: List<RemoteBanner> = emptyList(),
    val announcement: RemoteAnnouncement? = null,
    val emergency: RemoteEmergency? = null,
    val push: RemotePush? = null,
    val theme: RemoteTheme = RemoteTheme(),
    val whatsNew: RemoteWhatsNew? = null,
    val sponsorsTitle: String = "",
    val sponsorsIntro: String = "",
    val sponsors: List<RemoteSponsor> = emptyList(),
    val features: RemoteFeatures = RemoteFeatures(),
    val warnings: RemoteWarnings = RemoteWarnings(),
    val defaultSub: RemoteDefaultSub? = null,
    val update: RemoteUpdate? = null,
    val support: RemoteLinkItem? = null,
    val links: RemoteLinks = RemoteLinks(),
    val onboarding: List<RemoteSlide> = emptyList(),
)

/** What the user already closed or read, so the same item is not shown again. */
data class RemoteDismissed(
    val bannerIds: Set<Int> = emptySet(),
    val announcementId: Int = 0,
    val onboardingSeen: Boolean = false,
    val whatsNewSeen: Int = 0,
    val pushSeenId: Int = 0,
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
    private const val MAX_BANNERS = 5
    private const val MAX_SLIDES = 8
    private val COLOR_RE = Regex("^#[0-9a-fA-F]{6}$")

    /** Returns null when [json] is not a JSON object (the caller then keeps its previous config). */
    fun parse(json: String?): RemoteConfig? {
        if (json.isNullOrBlank() || json.length > MAX_BODY) return null
        val root = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return null

        val page = root.obj("sponsorsPage")
        val feat = root.obj("features")
        val warn = root.obj("warnings")
        val lk = root.obj("links")
        return RemoteConfig(
            banners = parseBanners(root),
            announcement = root.obj("announcement")?.let { a ->
                val text = a.str("text", MAX_TEXT)
                if (a.bool("enabled") && text.isNotEmpty()) {
                    RemoteAnnouncement(a.int("id"), a.str("title", MAX_TITLE), text, link(a.str("url", 500)))
                } else null
            },
            emergency = root.obj("emergency")?.let { m ->
                val text = m.str("text", MAX_TEXT)
                if (m.bool("enabled") && text.isNotEmpty()) {
                    RemoteEmergency(m.int("id"), m.str("title", MAX_TITLE), text, link(m.str("url", 500)))
                } else null
            },
            push = root.obj("push")?.let { m ->
                val text = m.str("text", MAX_TEXT)
                if (m.bool("enabled") && text.isNotEmpty() && m.int("id") > 0) {
                    RemotePush(m.int("id"), m.str("title", MAX_TITLE), text, link(m.str("url", 500)))
                } else null
            },
            theme = RemoteTheme(root.obj("theme")?.str("accent", 7)?.let { parseColor(it) }),
            whatsNew = root.obj("whatsNew")?.let { w ->
                val text = w.str("text", MAX_TEXT)
                val code = w.int("versionCode")
                if (code > 0 && text.isNotEmpty()) RemoteWhatsNew(code, w.str("title", MAX_TITLE), text) else null
            },
            sponsorsTitle = page?.str("title", MAX_TITLE).orEmpty(),
            sponsorsIntro = page?.str("intro", MAX_TEXT).orEmpty(),
            sponsors = (root.get("sponsors") as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.mapIndexedNotNull { index, s ->
                    val title = s.str("title", MAX_TITLE)
                    // Sponsors of an older Worker have no id; their position is a stable stand-in.
                    if (title.isEmpty()) null
                    else RemoteSponsor(s.int("id").takeIf { it > 0 } ?: (index + 1), title, s.str("desc", MAX_TEXT), link(s.str("url", 500)))
                }
                ?.take(MAX_SPONSORS)
                .orEmpty(),
            features = RemoteFeatures(feat?.bool("autoFastest") == true, feat?.bool("onboarding") == true, feat?.bool("smartReconnect") == true),
            warnings = RemoteWarnings(
                enabled = warn?.bool("enabled") == true,
                expiryDays = (warn?.int("expiryDays") ?: 3).coerceIn(0, 60),
                lowPercent = (warn?.int("lowPercent") ?: 10).coerceIn(0, 90),
            ),
            defaultSub = root.obj("defaultSub")?.let { d ->
                val url = d.str("url", 500)
                if (d.bool("enabled") && SubscriptionInfoParser.sanitizeLink(url)?.startsWith("http", true) == true) {
                    RemoteDefaultSub(d.str("name", MAX_TITLE).ifEmpty { "SabetNet" }, url)
                } else null
            },
            update = root.obj("update")?.let { u ->
                val code = u.int("versionCode")
                val apk = link(u.str("apkUrl", 500))
                if (code > 0 && apk != null) RemoteUpdate(code, u.str("versionName", 40), apk, u.str("changelog", MAX_TEXT)) else null
            },
            support = root.obj("support")?.let { s ->
                link(s.str("url", 500))?.let { RemoteLinkItem(s.str("label", MAX_TITLE), it) }
            },
            links = RemoteLinks(
                channel = lk?.str("channel", 500)?.let { link(it) },
                site = lk?.str("site", 500)?.let { link(it) },
                privacy = lk?.str("privacy", 500)?.let { link(it) },
                source = lk?.str("source", 500)?.let { link(it) },
                releases = lk?.str("releases", 500)?.let { link(it) },
                subscriptionTelegram = lk?.str("subscriptionTelegram", 500)?.let { link(it) },
            ),
            onboarding = (root.get("onboarding") as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.mapNotNull { o ->
                    val title = o.str("title", MAX_TITLE)
                    if (title.isEmpty()) null else RemoteSlide(o.str("emoji", 8).ifEmpty { "✨" }, title, o.str("text", MAX_TEXT))
                }
                ?.take(MAX_SLIDES)
                .orEmpty(),
        )
    }

    /** `banners` (schema 2) or, from an older Worker, the single `banner`. */
    private fun parseBanners(root: JsonObject): List<RemoteBanner> {
        val array = root.get("banners") as? JsonArray
        if (array != null) {
            return array.mapNotNull { it as? JsonObject }.mapNotNull { b ->
                val text = b.str("text", MAX_TEXT)
                if (text.isEmpty()) null else RemoteBanner(
                    id = b.int("id"),
                    text = text,
                    url = link(b.str("url", 500)),
                    label = b.str("label", MAX_TITLE).ifEmpty { null },
                    colorArgb = parseColor(b.str("color", 7)),
                    dismissible = if (b.has("dismissible")) b.bool("dismissible") else true,
                    startMs = b.long("start"),
                    endMs = b.long("end"),
                )
            }.take(MAX_BANNERS)
        }
        val legacy = root.obj("banner") ?: return emptyList()
        val text = legacy.str("text", MAX_TEXT)
        if (!legacy.bool("enabled") || text.isEmpty()) return emptyList()
        return listOf(RemoteBanner(legacy.int("id"), text, link(legacy.str("url", 500)), legacy.str("label", MAX_TITLE).ifEmpty { null }))
    }

    /** `#RRGGBB` to opaque ARGB, or null for anything else. */
    fun parseColor(raw: String): Int? =
        if (COLOR_RE.matches(raw)) (0xFF000000L or raw.substring(1).toLong(16)).toInt() else null

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

    private fun JsonObject.long(key: String): Long {
        val e = get(key)
        return if (e != null && e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asLong else 0L
    }
}

/**
 * Owns the remote sponsor/announcement config: downloads it, keeps the last good copy in MMKV and
 * remembers what the user dismissed. A failed download never changes the cached config and never
 * throws, so it cannot affect startup or the VPN service.
 */
object RemoteConfigManager {
    private const val PREF_CACHE = "pref_remote_config_cache"
    private const val PREF_BANNERS_DISMISSED = "pref_remote_banners_dismissed"
    private const val PREF_ANNOUNCEMENT_SEEN = "pref_remote_announcement_seen"
    private const val PREF_PUSH_SEEN = "pref_remote_push_seen"
    private const val PREF_ONBOARDING_SEEN = "pref_remote_onboarding_seen"
    private const val PREF_WHATS_NEW_SEEN = "pref_remote_whats_new_seen"
    private const val PREF_DEFAULT_SUB_URL = "pref_remote_default_sub_url"
    private const val PREF_INSTALL_ID = "pref_remote_install_id"
    private const val PREF_LAST_PING_DAY = "pref_remote_last_ping_day"
    private const val FETCH_TIMEOUT_MS = 6000
    private const val BEACON_TIMEOUT_MS = 4000
    private const val MAX_DISMISSED_BANNERS = 50
    private const val MILLIS_PER_DAY = 86_400_000L

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
                    bannerIds = decodeIds(MmkvManager.decodeSettingsString(PREF_BANNERS_DISMISSED)),
                    announcementId = MmkvManager.decodeSettingsInt(PREF_ANNOUNCEMENT_SEEN, 0),
                    onboardingSeen = MmkvManager.decodeSettingsBool(PREF_ONBOARDING_SEEN, false),
                    whatsNewSeen = MmkvManager.decodeSettingsInt(PREF_WHATS_NEW_SEEN, 0),
                    pushSeenId = MmkvManager.decodeSettingsInt(PREF_PUSH_SEEN, 0),
                )
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Remote config: failed to load cache", e)
        }
    }

    /** Direct request first, then through the local proxy so it also works while the VPN is the only route out. */
    private fun fetchText(url: String, timeoutMs: Int): String? {
        val direct = HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = timeoutMs))
        if (direct != null) return direct
        return HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = timeoutMs,
                httpPort = SettingsManager.getHttpPort(),
                proxyUsername = SettingsManager.getSocksUsername(),
                proxyPassword = SettingsManager.getSocksPassword(),
            )
        )
    }

    /** Downloads and applies the config. Returns true when a valid config was applied. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val url = AppConfig.REMOTE_CONFIG_URL
        if (url.isBlank()) return@withContext false
        try {
            val body = fetchText(url, FETCH_TIMEOUT_MS)
            val parsed = RemoteConfigParser.parse(body) ?: return@withContext false
            MmkvManager.encodeSettings(PREF_CACHE, body)
            _config.update { parsed }
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Remote config: refresh failed", e)
            false
        }
    }

    private fun beaconBase(): String? = AppConfig.REMOTE_CONFIG_URL.takeIf { it.isNotBlank() }?.substringBeforeLast('/')

    /** Counts a tap on a banner (`b`) or sponsor (`s`). Best effort: failures are ignored. */
    suspend fun reportClick(kind: String, id: Int) = withContext(Dispatchers.IO) {
        if ((kind != "b" && kind != "s") || id <= 0) return@withContext
        val base = beaconBase() ?: return@withContext
        try {
            fetchText("$base/c/$kind/$id", BEACON_TIMEOUT_MS)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Remote config: click report failed, kind=$kind id=$id", e)
        }
    }

    /** Sends the anonymous once-a-day "active user" beacon (random install id, no personal data). */
    suspend fun pingIfDue() = withContext(Dispatchers.IO) {
        val base = beaconBase() ?: return@withContext
        try {
            val day = System.currentTimeMillis() / MILLIS_PER_DAY
            if (MmkvManager.decodeSettingsString(PREF_LAST_PING_DAY) == day.toString()) return@withContext
            val id = MmkvManager.decodeSettingsString(PREF_INSTALL_ID)?.takeIf { it.matches(Regex("[a-f0-9]{16}")) }
                ?: Utils.getUuid().replace("-", "").take(16).lowercase().also { MmkvManager.encodeSettings(PREF_INSTALL_ID, it) }
            if (fetchText("$base/p/$id", BEACON_TIMEOUT_MS) != null) MmkvManager.encodeSettings(PREF_LAST_PING_DAY, day.toString())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Remote config: daily ping failed", e)
        }
    }

    fun dismissBanner(id: Int) {
        val ids = (_dismissed.value.bannerIds + id).toList().takeLast(MAX_DISMISSED_BANNERS).toSet()
        MmkvManager.encodeSettings(PREF_BANNERS_DISMISSED, ids.joinToString(","))
        _dismissed.update { it.copy(bannerIds = ids) }
    }

    fun markAnnouncementSeen(id: Int) {
        MmkvManager.encodeSettings(PREF_ANNOUNCEMENT_SEEN, id)
        _dismissed.update { it.copy(announcementId = id) }
    }

    fun markPushSeen(id: Int) {
        MmkvManager.encodeSettings(PREF_PUSH_SEEN, id)
        _dismissed.update { it.copy(pushSeenId = id) }
    }

    fun markWhatsNewSeen(versionCode: Int) {
        MmkvManager.encodeSettings(PREF_WHATS_NEW_SEEN, versionCode)
        _dismissed.update { it.copy(whatsNewSeen = versionCode) }
    }

    /** Finishing the first-run slides also counts as having read the current "what's new". */
    fun markOnboardingSeen() {
        MmkvManager.encodeSettings(PREF_ONBOARDING_SEEN, true)
        _dismissed.update { it.copy(onboardingSeen = true) }
        _config.value.whatsNew?.let { markWhatsNewSeen(maxOf(it.versionCode, _dismissed.value.whatsNewSeen)) }
    }

    internal fun decodeIds(raw: String?): Set<Int> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

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
