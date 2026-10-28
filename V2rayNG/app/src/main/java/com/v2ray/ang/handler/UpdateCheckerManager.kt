package com.v2ray.ang.handler

import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    /**
     * SabetNet updates come from the admin's own config. The upstream GitHub releases are never
     * queried because they would offer the original v2rayNG build.
     */
    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        RemoteConfigManager.load()
        if (!RemoteConfigManager.refresh() && RemoteConfigManager.config.value.update == null) {
            // Nothing downloaded and nothing cached: surface it as a failed check rather than "up to date".
            throw IllegalStateException("Failed to get response")
        }
        resultFor(RemoteConfigManager.config.value.update, BuildConfig.VERSION_CODE)
    }

    /** An update exists only when the published version code is newer than the installed one. */
    internal fun resultFor(update: RemoteUpdate?, currentVersionCode: Int): CheckUpdateResult =
        if (update != null && update.versionCode > currentVersionCode) {
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = update.versionName.ifEmpty { update.versionCode.toString() },
                releaseNotes = update.changelog,
                downloadUrl = update.apkUrl,
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
}
