package com.lostf1sh.pixelplayeross.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.core.view.WindowCompat
import com.lostf1sh.pixelplayeross.data.preferences.InterfaceAppearance
import com.lostf1sh.pixelplayeross.data.preferences.ThemePreset
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils

val LocalPixelPlayerDarkTheme = staticCompositionLocalOf { false }
val LocalPixelPlayerAmoledTheme = staticCompositionLocalOf { false }
val LocalShowScrollbar = staticCompositionLocalOf { true }
internal val LocalUseSystemFont = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun PixelPlayerStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

val DarkColorScheme = darkColorScheme(
    primary = PixelPlayerPurplePrimary,
    secondary = PixelPlayerPink,
    tertiary = PixelPlayerOrange,
    background = PixelPlayerPurpleDark,
    surface = PixelPlayerSurface,
    onPrimary = PixelPlayerWhite,
    onSecondary = PixelPlayerWhite,
    onTertiary = PixelPlayerWhite,
    onBackground = PixelPlayerWhite,
    onSurface = PixelPlayerLightPurple,
    error = Color(0xFFFF5252),
    onError = PixelPlayerWhite
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = PixelPlayerWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = PixelPlayerPink,
    onSecondary = PixelPlayerWhite,
    secondaryContainer = PixelPlayerPink.copy(alpha = 0.15f),
    onSecondaryContainer = PixelPlayerPink.copy(alpha = 0.85f),
    tertiary = PixelPlayerOrange,
    onTertiary = PixelPlayerBlack,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.6f),
    surfaceTint = LightPrimary,
    error = Color(0xFFD32F2F),
    onError = PixelPlayerWhite
)

fun isAmoledThemePreset(preset: String): Boolean = preset == ThemePreset.AMOLED

/** Keep the source palette's accents and content colors, replacing only its surface hierarchy. */
fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF242424),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF242424),
    surfaceVariant = Color(0xFF242424),
)

/**
 * AMOLED always uses a dark palette, even when the app is set to light mode. The accents come
 * from [colorSchemePairOverride] (the app-wide now-playing palette) when the user enabled it,
 * otherwise from the wallpaper/dynamic dark scheme.
 */
internal fun resolveAmoledColorScheme(
    colorSchemePairOverride: ColorSchemePair?,
    wallpaperDarkScheme: () -> ColorScheme,
): ColorScheme = (colorSchemePairOverride?.dark ?: wallpaperDarkScheme()).withAmoledSurfaces()

fun getAmoledColorScheme(
    context: Context,
    colorSchemePairOverride: ColorSchemePair? = null,
): ColorScheme = resolveAmoledColorScheme(colorSchemePairOverride) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            dynamicDarkColorScheme(context)
        } catch (e: Exception) {
            DarkColorScheme
        }
    } else {
        DarkColorScheme
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    themePreset: String = ThemePreset.DEFAULT,
    interfaceAppearance: InterfaceAppearance = InterfaceAppearance(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isAmoled = isAmoledThemePreset(themePreset)
    val finalColorScheme = when {
        isAmoled -> remember(context, colorSchemePairOverride) {
            getAmoledColorScheme(context, colorSchemePairOverride)
        }
        colorSchemePairOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (e: Exception) {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        colorSchemePairOverride != null -> {
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    PixelPlayerStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    val systemDensity = LocalDensity.current
    val scalePercent = interfaceAppearance.scalePercent
    val interfaceDensity = remember(systemDensity, scalePercent) {
        if (scalePercent == InterfaceAppearance.DEFAULT_SCALE) {
            systemDensity
        } else {
            object : Density {
                override val density = systemDensity.density * scalePercent / 100f
                override val fontScale = systemDensity.fontScale
                override fun Dp.toSp(): TextUnit = with(systemDensity) { this@toSp.toSp() }
                override fun TextUnit.toDp(): Dp = with(systemDensity) { this@toDp.toDp() }
            }
        }
    }

    CompositionLocalProvider(
        LocalDensity provides interfaceDensity,
        LocalUseSystemFont provides interfaceAppearance.useSystemFont,
        LocalPixelPlayerDarkTheme provides (darkTheme || isAmoled),
        LocalPixelPlayerAmoledTheme provides isAmoled
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = if (interfaceAppearance.useSystemFont) SystemTypography else Typography,
            shapes = Shapes,
            content = content
        )
    }
}
