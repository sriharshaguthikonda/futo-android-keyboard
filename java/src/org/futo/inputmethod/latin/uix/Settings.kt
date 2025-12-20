package org.futo.inputmethod.latin.uix

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.preference.PreferenceManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import org.futo.inputmethod.latin.ActiveSubtype
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.uix.theme.defaultThemeOption
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.io.File

// Used before first unlock (direct boot)
private object DefaultDataStore : DataStore<Preferences> {
    private var activePreferences = preferencesOf(
        ActiveSubtype.key to "en_US:",
        SubtypesSetting.key to setOf("en_US:"),
        THEME_KEY.key to ClassicMaterialDark.key,
        KeyHintsSetting.key to true
    )

    var subtypesInitialized = false

    suspend fun updateSubtypes(subtypes: Set<String>) {
        val newPreferences = activePreferences.toMutablePreferences()
        newPreferences[SubtypesSetting.key] = subtypes

        activePreferences = newPreferences
        sharedData.emit(activePreferences)
    }

    val sharedData = MutableSharedFlow<Preferences>(1)

    override val data: Flow<Preferences>
        get() {
            return unlockedDataStore?.data ?: sharedData
        }

    init {
        sharedData.tryEmit(activePreferences)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        return unlockedDataStore?.updateData(transform) ?: run {
            val newActiveSubtype = transform(activePreferences)[ActiveSubtype.key]
            if(newActiveSubtype != null && newActiveSubtype != activePreferences[ActiveSubtype.key]) {
                val newPreferences = activePreferences.toMutablePreferences()
                newPreferences[ActiveSubtype.key] = newActiveSubtype
                activePreferences = newPreferences
                sharedData.emit(newPreferences)
            }

            return activePreferences
        }
    }
}

// Set and used after first unlock (direct boot)
private var unlockedDataStore: DataStore<Preferences>? = null

// To prevent two threads trying to create a datastore at once
private val dataStoreCreationMutex = Mutex()

fun Context.getPreferencesDataStoreFile(): File =
    applicationContext.preferencesDataStoreFile("settings")

fun Context.getBackupPreferencesDataStoreFile(): File =
    applicationContext.preferencesDataStoreFile("settings_backup")

fun Context.getBackupPreferencesDataStoreFileSwap(): File =
    applicationContext.preferencesDataStoreFile("settings_backup_swap")

fun writeDatastoreBackup(context: Context, unlockedStore: DataStore<Preferences>) {
    val outFile = context.getBackupPreferencesDataStoreFileSwap()

    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch {
        val prefs = unlockedStore.data.take(1).first()
        outFile.parentFile?.mkdirs()
        outFile.sink().buffer().use { out ->
            PreferencesSerializer.writeTo(prefs, out)
        }

        val result = try {
            retrieveDatastoreBackup(context, outFile)
        } catch(_: Exception) {
            null
        }

        if(result == null || result.asMap().keys.size != prefs.asMap().keys.size) {
            Log.e("SettingsBackup", "Could not back up settings!")
            return@launch
        }

        val primaryFile = context.getBackupPreferencesDataStoreFile()
        if(primaryFile.exists()) primaryFile.delete()
        outFile.renameTo(primaryFile)
    }
}

suspend fun retrieveDatastoreBackup(context: Context, file: File = context.getBackupPreferencesDataStoreFile()): Preferences? {
    if(!file.exists()) return null

    val prefs = file.source().buffer().use { f ->
        PreferencesSerializer.readFrom(f)
    }

    if(!file.name.contains("_swap")) Log.e("SettingsBackup", "Preferences restored: ${prefs.asMap().keys.size} items")

    return prefs
}

private fun<T> Mutex.withTryLock(block: () -> T): T? {
    return if (tryLock()) {
        try {
            block()
        } finally {
            unlock()
        }
    } else {
        null
    }
}

