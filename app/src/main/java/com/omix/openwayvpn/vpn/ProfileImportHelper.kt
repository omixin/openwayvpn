package com.omix.openwayvpn.vpn

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.omix.openwayvpn.vpn.VlessProfileStore

/**
 * Helper для импорта VLESS профилей из различных источников (intent, clipboard).
 * Вынесен из MainActivity для уменьшения связанности.
 */
object ProfileImportHelper {
    private const val DEBUG_TAG = "openwayDEBUG"

    /**
     * Импортирует VLESS URI из intent (deep link или share intent)
     */
    fun importFromIntent(context: Context, intent: Intent?): Boolean {
        return try {
            val uri = extractVlessUri(intent) ?: return false
            importProfile(context, uri, "intent/share")
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error importing from intent: ${e.message}", e)
            false
        }
    }

    /**
     * Импортирует VLESS URI из буфера обмена
     */
    fun importFromClipboard(context: Context): Boolean {
        return try {
            val uri = readVlessFromClipboard(context) ?: return false
            importProfile(context, uri, "clipboard")
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error importing from clipboard: ${e.message}", e)
            false
        }
    }

    /**
     * Извлекает VLESS URI из intent
     */
    private fun extractVlessUri(intent: Intent?): String? {
        return try {
            if (intent == null) return null
            
            // Проверяем data uri
            val dataString = intent.dataString?.trim()
            if (!dataString.isNullOrBlank() && dataString.startsWith("vless://", ignoreCase = true)) {
                return dataString
            }
            
            // Проверяем shared text
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrBlank() && sharedText.startsWith("vless://", ignoreCase = true)) {
                return sharedText
            }
            
            null
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error extracting VLESS URI from intent: ${e.message}")
            null
        }
    }

    /**
     * Читает VLESS URI из буфера обмена
     */
    private fun readVlessFromClipboard(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return null
            
            val clip: ClipData = clipboard.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            
            val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
            
            if (text.startsWith("vless://", ignoreCase = true)) text else null
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error reading from clipboard: ${e.message}")
            null
        }
    }

    /**
     * Импортирует профиль и делает его активным
     */
    private fun importProfile(context: Context, uri: String, source: String): Boolean {
        return try {
            // Валидируем URI перед добавлением
            val parseResult = VlessUriParser.parse(uri)
            if (parseResult.isFailure) {
                Log.w(DEBUG_TAG, "Invalid VLESS URI from $source: ${parseResult.exceptionOrNull()?.message}")
                return false
            }

            VlessProfileStore.addPreset(context, uri)
            VlessProfileStore.setActiveUri(context, uri)
            Log.d(DEBUG_TAG, "Imported vless URI from $source")
            true
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error importing profile from $source: ${e.message}", e)
            false
        }
    }
}
