package org.futo.inputmethod.latin.uix.theme.presets

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.CustomAccentColor
import org.futo.inputmethod.latin.uix.CustomBaseColor
import org.futo.inputmethod.latin.uix.CustomIconColor
import org.futo.inputmethod.latin.uix.CustomIconBgColor
import org.futo.inputmethod.latin.uix.CustomKeyBgColor
import org.futo.inputmethod.latin.uix.CustomModifierColor
import org.futo.inputmethod.latin.uix.CustomBorderColor
import org.futo.inputmethod.latin.uix.CustomBackgroundImage
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

private fun safeColor(code: String, fallback: String): Color {
    return try { Color(parseColor(code)) } catch (_: IllegalArgumentException) { Color(parseColor(fallback)) }
}

private fun lighten(color: Color, amount: Float): Color {
    return Color(
        red = color.red + (1f - color.red) * amount,
        green = color.green + (1f - color.green) * amount,
        blue = color.blue + (1f - color.blue) * amount,
        alpha = color.alpha
    )
}

private fun idealOnColor(color: Color): Color {
    return if (color.luminance() > 0.5f) Color.Black else Color.White
}

private fun colorsFrom(
    accent: Color,
    base: Color,
    icon: Color,
    iconBg: Color,
    keyBg: Color,
    modifierBg: Color,
    border: Color,
    backgroundImage: String,
) = extendedDarkColorScheme(
    primary = accent,
    onPrimary = idealOnColor(accent),
    primaryContainer = accent,
    onPrimaryContainer = idealOnColor(accent),
    secondary = lighten(accent, 0.2f),
    onSecondary = idealOnColor(lighten(accent, 0.2f)),
    secondaryContainer = lighten(accent, 0.2f),
    onSecondaryContainer = idealOnColor(lighten(accent, 0.2f)),
    tertiary = lighten(accent, 0.4f),
    onTertiary = idealOnColor(lighten(accent, 0.4f)),
    tertiaryContainer = lighten(accent, 0.4f),
    onTertiaryContainer = idealOnColor(lighten(accent, 0.4f)),
    error = Color(0xFFFA6060),
    onError = Color.Black,
    errorContainer = Color(0xFF730000),
    onErrorContainer = Color.White,
    outline = border,
    outlineVariant = border.copy(alpha = 0.4f),
    surface = base,
    onSurface = Color.White,
    onSurfaceVariant = Color.White,
    surfaceContainerHighest = base,
    keyboardSurface = base,
    keyboardContainer = keyBg,
    keyboardContainerVariant = modifierBg,
    onKeyboardContainer = idealOnColor(keyBg),
    keyboardPress = accent.copy(alpha = 0.7f),
    keyboardFade0 = base,
    keyboardFade1 = base,
    keyboardBackgroundShader = if (backgroundImage.isNotEmpty()) backgroundImage else null,
    primaryTransparent = accent.copy(alpha = 0.3f),
    onSurfaceTransparent = Color.White.copy(alpha = 0.1f),
    settingsIconColor = icon,
    keyboardSurfaceDim = base,
)