fun forceUnlockDatastore(context: Context): DataStore<Preferences>? {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    if(!userManager.isUserUnlocked) return null // still in direct boot

    return unlockedDataStore ?: dataStoreCreationMutex.withTryLock {
        unlockedDataStore ?: run {
            val newDataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler {
                    Log.e(
                        "SettingsBackup",
                        "The settings file is corrupted! Attempting to restore..."
                    )
                    runBlocking {
                        retrieveDatastoreBackup(context)
                    } ?: run {
                        Log.e(
                            "SettingsBackup",
                            "File is corrupted, and could not restore backup. Resetting to default"
                        )
                        preferencesOf()
                    }
                },
                migrations = listOf(),
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            ) {
                context.getPreferencesDataStoreFile()
            }

            writeDatastoreBackup(context, newDataStore)
            unlockedDataStore = newDataStore

            // Send new values to the DefaultDataStore for any listeners
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                newDataStore.data.collect { value ->
                    DefaultDataStore.sharedData.emit(value)
                }
            }

            newDataStore
        }
    }
}

private fun lockedDatastoreWithSubtypes(context: Context): DataStore<Preferences> {
    if (!DefaultDataStore.subtypesInitialized) {
        DefaultDataStore.subtypesInitialized = true

        LayoutManager.init(context)

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            DefaultDataStore.updateSubtypes(Subtypes.getDirectBootInitialLayouts(context))
        }
    }

    return DefaultDataStore
}

// Initializes unlockedDataStore, or uses DefaultDataStore if device is still locked (direct boot)
val Context.dataStore: DataStore<Preferences>
    get() {
        return unlockedDataStore ?: forceUnlockDatastore(this) ?: lockedDatastoreWithSubtypes(this)
    }


object PreferenceUtils {
    fun getDefaultSharedPreferences(context: Context): SharedPreferences {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        return if (userManager.isUserUnlocked) {
            PreferenceManager.getDefaultSharedPreferences(context)
        } else {
            PreferenceManager.getDefaultSharedPreferences(context.createDeviceProtectedStorageContext())
        }
    }
}

val Context.isDirectBootUnlocked: Boolean
    get() {
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        return userManager.isUserUnlocked
    }

class DataStoreHelper {
    @OptIn(DelicateCoroutinesApi::class)
    companion object {
        private var initialized: Boolean = false
        private var currentPreferences: Preferences = preferencesOf()

        @JvmStatic
        fun init(context: Context) {
            if(initialized) return
            initialized = true

            runBlocking {
                context.dataStore.data.first().let {
                    currentPreferences = it
                }
            }

            GlobalScope.launch {
                context.dataStore.data.collect {
                    currentPreferences = it
                }
            }
        }

        @JvmStatic
        fun<T> getSettingOrNull(key: Preferences.Key<T>): T? = currentPreferences[key]

        @JvmStatic
        fun<T> getSetting(key: Preferences.Key<T>, default: T): T = getSettingOrNull(key) ?: default

        @JvmStatic
        fun<T> getSettingOrNull(setting: SettingsKey<T>): T? = getSettingOrNull(setting.key)

        @JvmStatic
        fun<T> getSetting(setting: SettingsKey<T>): T = getSettingOrNull(setting.key) ?: setting.default
    }
}


fun <T> Context.getSetting(key: Preferences.Key<T>, default: T): T {
    /*val valueFlow: Flow<T> =
        this.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

    return valueFlow.first()*/

    return DataStoreHelper.getSetting(key, default)
}

fun <T> Context.getSettingFlow(key: Preferences.Key<T>, default: T): Flow<T> {
    return dataStore.data.map { preferences -> preferences[key] ?: default }.distinctUntilChanged()
}

suspend fun <T> Context.setSetting(key: Preferences.Key<T>, value: T) {
    this.dataStore.edit { preferences ->
        preferences[key] = value
    }
}


fun <T> Context.getSettingBlocking(key: Preferences.Key<T>, default: T): T {
    /*
    val context = this

    return runBlocking {
        context.getSetting(key, default)
    }*/

    return DataStoreHelper.getSetting(key, default)
}

