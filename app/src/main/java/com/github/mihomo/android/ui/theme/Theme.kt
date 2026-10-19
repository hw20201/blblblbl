package com.github.mihomo.android.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme as NativeMiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles


data class LoonThemeColors(
    val bg: Color,
    val card: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val bottomBarBg: Color,
    val bottomBarBorder: Color,
    val isDark: Boolean,
    val separator: Color = if (isDark) Color(0x38545458) else Color(0x263C3C43),
    val secondaryBg: Color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
    val tertiaryBg: Color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
    val chevronTint: Color = if (isDark) Color(0xFF636366) else Color(0xFFC7C7CC),
    val switchUncheckedTrack: Color = if (isDark) Color(0xFF39393D) else Color(0xFFE9E9EA)
)

val LightLoonThemeColors = LoonThemeColors(
    bg = Color(0xFFF2F2F7), // Apple System Grouped Background
    card = Color(0xFFFFFFFF), // Apple Secondary System Grouped Background
    cardBorder = Color(0x1A000000), // Clean iOS hairline border
    textPrimary = Color(0xFF000000), // iOS Primary Text
    textSecondary = Color(0xFF646871), // Accessible secondary label
    textMuted = Color(0xFFC7C7CC), // iOS Tertiary Label
    bottomBarBg = Color.White.copy(alpha = 0.88f),
    bottomBarBorder = Color(0x1F000000),
    isDark = false,
    separator = Color(0x263C3C43),
    secondaryBg = Color(0xFFFFFFFF),
    tertiaryBg = Color(0xFFE5E5EA),
    chevronTint = Color(0xFFC7C7CC),
    switchUncheckedTrack = Color(0xFFE9E9EA)
)

val DarkLoonThemeColors = LoonThemeColors(
    bg = Color(0xFF000000), // True OLED Pure Black
    card = Color(0xFF1C1C1E), // Apple Secondary System Grouped Background
    cardBorder = Color(0x2EFFFFFF), // Clean iOS dark hairline border
    textPrimary = Color(0xFFFFFFFF), // iOS White Text
    textSecondary = Color(0xFFA5A7AE), // Accessible secondary label
    textMuted = Color(0xFF636366), // iOS Tertiary Label
    bottomBarBg = Color(0xFF1C1C1E).copy(alpha = 0.90f),
    bottomBarBorder = Color(0x26FFFFFF),
    isDark = true,
    separator = Color(0x38545458),
    secondaryBg = Color(0xFF1C1C1E),
    tertiaryBg = Color(0xFF2C2C2E),
    chevronTint = Color(0xFF636366),
    switchUncheckedTrack = Color(0xFF39393D)
)

val LocalLoonColors = staticCompositionLocalOf { LightLoonThemeColors }
val LocalUiStyle = staticCompositionLocalOf { UiStyle.CUPERTINO }
val LocalGlassIntensity = staticCompositionLocalOf { 0.5f }

@Composable
fun MihomoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    uiStyle: UiStyle = UiStyle.CUPERTINO,
    glassIntensity: Float = 0.5f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val loonColors = if (isDark) DarkLoonThemeColors else LightLoonThemeColors
    val uiColors = if (uiStyle == UiStyle.MIUIX) {
        if (isDark) loonColors.copy(
            bg = Color(0xFF101114), card = Color(0xFF202226), cardBorder = Color(0xFF30343A),
            bottomBarBg = Color(0xFF1B1D21), bottomBarBorder = Color(0xFF30343A)
        ) else loonColors.copy(
            bg = Color(0xFFF1F2F5), card = Color(0xFFFFFFFF), cardBorder = Color(0xFFE5E7EB),
            bottomBarBg = Color(0xFFF9FAFC), bottomBarBorder = Color(0xFFE1E4E9)
        )
    } else loonColors

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = LoonOrange,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF3B2414),
            onPrimaryContainer = LoonOrangeLight,
            background = uiColors.bg,
            surface = uiColors.card,
            surfaceVariant = uiColors.bg,
            onBackground = uiColors.textPrimary,
            onSurface = uiColors.textPrimary,
            onSurfaceVariant = uiColors.textSecondary
        )
    } else {
        lightColorScheme(
            primary = LoonOrange,
            onPrimary = Color.White,
            primaryContainer = LoonOrangeBg,
            onPrimaryContainer = LoonOrange,
            background = uiColors.bg,
            surface = uiColors.card,
            surfaceVariant = uiColors.bg,
            onBackground = uiColors.textPrimary,
            onSurface = uiColors.textPrimary,
            onSurfaceVariant = uiColors.textSecondary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val statusBarColor = uiColors.bg.toArgb()
        val navBarColor = uiColors.bg.toArgb()
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = statusBarColor
            window.navigationBarColor = navBarColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    CompositionLocalProvider(
        LocalLoonColors provides uiColors,
        LocalUiStyle provides uiStyle,
        LocalGlassIntensity provides com.github.mihomo.android.data.GlassEffect.normalize(glassIntensity),
        androidx.compose.foundation.LocalOverscrollConfiguration provides null
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
        ) {
            // Keep one stable composition path: changing the style must not dispose MainAppScreen.
            val miuixColors = if (isDark) miuixDarkColorScheme() else miuixLightColorScheme()
            val legacyIndication = androidx.compose.foundation.LocalIndication.current
            NativeMiuixTheme(
                colors = miuixColors,
                textStyles = defaultTextStyles(
                    headline1 = TextStyle(fontSize = 14.sp),
                    body2 = TextStyle(fontSize = 12.sp)
                )
            ) {
                val indication = if (uiStyle == UiStyle.MIUIX) {
                    androidx.compose.foundation.LocalIndication.current
                } else legacyIndication
                CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides indication) {
                    content()
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
