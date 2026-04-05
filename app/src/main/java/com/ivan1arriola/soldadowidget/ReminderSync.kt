package com.ivan1arriola.soldadowidget

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ReminderSync {

    data class ReminderSnapshot(
        val pendingCount: Int,
        val urgentCount: Int,
        val topTitle: String?
    )

    data class ExtensionConfig(
        val baseUrl: String,
        val usuarioId: String,
        val token: String
    ) {
        val isConfigured: Boolean
            get() = baseUrl.isNotBlank() && usuarioId.isNotBlank() && token.isNotBlank()
    }

    fun readConfig(context: Context): ExtensionConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ExtensionConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, "")?.trim().orEmpty(),
            usuarioId = prefs.getString(KEY_USUARIO_ID, "")?.trim().orEmpty(),
            token = prefs.getString(KEY_TOKEN, "")?.trim().orEmpty()
        )
    }

    fun saveConfig(context: Context, config: ExtensionConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_BASE_URL, config.baseUrl.trim())
            putString(KEY_USUARIO_ID, config.usuarioId.trim())
            putString(KEY_TOKEN, config.token.trim())
        }
    }

    fun isConfigured(context: Context): Boolean = readConfig(context).isConfigured

    fun fetchSnapshot(context: Context): ReminderSnapshot? {
        val config = readConfig(context)
        if (!config.isConfigured) return null

        val endpoint =
            config.baseUrl.trimEnd('/') +
                "/api/extensions/soldado-widget/recordatorios?usuarioId=" +
                urlEncode(config.usuarioId) +
                "&limit=3"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.token}")
        }

        return try {
            val status = connection.responseCode
            if (status !in 200..299) return null

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            val summary = json.optJSONObject("summary") ?: return null
            val tasks = json.optJSONArray("tasks")

            val topTitle = if (tasks != null && tasks.length() > 0) {
                tasks.optJSONObject(0)?.optString("titulo")?.trim().orEmpty().ifEmpty { null }
            } else {
                null
            }

            ReminderSnapshot(
                pendingCount = summary.optInt("pendingCount", 0),
                urgentCount = summary.optInt("urgentCount", 0),
                topTitle = topTitle
            )
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun saveSnapshotForWidget(context: Context, appWidgetId: Int, snapshot: ReminderSnapshot?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            if (snapshot == null) {
                remove("$KEY_PENDING_PREFIX$appWidgetId")
                remove("$KEY_URGENT_PREFIX$appWidgetId")
                remove("$KEY_TOP_TITLE_PREFIX$appWidgetId")
                remove("$KEY_LAST_SYNC_PREFIX$appWidgetId")
                return@edit
            }

            putInt("$KEY_PENDING_PREFIX$appWidgetId", snapshot.pendingCount)
            putInt("$KEY_URGENT_PREFIX$appWidgetId", snapshot.urgentCount)
            putString("$KEY_TOP_TITLE_PREFIX$appWidgetId", snapshot.topTitle)
            putLong("$KEY_LAST_SYNC_PREFIX$appWidgetId", System.currentTimeMillis())
        }
    }

    fun readLastSyncTime(context: Context, appWidgetId: Int): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_LAST_SYNC_PREFIX$appWidgetId", 0L)
    }

    fun reminderLine(context: Context, appWidgetId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getInt("$KEY_PENDING_PREFIX$appWidgetId", 0)
        val urgent = prefs.getInt("$KEY_URGENT_PREFIX$appWidgetId", 0)

        return when {
            pending <= 0 -> context.getString(R.string.reminder_line_clear)
            urgent > 0 -> context.getString(R.string.reminder_line_urgent, pending, urgent)
            else -> context.getString(R.string.reminder_line_pending, pending)
        }
    }

    fun phrase(context: Context, appWidgetId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getInt("$KEY_PENDING_PREFIX$appWidgetId", 0)
        val urgent = prefs.getInt("$KEY_URGENT_PREFIX$appWidgetId", 0)
        val topTitle = prefs.getString("$KEY_TOP_TITLE_PREFIX$appWidgetId", "")?.trim().orEmpty()

        return when {
            pending <= 0 -> context.getString(R.string.reminder_phrase_clear)
            urgent > 0 -> context.getString(R.string.reminder_phrase_urgent, urgent)
            topTitle.isNotEmpty() -> context.getString(R.string.reminder_phrase_next, truncate(topTitle, 34))
            else -> context.getString(R.string.reminder_phrase_pending, pending)
        }
    }

    private fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        return value.take(maxLength - 1) + "…"
    }

    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private const val PREFS_NAME = "soldado_widget_prefs"
    private const val KEY_BASE_URL = "extension_base_url"
    private const val KEY_USUARIO_ID = "extension_usuario_id"
    private const val KEY_TOKEN = "extension_token"

    private const val KEY_PENDING_PREFIX = "reminder_pending_"
    private const val KEY_URGENT_PREFIX = "reminder_urgent_"
    private const val KEY_TOP_TITLE_PREFIX = "reminder_top_title_"
    private const val KEY_LAST_SYNC_PREFIX = "reminder_last_sync_"
}
