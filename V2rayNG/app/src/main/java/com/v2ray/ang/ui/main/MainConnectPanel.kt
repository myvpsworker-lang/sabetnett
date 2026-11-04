package com.v2ray.ang.ui.main

import android.os.SystemClock
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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

/** What the connect panel shows; built from [MainUiState] so the composables stay free of repositories. */
data class ConnectPanelModel(
    val isRunning: Boolean,
    val connectedSinceMs: Long?,
    val serverName: String?,
    /** Negative means the last ping failed; null means the server was never pinged. */
    val pingMs: Long?,
)

fun MainUiState.toConnectPanelModel() = ConnectPanelModel(isRunning, connectedSinceMs, selectedServerName, selectedPingMs)

/** Full-size panel for the collapsed state: big power circle, running time and the server card. */
@Composable
internal fun ConnectPanelFull(model: ConnectPanelModel, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PowerCircle(model.isRunning, size = 164.dp, iconSize = 56.dp, onClick = onToggle)
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(if (model.isRunning) R.string.connect_state_on else R.string.connect_state_off),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (model.isRunning) colorPing else MaterialTheme.colorScheme.onSurfaceVariant,
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
            PowerCircle(model.isRunning, size = 48.dp, iconSize = 22.dp, onClick = onToggle, pulse = false)
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
            Text(
                text = model.serverName ?: stringResource(R.string.connect_server_none),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
private fun PowerCircle(isRunning: Boolean, size: Dp, iconSize: Dp, onClick: () -> Unit, pulse: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val brush = if (isRunning) {
        Brush.linearGradient(listOf(colorConnectGradientStart, colorConnectGradientEnd))
    } else {
        Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceContainerHighest))
    }
    Box(contentAlignment = Alignment.Center) {
        if (isRunning && pulse) PulseRing(size)
        Box(
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = if (isRunning) 14.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = if (isRunning) colorConnectGradientEnd else colors.primary,
                    spotColor = if (isRunning) colorConnectGradientEnd else colors.primary,
                )
                .clip(CircleShape)
                .background(brush)
                .clickable(role = Role.Switch, onClick = onClick),
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
