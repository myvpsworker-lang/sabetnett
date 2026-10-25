package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.colorConnectGradientEnd
import com.v2ray.ang.ui.compose.colorConnectGradientStart
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.launch

private val CONNECT_BUTTON_SIZE = 68.dp

/**
 * Bottom connection panel: a rounded sheet with the connection status on the left (tap to test
 * the current server) and a large gradient connect button on the right (tap to start/stop).
 * While connected the button gets a soft pulsing ring.
 */
@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotationAnim.snapTo(0f)
        }
    }

    val buttonBrush = if (isRunning) {
        Brush.linearGradient(listOf(colorConnectGradientStart, colorConnectGradientEnd))
    } else {
        Brush.linearGradient(listOf(colors.primary, colors.secondary))
    }
    val statusDotColor = if (isRunning) {
        colorFabActive
    } else if (isDarkTheme) {
        colorFabInactiveDark
    } else {
        colorFabInactiveLight
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = colors.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = { onAction(MainAction.TestCurrentServer) })
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics {
                            contentDescription = displayText
                        }
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Box(contentAlignment = Alignment.Center) {
                if (isRunning) {
                    PulseRing()
                }
                Box(
                    modifier = Modifier
                        .size(CONNECT_BUTTON_SIZE)
                        .shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            ambientColor = colors.primary,
                            spotColor = if (isRunning) colorFabActive else colors.primary
                        )
                        .clip(CircleShape)
                        .background(buttonBrush)
                        .clickable(role = Role.Button) {
                            if (!isRunning) {
                                scope.launch {
                                    rotationAnim.animateTo(
                                        targetValue = 720f,
                                        animationSpec = tween(durationMillis = 3000)
                                    )
                                }
                            }
                            onAction(MainAction.ToggleService)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                        else painterResource(R.drawable.ic_play_24dp),
                        contentDescription = stringResource(
                            if (isRunning) R.string.acc_stop else R.string.acc_start
                        ),
                        tint = Color.White,
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer { rotationZ = rotationAnim.value }
                    )
                }
            }
        }
    }
}

/** Soft expanding ring shown behind the connect button while the service is running. */
@Composable
private fun PulseRing() {
    val transition = rememberInfiniteTransition(label = "connectPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(CONNECT_BUTTON_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(colorFabActive)
    )
}
