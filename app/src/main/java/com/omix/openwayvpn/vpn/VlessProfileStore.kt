package com.omix.openwayvpn.vpn

import android.content.Context
import org.json.JSONArray

object VlessProfileStore {
    private const val PREFS = "openway_vpn_profiles"
    private const val KEY_PRESET_URIS_JSON = "preset_uris_json"
    private const val KEY_ACTIVE_URI = "active_uri"

    fun addPreset(context: Context, uri: String): Boolean {
        val normalized = uri.trim()
        if (!normalized.startsWith("vless://", ignoreCase = true)) return false

        val existing = getAllUris(context).toMutableList()
        if (existing.any { it.equals(normalized, ignoreCase = true) }) {
            setActiveUri(context, normalized)
            return false
        }

        existing.add(0, normalized)
        saveAllUris(context, existing)
        setActiveUri(context, normalized)
        return true
    }

    fun setActiveUri(context: Context, uri: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_URI, uri.trim())
            .apply()
    }

    fun getActiveUri(context: Context): String? {
        val active = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_URI, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (active != null) return active
        return getAllUris(context).firstOrNull()
    }

    fun getAllUris(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PRESET_URIS_JSON, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveAllUris(context: Context, uris: List<String>) {
        val arr = JSONArray()
        uris.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PRESET_URIS_JSON, arr.toString())
            .apply()
    }

    fun removePreset(context: Context, uri: String): Boolean {
        val normalized = uri.trim()
        val existing = getAllUris(context).toMutableList()
        val removed = existing.removeIf { it.equals(normalized, ignoreCase = true) }
        if (removed) {
            saveAllUris(context, existing)
            // Если удалили активный профиль, выберем первый доступный
            val activeUri = getActiveUri(context)
            if (activeUri == null || activeUri.equals(normalized, ignoreCase = true)) {
                setActiveUri(context, existing.firstOrNull().orEmpty())
            }
        }
        return removed
    }
}