val CustomTheme = ThemeOption(
    dynamic = false,
    key = "CustomTheme",
    name = R.string.theme_custom,
    available = { true },
    obtainColors = {
        val icon = safeColor(it.getSetting(CustomIconColor), CustomIconColor.default)
        val iconBg = safeColor(it.getSetting(CustomIconBgColor), CustomIconBgColor.default)
        val keyBg = safeColor(it.getSetting(CustomKeyBgColor), CustomKeyBgColor.default)
        val modBg = safeColor(it.getSetting(CustomModifierColor), CustomModifierColor.default)
        val border = safeColor(it.getSetting(CustomBorderColor), CustomBorderColor.default)
        val bgImage = it.getSetting(CustomBackgroundImage)

        extendedDarkColorScheme(
            primary = safeColor(it.getSetting(CustomPrimaryColor), CustomPrimaryColor.default),
            onPrimary = safeColor(it.getSetting(CustomOnPrimaryColor), CustomOnPrimaryColor.default),
            primaryContainer = safeColor(it.getSetting(CustomPrimaryContainerColor), CustomPrimaryContainerColor.default),
            onPrimaryContainer = safeColor(it.getSetting(CustomOnPrimaryContainerColor), CustomOnPrimaryContainerColor.default),
            secondary = safeColor(it.getSetting(CustomSecondaryColor), CustomSecondaryColor.default),
            onSecondary = safeColor(it.getSetting(CustomOnSecondaryColor), CustomOnSecondaryColor.default),
            secondaryContainer = safeColor(it.getSetting(CustomSecondaryContainerColor), CustomSecondaryContainerColor.default),
            onSecondaryContainer = safeColor(it.getSetting(CustomOnSecondaryContainerColor), CustomOnSecondaryContainerColor.default),
            tertiary = safeColor(it.getSetting(CustomTertiaryColor), CustomTertiaryColor.default),
            onTertiary = safeColor(it.getSetting(CustomOnTertiaryColor), CustomOnTertiaryColor.default),
            tertiaryContainer = safeColor(it.getSetting(CustomTertiaryContainerColor), CustomTertiaryContainerColor.default),
            onTertiaryContainer = safeColor(it.getSetting(CustomOnTertiaryContainerColor), CustomOnTertiaryContainerColor.default),
            error = safeColor(it.getSetting(CustomErrorColor), CustomErrorColor.default),
            onError = safeColor(it.getSetting(CustomOnErrorColor), CustomOnErrorColor.default),
            errorContainer = safeColor(it.getSetting(CustomErrorContainerColor), CustomErrorContainerColor.default),
            onErrorContainer = safeColor(it.getSetting(CustomOnErrorContainerColor), CustomOnErrorContainerColor.default),
            outline = safeColor(it.getSetting(CustomOutlineColor), CustomOutlineColor.default),
            outlineVariant = safeColor(it.getSetting(CustomOutlineVariantColor), CustomOutlineVariantColor.default),
            surface = safeColor(it.getSetting(CustomSurfaceColor), CustomSurfaceColor.default),
            onSurface = safeColor(it.getSetting(CustomOnSurfaceColor), CustomOnSurfaceColor.default),
            onSurfaceVariant = safeColor(it.getSetting(CustomOnSurfaceVariantColor), CustomOnSurfaceVariantColor.default),
            surfaceContainerHighest = safeColor(it.getSetting(CustomSurfaceContainerHighestColor), CustomSurfaceContainerHighestColor.default),
            shadow = safeColor(it.getSetting(CustomShadowColor), CustomShadowColor.default),
            keyboardSurface = safeColor(it.getSetting(CustomKeyboardSurfaceColor), CustomKeyboardSurfaceColor.default),
            keyboardContainer = safeColor(it.getSetting(CustomKeyboardContainerColor), CustomKeyboardContainerColor.default),
            keyboardContainerVariant = safeColor(it.getSetting(CustomKeyboardContainerVariantColor), CustomKeyboardContainerVariantColor.default),
            onKeyboardContainer = safeColor(it.getSetting(CustomOnKeyboardContainerColor), CustomOnKeyboardContainerColor.default),
            keyboardPress = safeColor(it.getSetting(CustomKeyboardPressColor), CustomKeyboardPressColor.default),
            keyboardFade0 = safeColor(it.getSetting(CustomKeyboardFade0Color), CustomKeyboardFade0Color.default),
            keyboardFade1 = safeColor(it.getSetting(CustomKeyboardFade1Color), CustomKeyboardFade1Color.default),
            keyboardBackgroundGradient = null,
            primaryTransparent = safeColor(it.getSetting(CustomPrimaryTransparentColor), CustomPrimaryTransparentColor.default),
            onSurfaceTransparent = safeColor(it.getSetting(CustomOnSurfaceTransparentColor), CustomOnSurfaceTransparentColor.default),
            settingsIconColor = icon,
            settingsIconBackground = iconBg,
            keyboardSurfaceDim = safeColor(it.getSetting(CustomSurfaceColor), CustomSurfaceColor.default),
            keyboardContainerPressed = border.copy(alpha = 0.33f),
            onKeyboardContainerPressed = Color.Transparent,
            keyboardBackgroundShader = if (bgImage.isNotEmpty()) bgImage else null,
        )
    }
)
