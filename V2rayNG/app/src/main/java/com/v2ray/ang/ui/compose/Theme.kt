package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.RemoteConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// "Aurora" palette: deep midnight surfaces, periwinkle primary, cyan accent.
private val LightColor = lightColorScheme(
    primary = Color(0xFF4F5BD5), // Indigo
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFE0E3FF), // Pale Indigo
    onPrimaryContainer = Color(0xFF0B1262), // Deep Indigo
    secondary = Color(0xFF0891B2), // Cyan
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFCDF3FB), // Pale Cyan
    onSecondaryContainer = Color(0xFF002E38), // Deep Cyan
    tertiary = Color(0xFF059669), // Emerald
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFB7F0D8), // Pale Emerald
    onTertiaryContainer = Color(0xFF002114), // Deep Emerald
    error = Color(0xFFE11D48), // Rose
    errorContainer = Color(0xFFFFDDE3), // Pale Rose
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF4C0016), // Deep Rose
    background = Color(0xFFF5F6FF), // Mist
    onBackground = Color(0xFF14172B), // Ink
    surface = Color(0xFFF5F6FF), // Mist
    onSurface = Color(0xFF14172B), // Ink
    surfaceVariant = Color(0xFFE3E5F5), // Lavender Gray
    onSurfaceVariant = Color(0xFF454963), // Slate
    outline = Color(0xFF767A94), // Slate Gray
    outlineVariant = Color(0xFFC8CBE3), // Light Slate
    inverseSurface = Color(0xFF292C42), // Dark Slate
    inverseOnSurface = Color(0xFFEFF0FF), // Very Light Mist
    inversePrimary = Color(0xFFBBC2FF), // Light Indigo
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF4F5BD5), // Indigo
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFEFF0FD), // Mist Low
    surfaceContainer = Color(0xFFFFFFFF), // White cards
    surfaceContainerHigh = Color(0xFFE9EAF9), // Mist High
    surfaceContainerHighest = Color(0xFFE1E3F5), // Mist Highest
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF8E9BFF), // Periwinkle
    onPrimary = Color(0xFF0B1262), // Deep Indigo
    primaryContainer = Color(0xFF2E3A9E), // Indigo
    onPrimaryContainer = Color(0xFFE0E3FF), // Pale Indigo
    secondary = Color(0xFF22D3EE), // Cyan
    onSecondary = Color(0xFF00363F), // Deep Cyan
    secondaryContainer = Color(0xFF004E5C), // Teal
    onSecondaryContainer = Color(0xFFA6EEFF), // Pale Cyan
    tertiary = Color(0xFF34D399), // Emerald
    onTertiary = Color(0xFF00382A), // Deep Emerald
    tertiaryContainer = Color(0xFF005139), // Dark Emerald
    onTertiaryContainer = Color(0xFFB7F0D8), // Pale Emerald
    error = Color(0xFFFF8FA3), // Light Rose
    errorContainer = Color(0xFF93002D), // Dark Rose
    onError = Color(0xFF68001B), // Deep Rose
    onErrorContainer = Color(0xFFFFDDE3), // Pale Rose
    background = Color(0xFF0B0F1A), // Midnight
    onBackground = Color(0xFFE4E6F7), // Soft White
    surface = Color(0xFF0B0F1A), // Midnight
    onSurface = Color(0xFFE4E6F7), // Soft White
    surfaceVariant = Color(0xFF2A3047), // Navy Gray
    onSurfaceVariant = Color(0xFFB9BDD6), // Light Slate
    outline = Color(0xFF8387A0), // Slate
    outlineVariant = Color(0xFF2E3450), // Navy Border
    inverseSurface = Color(0xFFE4E6F7), // Soft White
    inverseOnSurface = Color(0xFF14172B), // Ink
    inversePrimary = Color(0xFF4F5BD5), // Indigo
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF8E9BFF), // Periwinkle
    surfaceContainerLowest = Color(0xFF070A12), // Near Black
    surfaceContainerLow = Color(0xFF0F1424), // Navy Low
    surfaceContainer = Color(0xFF141B2E), // Navy cards
    surfaceContainerHigh = Color(0xFF1B243A), // Navy High
    surfaceContainerHighest = Color(0xFF232E49), // Navy Highest
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

// Semantic Colors
val colorPing = Color(0xFF16A34A) // Green
val colorPingRed = Color(0xFFF43F5E) // Rose Red
val colorConfigType = Color(0xFF0891B2) // Cyan
val colorFabActive = Color(0xFF10B981) // Emerald
val colorFabInactiveLight = Color(0xFF9AA0BC) // Light Slate
val colorFabInactiveDark = Color(0xFF5B627F) // Dark Slate
val colorConnectGradientStart = Color(0xFF22D3EE) // Cyan
val colorConnectGradientEnd = Color(0xFF10B981) // Emerald
val dividerColorLight = Color(0xFFDCDFF1) // Light Slate
val dividerColorDark = Color(0xFF2A3047) // Navy Border

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

private fun ColorScheme.withAccent(accentArgb: Int?, dark: Boolean): ColorScheme {
    if (accentArgb == null) return this
    val p = AccentMath.primaryFor(accentArgb, dark)
    return copy(
        primary = Color(p.primary),
        onPrimary = Color(p.onPrimary),
        primaryContainer = Color(p.container),
        onPrimaryContainer = Color(p.onContainer),
        inversePrimary = Color(p.inverse),
        surfaceTint = Color(p.primary),
    )
}

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Off by default so the app's own palette is shown; users can still enable Material You in settings.
    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val remote by RemoteConfigManager.config.collectAsState()
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // The admin's accent applies only to the built-in palette; Material You (chosen by the user) wins.
    val accent = remote.theme.accentArgb.takeIf { !useDynamic }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }.withAccent(accent, darkTheme)
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
