package com.v2ray.ang.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.RemoteAnnouncement
import com.v2ray.ang.handler.RemoteBanner
import com.v2ray.ang.handler.RemoteConfig

/** Sponsor banner shown above the server list; tap opens its link, the X closes it for good. */
@Composable
fun RemoteBannerCard(banner: RemoteBanner, onOpenLink: (String) -> Unit, onDismiss: (Int) -> Unit) {
    val url = banner.url
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (url != null) Modifier.clickable(role = Role.Button) { onOpenLink(url) } else Modifier
                    )
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = banner.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (url != null && !banner.label.isNullOrEmpty()) {
                    Text(
                        text = banner.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = { onDismiss(banner.id) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24dp),
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** One-time announcement. Shown again only when the admin bumps its id. */
@Composable
fun RemoteAnnouncementDialog(
    announcement: RemoteAnnouncement,
    onOpenLink: (String) -> Unit,
    onDismiss: (Int) -> Unit,
) {
    val url = announcement.url
    AlertDialog(
        onDismissRequest = { onDismiss(announcement.id) },
        title = if (announcement.title.isNotEmpty()) {
            { Text(announcement.title) }
        } else null,
        text = { Text(announcement.text) },
        confirmButton = {
            if (url != null) {
                TextButton(onClick = { onOpenLink(url); onDismiss(announcement.id) }) {
                    Text(stringResource(R.string.remote_open_link))
                }
            } else {
                TextButton(onClick = { onDismiss(announcement.id) }) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = if (url != null) {
            { TextButton(onClick = { onDismiss(announcement.id) }) { Text(stringResource(R.string.action_close)) } }
        } else null
    )
}

@Composable
fun SponsorsDialog(content: RemoteConfig, onOpenLink: (String) -> Unit, onDismiss: () -> Unit) {
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
                // Sponsors have no id in the config, so the position is only a Compose key here and is never
                // persisted or dispatched; the link string is what an action carries.
                itemsIndexed(content.sponsors, key = { index, s -> "$index:${s.title}" }) { _, sponsor ->
                    val url = sponsor.url
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (url != null) Modifier.clickable(role = Role.Button) { onOpenLink(url) } else Modifier),
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
