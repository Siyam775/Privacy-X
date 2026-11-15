package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.AppearanceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.privacyXDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "privacyx_prefs"
)

data class LocalThemePreferences(
    val themeId: AppThemeId = AppThemeId.MATERIAL_LIQUID_GLASS,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val accentColorHex: String = "",
    val reducedMotion: Boolean = false
)

data class ChatAppearancePreferences(
    val colorPresetId: String = "default",
    val wallpaperPresetId: String = "default",
    val customWallpaperUri: String = "",
    val wallpaperDimAlpha: Float = 0.15f
)

class ThemePreferencesStore(private val context: Context) {
    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val ACCENT_COLOR_HEX = stringPreferencesKey("accent_color_hex")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        fun draftKey(conversationId: String) = stringPreferencesKey("draft_$conversationId")
        fun chatColorKey(conversationId: String) = stringPreferencesKey("chat_color_$conversationId")
        fun chatWallpaperKey(conversationId: String) = stringPreferencesKey("chat_wall_$conversationId")
        fun chatCustomWallpaperKey(conversationId: String) = stringPreferencesKey("chat_custom_wall_$conversationId")
        fun chatWallpaperDimKey(conversationId: String) = floatPreferencesKey("chat_wall_dim_$conversationId")
    }

    val preferencesFlow: Flow<LocalThemePreferences> = context.privacyXDataStore.data.map { prefs ->
        LocalThemePreferences(
            themeId = AppThemeId.fromId(prefs[Keys.THEME_ID]),
            appearanceMode = AppearanceMode.fromId(prefs[Keys.APPEARANCE_MODE]),
            accentColorHex = prefs[Keys.ACCENT_COLOR_HEX] ?: "",
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false
        )
    }

    suspend fun saveTheme(
        themeId: AppThemeId,
        appearanceMode: AppearanceMode,
        accentColorHex: String,
        reducedMotion: Boolean
    ) {
        context.privacyXDataStore.edit { prefs ->
            prefs[Keys.THEME_ID] = themeId.id
            prefs[Keys.APPEARANCE_MODE] = appearanceMode.id
            prefs[Keys.ACCENT_COLOR_HEX] = accentColorHex
            prefs[Keys.REDUCED_MOTION] = reducedMotion
        }
    }

    fun observeChatAppearance(conversationId: String): Flow<ChatAppearancePreferences> =
        context.privacyXDataStore.data.map { prefs ->
            val storedCustom = prefs[Keys.chatCustomWallpaperKey(conversationId)] ?: ""
            val validCustom = if (storedCustom.startsWith("file://")) {
                val path = storedCustom.removePrefix("file://")
                if (File(path).exists()) storedCustom else ""
            } else storedCustom
            ChatAppearancePreferences(
                colorPresetId = prefs[Keys.chatColorKey(conversationId)] ?: "default",
                wallpaperPresetId = prefs[Keys.chatWallpaperKey(conversationId)] ?: "default",
                customWallpaperUri = validCustom,
                wallpaperDimAlpha = prefs[Keys.chatWallpaperDimKey(conversationId)] ?: 0.15f
            )
        }

    suspend fun saveChatAppearance(
        conversationId: String,
        colorPresetId: String? = null,
        wallpaperPresetId: String? = null,
        customWallpaperUri: String? = null,
        wallpaperDimAlpha: Float? = null
    ) {
        context.privacyXDataStore.edit { prefs ->
            colorPresetId?.let { prefs[Keys.chatColorKey(conversationId)] = it }
            wallpaperPresetId?.let { prefs[Keys.chatWallpaperKey(conversationId)] = it }
            customWallpaperUri?.let {
                val key = Keys.chatCustomWallpaperKey(conversationId)
                if (it.isBlank()) prefs.remove(key) else prefs[key] = it
            }
            wallpaperDimAlpha?.let {
                prefs[Keys.chatWallpaperDimKey(conversationId)] = it.coerceIn(0f, 0.78f)
            }
        }
    }

    fun observeDraft(conversationId: String): Flow<String> =
        context.privacyXDataStore.data.map { prefs -> prefs[Keys.draftKey(conversationId)] ?: "" }

    suspend fun saveDraft(conversationId: String, text: String) {
        context.privacyXDataStore.edit { prefs ->
            val key = Keys.draftKey(conversationId)
            if (text.isEmpty()) prefs.remove(key) else prefs[key] = text
        }
    }

    suspend fun clearLocalCache(): Long {
        val cacheDir = context.cacheDir
        val bytesFreed = cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        return bytesFreed
    }
}