fun <T> Context.getSettingBlocking(key: SettingsKey<T>): T {
    return getSettingBlocking(key.key, key.default)
}

fun <T> Context.setSettingBlocking(key: Preferences.Key<T>, value: T) {
    val context = this
    runBlocking {
        context.setSetting(key, value)
    }
}

suspend fun <T> Context.getUnlockedSetting(key: SettingsKey<T>): T? {
    return unlockedDataStore?.let {
        val valueFlow: Flow<T> =
            it.data.map { preferences -> preferences[key.key] ?: key.default }.take(1)


        valueFlow.first()
    }
}

suspend fun Context.getUnlockedPreferences(): Preferences? {
    return unlockedDataStore?.data?.take(1)?.first()
}

fun <T> LifecycleOwner.deferSetSetting(context: Context, key: Preferences.Key<T>, value: T): Job {
    return lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            context.setSetting(key, value)
        }
    }
}

data class SettingsKey<T>(
    val key: Preferences.Key<T>,
    val default: T
)

fun <T> Context.getSetting(key: SettingsKey<T>): T {
    return getSetting(key.key, key.default)
}

fun <T> Context.getSettingFlow(key: SettingsKey<T>): Flow<T> {
    return getSettingFlow(key.key, key.default)
}

suspend fun <T> Context.setSetting(key: SettingsKey<T>, value: T) {
    return setSetting(key.key, value)
}

fun <T> LifecycleOwner.deferSetSetting(context: Context, key: SettingsKey<T>, value: T): Job {
    return deferSetSetting(context, key.key, value)
}


val THEME_KEY = SettingsKey(
    key = stringPreferencesKey("activeThemeOption"),
    default = ""
)

val CustomAccentColor = SettingsKey(
    key = stringPreferencesKey("custom_accent_color"),
    default = "#B2C8FF"
)

val CustomBaseColor = SettingsKey(
    key = stringPreferencesKey("custom_base_color"),
    default = "#121316"
)

val CustomIconColor = SettingsKey(
    key = stringPreferencesKey("custom_icon_color"),
    default = "#B2C8FF"
)

val CustomIconBgColor = SettingsKey(
    key = stringPreferencesKey("custom_icon_bg_color"),
    default = "#121316"
)

val CustomKeyBgColor = SettingsKey(
    key = stringPreferencesKey("custom_key_bg_color"),
    default = "#121316"
)

val CustomModifierColor = SettingsKey(
    key = stringPreferencesKey("custom_modifier_color"),
    default = "#1E1F21"
)

val CustomBorderColor = SettingsKey(
    key = stringPreferencesKey("custom_border_color"),
    default = "#444444"
)

val CustomHomePrimaryBgColor = SettingsKey(
    key = stringPreferencesKey("custom_home_primary_bg_color"),
    default = CustomIconBgColor.default
)

val CustomHomePrimaryBgImage = SettingsKey(
    key = stringPreferencesKey("custom_home_primary_bg_image"),
    default = ""
)

val CustomHomeSecondaryBgColor = SettingsKey(
    key = stringPreferencesKey("custom_home_secondary_bg_color"),
    default = CustomIconBgColor.default
)

val CustomHomeSecondaryBgImage = SettingsKey(
    key = stringPreferencesKey("custom_home_secondary_bg_image"),
    default = ""
)

val CustomHomeTertiaryBgColor = SettingsKey(
    key = stringPreferencesKey("custom_home_tertiary_bg_color"),
    default = CustomIconBgColor.default
)

val CustomHomeTertiaryBgImage = SettingsKey(
    key = stringPreferencesKey("custom_home_tertiary_bg_image"),
    default = ""
)

val CustomMiscBgColor = SettingsKey(
    key = stringPreferencesKey("custom_misc_bg_color"),
    default = CustomIconBgColor.default
)

