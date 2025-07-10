package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary = Color(0xFF9AA2FF),
    onPrimary = Color(0xFF140048),
    primaryContainer = Color(0xFF140048),
    onPrimaryContainer = Color(0xFFD5D6FF),
    secondary = Color(0xFFC0C5FF),
    onSecondary = Color(0xFF140048),
    secondaryContainer = Color(0xFF2A1B78),
    onSecondaryContainer = Color(0xFFC0C5FF),
    tertiary = Color(0xFFF1FFA3),
    onTertiary = Color(0xFF444D12),
    tertiaryContainer = Color(0xFF5A6618),
    onTertiaryContainer = Color(0xFFF9FFD6),
    error = Color(0xFFFA7C75),
    onError = Color(0xFF591A16),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9AFA9),
    outline = Color(0xFF9E93AD),
    outlineVariant = Color(0xFF3B2D4F),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCCC1D6),
    surfaceContainerHighest = Color(0xFF232129),
    keyboardSurface = Color(0xFF000000),
    keyboardContainer = Color(0xFF2A1B78).copy(alpha = 0.6f),
    keyboardContainerVariant = Color(0xFF2A1B78).copy(alpha = 0.2f),
    onKeyboardContainer = Color(0xFFE6E1E5).copy(alpha = 0.8f),
    keyboardPress = Color(0xFF140048),
    keyboardFade0 = Color(0xFF000000),
    keyboardFade1 = Color(0xFF000000),
    primaryTransparent = Color(0xFF9AA2FF).copy(alpha = 0.3f),
    onSurfaceTransparent = Color(0xFFE6E1E5).copy(alpha = 0.1f),
)

val AMOLEDDarkIndigo = ThemeOption(
    dynamic = false,
    key = "AMOLEDDarkIndigo",
    name = R.string.theme_amoled_dark_indigo,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(AMOLEDDarkIndigo)
}

