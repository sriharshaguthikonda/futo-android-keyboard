package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.CustomIconColor
import org.futo.inputmethod.latin.uix.CustomIconBgColor
import org.futo.inputmethod.latin.uix.CustomKeyBgColor
import org.futo.inputmethod.latin.uix.CustomModifierColor
import org.futo.inputmethod.latin.uix.CustomBorderColor
import org.futo.inputmethod.latin.uix.CustomBackgroundImage
import org.futo.inputmethod.latin.uix.CustomPrimaryColor
import org.futo.inputmethod.latin.uix.CustomOnPrimaryColor
import org.futo.inputmethod.latin.uix.CustomPrimaryContainerColor
import org.futo.inputmethod.latin.uix.CustomOnPrimaryContainerColor
import org.futo.inputmethod.latin.uix.CustomSecondaryColor
import org.futo.inputmethod.latin.uix.CustomOnSecondaryColor
import org.futo.inputmethod.latin.uix.CustomSecondaryContainerColor
import org.futo.inputmethod.latin.uix.CustomOnSecondaryContainerColor
import org.futo.inputmethod.latin.uix.CustomTertiaryColor
import org.futo.inputmethod.latin.uix.CustomOnTertiaryColor
import org.futo.inputmethod.latin.uix.CustomTertiaryContainerColor
import org.futo.inputmethod.latin.uix.CustomOnTertiaryContainerColor
import org.futo.inputmethod.latin.uix.CustomErrorColor
import org.futo.inputmethod.latin.uix.CustomOnErrorColor
import org.futo.inputmethod.latin.uix.CustomErrorContainerColor
import org.futo.inputmethod.latin.uix.CustomOnErrorContainerColor
import org.futo.inputmethod.latin.uix.CustomOutlineColor
import org.futo.inputmethod.latin.uix.CustomOutlineVariantColor
import org.futo.inputmethod.latin.uix.CustomSurfaceColor
import org.futo.inputmethod.latin.uix.CustomOnSurfaceColor
import org.futo.inputmethod.latin.uix.CustomOnSurfaceVariantColor
import org.futo.inputmethod.latin.uix.CustomSurfaceContainerHighestColor
import org.futo.inputmethod.latin.uix.CustomShadowColor
import org.futo.inputmethod.latin.uix.CustomKeyboardSurfaceColor
import org.futo.inputmethod.latin.uix.CustomKeyboardContainerColor
import org.futo.inputmethod.latin.uix.CustomKeyboardContainerVariantColor
import org.futo.inputmethod.latin.uix.CustomOnKeyboardContainerColor
import org.futo.inputmethod.latin.uix.CustomKeyboardPressColor
import org.futo.inputmethod.latin.uix.CustomKeyboardFade0Color
import org.futo.inputmethod.latin.uix.CustomKeyboardFade1Color
import org.futo.inputmethod.latin.uix.CustomPrimaryTransparentColor
import org.futo.inputmethod.latin.uix.CustomOnSurfaceTransparentColor

import org.futo.inputmethod.latin.uix.CustomHomePrimaryBgColor
import org.futo.inputmethod.latin.uix.CustomHomeSecondaryBgColor
import org.futo.inputmethod.latin.uix.CustomHomeTertiaryBgColor
import org.futo.inputmethod.latin.uix.CustomMiscBgColor
import org.futo.inputmethod.latin.uix.CustomMiscNoArrowBgColor

import org.futo.inputmethod.latin.uix.CustomHomePrimaryBgImage
import org.futo.inputmethod.latin.uix.CustomHomeSecondaryBgImage
import org.futo.inputmethod.latin.uix.CustomHomeTertiaryBgImage
import org.futo.inputmethod.latin.uix.CustomMiscBgImage
import org.futo.inputmethod.latin.uix.CustomMiscNoArrowBgImage
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview
import org.futo.inputmethod.latin.uix.theme.presets.CustomTheme