val CustomMiscBgImage = SettingsKey(
    key = stringPreferencesKey("custom_misc_bg_image"),
    default = ""
)

val CustomMiscNoArrowBgColor = SettingsKey(
    key = stringPreferencesKey("custom_misc_noarrow_bg_color"),
    default = CustomIconBgColor.default
)

val CustomMiscNoArrowBgImage = SettingsKey(
    key = stringPreferencesKey("custom_misc_noarrow_bg_image"),
    default = ""
)

val CustomBackgroundImage = SettingsKey(
    key = stringPreferencesKey("custom_background_image"),
    default = ""
)

val CustomPrimaryColor = SettingsKey(
    key = stringPreferencesKey("custom_primary_color"),
    default = "#D0BCFF"
)
val CustomOnPrimaryColor = SettingsKey(
    key = stringPreferencesKey("custom_on_primary_color"),
    default = "#381E72"
)
val CustomPrimaryContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_primary_container_color"),
    default = "#3A2966"
)
val CustomOnPrimaryContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_on_primary_container_color"),
    default = "#E4D4FF"
)
val CustomSecondaryColor = SettingsKey(
    key = stringPreferencesKey("custom_secondary_color"),
    default = "#CFBAFF"
)
val CustomOnSecondaryColor = SettingsKey(
    key = stringPreferencesKey("custom_on_secondary_color"),
    default = "#3E2663"
)
val CustomSecondaryContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_secondary_container_color"),
    default = "#1E192B"
)
val CustomOnSecondaryContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_on_secondary_container_color"),
    default = "#AC9DC4"
)
val CustomTertiaryColor = SettingsKey(
    key = stringPreferencesKey("custom_tertiary_color"),
    default = "#F1FFA3"
)
val CustomOnTertiaryColor = SettingsKey(
    key = stringPreferencesKey("custom_on_tertiary_color"),
    default = "#444D12"
)
val CustomTertiaryContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_tertiary_container_color"),
    default = "#5A6618"
)
val CustomOnTertiaryContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_on_tertiary_container_color"),
    default = "#F9FFD6"
)
val CustomErrorColor = SettingsKey(
    key = stringPreferencesKey("custom_error_color"),
    default = "#FA7C75"
)
val CustomOnErrorColor = SettingsKey(
    key = stringPreferencesKey("custom_on_error_color"),
    default = "#591A16"
)
val CustomErrorContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_error_container_color"),
    default = "#8C1D18"
)
val CustomOnErrorContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_on_error_container_color"),
    default = "#F9AFA9"
)
val CustomOutlineColor = SettingsKey(
    key = stringPreferencesKey("custom_outline_color"),
    default = "#9E93AD"
)
val CustomOutlineVariantColor = SettingsKey(
    key = stringPreferencesKey("custom_outline_variant_color"),
    default = "#3B2D4F"
)
val CustomSurfaceColor = SettingsKey(
    key = stringPreferencesKey("custom_surface_color"),
    default = "#000000"
)
val CustomOnSurfaceColor = SettingsKey(
    key = stringPreferencesKey("custom_on_surface_color"),
    default = "#E6E1E5"
)
val CustomOnSurfaceVariantColor = SettingsKey(
    key = stringPreferencesKey("custom_on_surface_variant_color"),
    default = "#CCC1D6"
)
val CustomSurfaceContainerHighestColor = SettingsKey(
    key = stringPreferencesKey("custom_surface_container_highest_color"),
    default = "#232129"
)
val CustomShadowColor = SettingsKey(
    key = stringPreferencesKey("custom_shadow_color"),
    default = "#000000"
)
val CustomKeyboardSurfaceColor = SettingsKey(
    key = stringPreferencesKey("custom_keyboard_surface_color"),
    default = "#000000"
)
val CustomKeyboardContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_keyboard_container_color"),
    default = "#1E192B"
)
val CustomKeyboardContainerVariantColor = SettingsKey(
    key = stringPreferencesKey("custom_keyboard_container_variant_color"),
    default = "#181324"
)
val CustomOnKeyboardContainerColor = SettingsKey(
    key = stringPreferencesKey("custom_on_keyboard_container_color"),
    default = "#E6E1E5"
)
val CustomKeyboardPressColor = SettingsKey(
    key = stringPreferencesKey("custom_keyboard_press_color"),
    default = "#31264F"
)
val CustomKeyboardFade0Color = SettingsKey(
    key = stringPreferencesKey("custom_keyboard_fade0_color"),
    default = "#000000"
)
val CustomKeyboardFade1Color = SettingsKey(
    key = stringPreferencesKey("custom_keyboard_fade1_color"),
    default = "#000000"
)
val CustomPrimaryTransparentColor = SettingsKey(
    key = stringPreferencesKey("custom_primary_transparent_color"),
    default = "#D0BCFF"
)
val CustomOnSurfaceTransparentColor = SettingsKey(
    key = stringPreferencesKey("custom_on_surface_transparent_color"),
    default = "#E6E1E5"
)

