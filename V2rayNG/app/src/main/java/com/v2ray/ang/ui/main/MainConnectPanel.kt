package com.v2ray.ang.ui.main

import android.os.SystemClock
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.ConnectionTimeFormatter
import com.v2ray.ang.ui.compose.colorConnectGradientEnd
import com.v2ray.ang.ui.compose.colorConnectGradientStart
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import kotlinx.coroutines.delay

private const val PING_SLOW_MS = 500L
private const val PENDING_TIMEOUT_MS = 8000L
private val CIRCLE_MAX = 164.dp
private val CIRCLE_MIN = 88.dp
/** Height of everything in the full panel except the circle: status, timer, server card and paddings. */
private val PANEL_FIXED_HEIGHT = 190.dp

/** What the connect panel shows; built from [MainUiState] so the composables stay free of repositories. */
data class ConnectPanelModel(
    val isRunning: Boolean,
    val connectedSinceMs: Long?,
    val serverName: String?,
    val groupName: String?,
    /** Negative means the last ping failed; null means the server was never pinged. */
    val pingMs: Long?,
)

fun MainUiState.toConnectPanelModel() = ConnectPanelModel(isRunning, connectedSinceMs, selectedServerName, selectedGroupName, selectedPingMs)

/** Full-size panel for the collapsed state: big power circle, running time and the server card. */
@Composable
internal fun ConnectPanelFull(model: ConnectPanelModel, onToggle: () -> Unit, availableHeight: Dp, modifier: Modifier = Modifier) {
    // The circle shrinks when a warning card or a small screen leaves little height, so the server card is never cut off;
    // the column also scrolls as a last resort.
    val circle = (availableHeight - PANEL_FIXED_HEIGHT).coerceIn(CIRCLE_MIN, CIRCLE_MAX)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = availableHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val toggle = rememberOptimisticToggle(model.isRunning, onToggle)
        PowerCircle(toggle.shownOn, toggle.busy, size = circle, iconSize = circle / 3, onClick = toggle.onClick)
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(
                when {
                    toggle.busy && toggle.shownOn -> R.string.connect_state_connecting
                    toggle.busy -> R.string.connect_state_disconnecting
                    model.isRunning -> R.string.connect_state_on
                    else -> R.string.connect_state_off
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (toggle.shownOn) colorPing else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = rememberElapsedText(model.connectedSinceMs),
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        ServerPingCard(model)
    }
}

/** Slim panel under the open server list: small circle, timer, server name and ping on one row. */
@Composable
internal fun ConnectPanelCompact(model: ConnectPanelModel, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val toggle = rememberOptimisticToggle(model.isRunning, onToggle)
            PowerCircle(toggle.shownOn, toggle.busy, size = 48.dp, iconSize = 22.dp, onClick = toggle.onClick, pulse = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = rememberElapsedText(model.connectedSinceMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = model.serverName ?: stringResource(R.string.connect_server_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PingChip(model.pingMs)
        }
    }
}

@Composable
private fun ServerPingCard(model: ConnectPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (model.isRunning) colorPing else MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.serverName ?: stringResource(R.string.connect_server_none),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!model.groupName.isNullOrEmpty()) {
                    Text(
                        text = model.groupName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            PingChip(model.pingMs)
        }
    }
}

@Composable
private fun PingChip(pingMs: Long?) {
    val (text, color) = when {
        pingMs == null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        pingMs < 0 -> stringResource(R.string.connect_ping_failed) to colorPingRed
        else -> stringResource(R.string.connect_ping_ms, pingMs) to if (pingMs <= PING_SLOW_MS) colorPing else colorPingRed
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Round on/off button: gradient and a soft pulse ring while connected, muted while disconnected. */
@Composable
private fun PowerCircle(isRunning: Boolean, busy: Boolean, size: Dp, iconSize: Dp, onClick: () -> Unit, pulse: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "powerPress")
    val brush = if (isRunning) {
        Brush.linearGradient(listOf(colorConnectGradientStart, colorConnectGradientEnd))
    } else {
        Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceContainerHighest))
    }
    Box(contentAlignment = Alignment.Center) {
        if ((isRunning || busy) && pulse) PulseRing(size)
        Box(
            modifier = Modifier
                .size(size)
                .scale(pressScale)
                .shadow(
                    elevation = if (isRunning) 14.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = if (isRunning) colorConnectGradientEnd else colors.primary,
                    spotColor = if (isRunning) colorConnectGradientEnd else colors.primary,
                )
                .clip(CircleShape)
                .background(brush)
                .clickable(interactionSource = interaction, indication = null, role = Role.Switch) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_power_24dp),
                contentDescription = stringResource(R.string.acc_toggle_vpn),
                tint = if (isRunning) Color.White else colors.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun PulseRing(size: Dp) {
    val transition = rememberInfiniteTransition(label = "panelPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "panelPulseScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "panelPulseAlpha",
    )
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .background(colorConnectGradientEnd, CircleShape)
    )
}

/**
 * Makes a tap feel instant: the circle flips to its target look immediately while the service starts or
 * stops, and falls back to the real state when it arrives or after [PENDING_TIMEOUT_MS] (for example when
 * the VPN permission dialog is declined). Purely visual; the real toggle still goes through [onToggle].
 */
private class OptimisticToggle(val shownOn: Boolean, val busy: Boolean, val onClick: () -> Unit)

@Composable
private fun rememberOptimisticToggle(isRunning: Boolean, onToggle: () -> Unit): OptimisticToggle {
    var pending by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isRunning) { pending = null }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(PENDING_TIMEOUT_MS)
            pending = null
        }
    }
    return OptimisticToggle(
        shownOn = pending ?: isRunning,
        busy = pending != null,
        onClick = {
            pending = !isRunning
            onToggle()
        },
    )
}

/** Ticks once a second while connected and returns the running time as text. */
@Composable
private fun rememberElapsedText(sinceMs: Long?): String {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sinceMs) {
        while (sinceMs != null) {
            now = SystemClock.elapsedRealtime()
            delay(1000L)
        }
    }
    return ConnectionTimeFormatter.format(if (sinceMs == null) 0L else now - sinceMs)
}