@Composable
fun ThemeGeneratorScreen(navController: NavHostController) {
    val colorSettings = listOf(
        CustomPrimaryColor to "Primary",
        CustomOnPrimaryColor to "On Primary",
        CustomPrimaryContainerColor to "Primary Container",
        CustomOnPrimaryContainerColor to "On Primary Container",
        CustomSecondaryColor to "Secondary",
        CustomOnSecondaryColor to "On Secondary",
        CustomSecondaryContainerColor to "Secondary Container",
        CustomOnSecondaryContainerColor to "On Secondary Container",
        CustomTertiaryColor to "Tertiary",
        CustomOnTertiaryColor to "On Tertiary",
        CustomTertiaryContainerColor to "Tertiary Container",
        CustomOnTertiaryContainerColor to "On Tertiary Container",
        CustomErrorColor to "Error",
        CustomOnErrorColor to "On Error",
        CustomErrorContainerColor to "Error Container",
        CustomOnErrorContainerColor to "On Error Container",
        CustomOutlineColor to "Outline",
        CustomOutlineVariantColor to "Outline Variant",
        CustomSurfaceColor to "Surface",
        CustomOnSurfaceColor to "On Surface",
        CustomOnSurfaceVariantColor to "On Surface Variant",
        CustomSurfaceContainerHighestColor to "Surface Container Highest",
        CustomShadowColor to "Shadow",
        CustomKeyboardSurfaceColor to "Keyboard Surface",
        CustomKeyboardContainerColor to "Keyboard Container",
        CustomKeyboardContainerVariantColor to "Keyboard Container Variant",
        CustomOnKeyboardContainerColor to "On Keyboard Container",
        CustomKeyboardPressColor to "Keyboard Press",
        CustomKeyboardFade0Color to "Keyboard Fade0",
        CustomKeyboardFade1Color to "Keyboard Fade1",
        CustomPrimaryTransparentColor to "Primary Transparent",
        CustomOnSurfaceTransparentColor to "On Surface Transparent"
    )
    val (icon, setIcon) = useDataStore(CustomIconColor)
    val (iconBg, setIconBg) = useDataStore(CustomIconBgColor)
    val (keyBg, setKeyBg) = useDataStore(CustomKeyBgColor)
    val (modBg, setModBg) = useDataStore(CustomModifierColor)
    val (border, setBorder) = useDataStore(CustomBorderColor)
    val (bgImage, setBgImage) = useDataStore(CustomBackgroundImage)
    val (homePrimaryColor, setHomePrimaryColor) = useDataStore(CustomHomePrimaryBgColor)
    val (homeSecondaryColor, setHomeSecondaryColor) = useDataStore(CustomHomeSecondaryBgColor)
    val (homeTertiaryColor, setHomeTertiaryColor) = useDataStore(CustomHomeTertiaryBgColor)
    val (miscColor, setMiscColor) = useDataStore(CustomMiscBgColor)
    val (miscNoArrowColor, setMiscNoArrowColor) = useDataStore(CustomMiscNoArrowBgColor)

    val (homePrimaryBg, setHomePrimaryBg) = useDataStore(CustomHomePrimaryBgImage)
    val (homeSecondaryBg, setHomeSecondaryBg) = useDataStore(CustomHomeSecondaryBgImage)
    val (homeTertiaryBg, setHomeTertiaryBg) = useDataStore(CustomHomeTertiaryBgImage)
    val (miscBg, setMiscBg) = useDataStore(CustomMiscBgImage)
    val (miscNoArrowBg, setMiscNoArrowBg) = useDataStore(CustomMiscNoArrowBgImage)
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        ScreenTitle(stringResource(R.string.theme_generator_title), showBack = true, navController)
        colorSettings.forEach { (key, label) ->
            val (value, setter) = useDataStore(key)
            ColorPicker(label, value, setter)
        }
        ColorPicker(stringResource(R.string.theme_generator_icon), icon, setIcon)
        ColorPicker("Icon Background", iconBg, setIconBg)
        ColorPicker("Key Background", keyBg, setKeyBg)
        ColorPicker("Modifier Key", modBg, setModBg)
        ColorPicker("Key Border", border, setBorder)
        ColorPicker("Home Primary Icon Background", homePrimaryColor, setHomePrimaryColor)
        ColorPicker("Home Secondary Icon Background", homeSecondaryColor, setHomeSecondaryColor)
        ColorPicker("Home Tertiary Icon Background", homeTertiaryColor, setHomeTertiaryColor)
        ColorPicker("Misc Icon Background", miscColor, setMiscColor)
        ColorPicker("Misc No Arrow Icon Background", miscNoArrowColor, setMiscNoArrowColor)

        TextFieldWithLabel("Home Primary Item Image", homePrimaryBg, setHomePrimaryBg)
        TextFieldWithLabel("Home Secondary Item Image", homeSecondaryBg, setHomeSecondaryBg)
        TextFieldWithLabel("Home Tertiary Item Image", homeTertiaryBg, setHomeTertiaryBg)
        TextFieldWithLabel("Misc Item Image", miscBg, setMiscBg)
        TextFieldWithLabel("Misc No Arrow Item Image", miscNoArrowBg, setMiscNoArrowBg)

        ImagePicker("Background Image", bgImage, setBgImage)

        Button(onClick = { navController.navigateUp() }, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.theme_generator_save))
        }
        ThemePreview(CustomTheme, modifier = Modifier.padding(16.dp)) {}
    }
}

@Composable
private fun ColorPicker(label: String, colorStr: String, setColor: (String) -> Unit) {
    fun toHex(c: Color): String = String.format("#%06X", 0xFFFFFF and c.toArgb())
    var color by remember(colorStr) { mutableStateOf(runCatching { Color(android.graphics.Color.parseColor(colorStr)) }.getOrDefault(Color.White)) }
    val update = { setColor(toHex(color)) }
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp, 20.dp).background(color))
        }
        Slider(value = color.red, onValueChange = { color = color.copy(red = it); update() }, colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red))
        Slider(value = color.green, onValueChange = { color = color.copy(green = it); update() }, colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green))
        Slider(value = color.blue, onValueChange = { color = color.copy(blue = it); update() }, colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue))
    }
}

@Composable
private fun TextFieldWithLabel(label: String, value: String, setValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Text(label)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; setValue(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ImagePicker(label: String, value: String, setValue: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { setValue(it.toString()) }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { launcher.launch(arrayOf("image/*")) }) {
                Icon(Icons.Filled.Folder, contentDescription = null)
            }
            if (value.isNotBlank()) Text(value)
        }
    }
}