suspend fun Context.resetThemeToDefault() {
    withContext(Dispatchers.Default) {
        listOf(
            CustomAccentColor,
            CustomBaseColor,
            CustomIconColor,
            CustomIconBgColor,
            CustomKeyBgColor,
            CustomModifierColor,
            CustomBorderColor,
            CustomBackgroundImage,
            CustomPrimaryColor,
            CustomOnPrimaryColor,
            CustomPrimaryContainerColor,
            CustomOnPrimaryContainerColor,
            CustomSecondaryColor,
            CustomOnSecondaryColor,
            CustomSecondaryContainerColor,
            CustomOnSecondaryContainerColor,
            CustomTertiaryColor,
            CustomOnTertiaryColor,
            CustomTertiaryContainerColor,
            CustomOnTertiaryContainerColor,
            CustomErrorColor,
            CustomOnErrorColor,
            CustomErrorContainerColor,
            CustomOnErrorContainerColor,
            CustomOutlineColor,
            CustomOutlineVariantColor,
            CustomSurfaceColor,
            CustomOnSurfaceColor,
            CustomOnSurfaceVariantColor,
            CustomSurfaceContainerHighestColor,
            CustomShadowColor,
            CustomKeyboardSurfaceColor,
            CustomKeyboardContainerColor,
            CustomKeyboardContainerVariantColor,
            CustomOnKeyboardContainerColor,
            CustomKeyboardPressColor,
            CustomKeyboardFade0Color,
            CustomKeyboardFade1Color,
            CustomPrimaryTransparentColor,
            CustomOnSurfaceTransparentColor,
            CustomHomePrimaryBgColor,
            CustomHomeSecondaryBgColor,
            CustomHomeTertiaryBgColor,
            CustomMiscBgColor,
            CustomMiscNoArrowBgColor,
            CustomHomePrimaryBgImage,
            CustomHomeSecondaryBgImage,
            CustomHomeTertiaryBgImage,
            CustomMiscBgImage,
            CustomMiscNoArrowBgImage,
        ).forEach { setSetting(it, it.default) }

        setSetting(THEME_KEY, defaultThemeOption(this@resetThemeToDefault).key)
    }
}

val USE_SYSTEM_VOICE_INPUT = SettingsKey(
    key = booleanPreferencesKey("useSystemVoiceInput"),
    default = false
)

val USE_TRANSFORMER_FINETUNING = SettingsKey(
    key = booleanPreferencesKey("useTransformerFinetuning2"),
    default = false
)

val SUGGESTION_BLACKLIST = SettingsKey(
    key = stringSetPreferencesKey("suggestionBlacklist"),
    default = setOf()
)

val SHOW_EMOJI_SUGGESTIONS = SettingsKey(
    key = booleanPreferencesKey("suggestEmojis"),
    default = true)
