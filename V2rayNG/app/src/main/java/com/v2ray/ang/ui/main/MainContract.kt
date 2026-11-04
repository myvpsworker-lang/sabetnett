package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.handler.RemoteBanner
import com.v2ray.ang.handler.RemoteConfig
import com.v2ray.ang.handler.RemoteDialog

/** Locale-neutral state formatted only when it reaches the main UI. */
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val status: MainStatus = MainStatus.Disconnected,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null,
    /** Active remote banners the user has not closed, shown in rotation. */
    val banners: List<RemoteBanner> = emptyList(),
    /** The single remote message dialog to show now (slides, emergency, what's new or announcement). */
    val remoteDialog: RemoteDialog = RemoteDialog.None,
    /** Latest remote config (sponsors page, links, warnings, slides). */
    val sponsorsContent: RemoteConfig = RemoteConfig(),
    val showSponsors: Boolean = false,
    /** True while the server list is open; false shows the connect circle instead. */
    val serversExpanded: Boolean = false,
    /** Elapsed-realtime (ms) when the tunnel came up in this app session; null while disconnected. */
    val connectedSinceMs: Long? = null,
    /** Name of the selected server, for the connect panel. */
    val selectedServerName: String? = null,
    /** Last ping of the selected server in ms; negative means the last test failed, null means never tested. */
    val selectedPingMs: Long? = null,
)

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object ToggleServersExpanded : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object TestRealAllServers : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data class UpdateGroupSubscription(val groupId: String) : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.v2ray.ang.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class ShareQRCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data object DismissQRCodeDialog : MainAction
    data class DismissBanner(val id: Int) : MainAction
    data class DismissAnnouncement(val id: Int) : MainAction
    data class DismissEmergency(val id: Int) : MainAction
    data class DismissWhatsNew(val versionCode: Int) : MainAction
    data object FinishOnboarding : MainAction
    /** Opens a banner (`b`) or sponsor (`s`) link; the Activity opens it and the ViewModel counts the click. */
    data class OpenPromoLink(val kind: String, val id: Int, val url: String) : MainAction
    data class SetTheme(val dark: Boolean) : MainAction
    data object ShowSponsors : MainAction
    data object HideSponsors : MainAction
    data class OpenSubscriptionLink(val url: String) : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data object LocateHandled : MainAction
}
