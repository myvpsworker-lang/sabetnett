package com.v2ray.ang.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R
import com.v2ray.ang.handler.RemoteBanner
import com.v2ray.ang.handler.RemoteConfig
import com.v2ray.ang.handler.RemoteDialog
import com.v2ray.ang.handler.RemoteSlide
import com.v2ray.ang.handler.RemoteSponsor
import com.v2ray.ang.handler.SubscriptionWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BANNER_ROTATION_MS = 6000L

/**
 * Sponsor banners above the server list. Several active banners rotate; a tap opens the link, and the X
 * (only when the admin allows it) hides that banner for good.
 */
@Composable
fun RemoteBannerCard(
    banners: List<RemoteBanner>,
    onOpenLink: (RemoteBanner, String) -> Unit,
    onDismiss: (Int) -> Unit,
) {
    if (banners.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    val ids = banners.map { it.id }
    LaunchedEffect(ids) {
        index = 0
        while (ids.size > 1) {
            delay(BANNER_ROTATION_MS)
            index = (index + 1) % ids.size
        }
    }
    val banner = banners[index.coerceIn(0, banners.lastIndex)]
    val container = banner.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val content = when {
        banner.colorArgb == null -> MaterialTheme.colorScheme.onSecondaryContainer
        container.luminance() > 0.5f -> Color.Black
        else -> Color.White
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(targetState = banner, modifier = Modifier.weight(1f), label = "banner") { b ->
                val url = b.url
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (url != null) Modifier.clickable(role = Role.Button) { onOpenLink(b, url) } else Modifier)
                        .padding(start = 16.dp, end = if (b.dismissible) 0.dp else 16.dp, top = 12.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = b.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (url != null && !b.label.isNullOrEmpty()) {
                        Text(
                            text = b.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = content.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            if (banner.dismissible) {
                IconButton(onClick = { onDismiss(banner.id) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_24dp),
                        contentDescription = stringResource(R.string.action_close),
                        tint = content,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** Shows the single remote message the picker chose; each kind reports its own dismissal. */
@Composable
fun RemoteDialogHost(
    dialog: RemoteDialog,
    onOpenLink: (String) -> Unit,
    onAction: (MainAction) -> Unit,
    slides: List<RemoteSlide>,
) {
    when (dialog) {
        RemoteDialog.None -> Unit
        RemoteDialog.Onboarding -> OnboardingScreen(slides) { onAction(MainAction.FinishOnboarding) }
        is RemoteDialog.Emergency -> MessageDialog(
            title = dialog.message.title,
            text = dialog.message.text,
            url = dialog.message.url,
            onOpenLink = onOpenLink,
            onClose = { onAction(MainAction.DismissEmergency(dialog.message.id)) },
        )
        is RemoteDialog.WhatsNew -> MessageDialog(
            title = dialog.info.title,
            text = dialog.info.text,
            url = null,
            onOpenLink = onOpenLink,
            onClose = { onAction(MainAction.DismissWhatsNew(dialog.info.versionCode)) },
        )
        is RemoteDialog.Announcement -> MessageDialog(
            title = dialog.announcement.title,
            text = dialog.announcement.text,
            url = dialog.announcement.url,
            onOpenLink = onOpenLink,
            onClose = { onAction(MainAction.DismissAnnouncement(dialog.announcement.id)) },
        )
    }
}

@Composable
private fun MessageDialog(
    title: String,
    text: String,
    url: String?,
    onOpenLink: (String) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = if (title.isNotEmpty()) {
            { Text(title) }
        } else null,
        text = { Text(text) },
        confirmButton = {
            if (url != null) {
                TextButton(onClick = { onOpenLink(url); onClose() }) { Text(stringResource(R.string.remote_open_link)) }
            } else {
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = if (url != null) {
            { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } }
        } else null
    )
}

@Composable
fun SponsorsDialog(content: RemoteConfig, onOpenSponsor: (RemoteSponsor, String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(content.sponsorsTitle.ifEmpty { stringResource(R.string.title_pref_promotion) }) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (content.sponsorsIntro.isNotEmpty()) {
                    item(key = "intro") {
                        Text(content.sponsorsIntro, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // Keyed by the sponsor's id from the admin panel, never by list position.
                itemsIndexed(content.sponsors, key = { _, s -> s.id }) { _, sponsor ->
                    val url = sponsor.url
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (url != null) Modifier.clickable(role = Role.Button) { onOpenSponsor(sponsor, url) } else Modifier),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(sponsor.title, style = MaterialTheme.typography.titleSmall)
                            if (sponsor.desc.isNotEmpty()) {
                                Text(sponsor.desc, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

/**
 * Full-screen first-run slides: a big emoji, title and text per page, page dots, and one button that
 * reads "Next" until the last slide, where it becomes "Get started".
 */
@Composable
fun OnboardingScreen(slides: List<RemoteSlide>, onFinish: () -> Unit) {
    if (slides.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage >= slides.lastIndex
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.45f), colors.background)))
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (!isLast) {
                        TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                    val slide = slides[page]
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = slide.emoji, fontSize = 104.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(28.dp))
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = slide.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                    repeat(slides.size) { i ->
                        Box(
                            Modifier
                                .size(if (i == pagerState.currentPage) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (i == pagerState.currentPage) colors.primary else colors.outlineVariant)
                        )
                    }
                }
                Button(
                    onClick = {
                        if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        text = stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/** Warning strip shown above the server list when the subscription runs out soon. */
@Composable
fun SubscriptionWarningCard(warning: SubscriptionWarning) {
    val text = when (warning) {
        SubscriptionWarning.Expired -> stringResource(R.string.warn_expired)
        SubscriptionWarning.ExpiresToday -> stringResource(R.string.warn_expiry_today)
        is SubscriptionWarning.ExpiresIn -> stringResource(R.string.warn_expiry_days, warning.days)
        is SubscriptionWarning.LowData -> stringResource(R.string.warn_low_data, warning.percentLeft)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
