package com.ivan1arriola.soldadowidget

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

object ReminderSync {

    data class ReminderTask(
        val tareaId: String,
        val titulo: String,
        val nota: String,
        val fechaLimite: String?,
        val prioridad: String,
        val completada: Boolean,
        val updatedAt: String
    )

    data class ReminderSnapshot(
        val pendingCount: Int,
        val urgentCount: Int,
        val topTitle: String?
    )

    data class TaskListResponse(
        val usuarioId: String,
        val username: String,
        val generatedAt: String,
        val total: Int,
        val tareas: List<ReminderTask>
    )

    data class ExtensionConfig(
        val baseUrl: String,
        val username: String,
        val usuarioId: String,
        val token: String,
        val tokenExpiresAtMs: Long
    ) {
        val isConfigured: Boolean
            get() = baseUrl.isNotBlank() && usuarioId.isNotBlank() && token.isNotBlank() && !isTokenExpired(this)
    }

    data class LoginResult(
        val success: Boolean,
        val message: String,
        val config: ExtensionConfig? = null
    )

    fun readConfig(context: Context): ExtensionConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ExtensionConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, "https://deposito-apt1.vercel.app/")?.trim().orEmpty(),
            username = prefs.getString(KEY_USERNAME, "")?.trim().orEmpty(),
            usuarioId = prefs.getString(KEY_USUARIO_ID, "")?.trim().orEmpty(),
            token = prefs.getString(KEY_TOKEN, "")?.trim().orEmpty(),
            tokenExpiresAtMs = prefs.getLong(KEY_TOKEN_EXPIRES_AT_MS, 0L)
        )
    }

    fun saveConfig(context: Context, config: ExtensionConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_BASE_URL, config.baseUrl.trim())
            putString(KEY_USERNAME, config.username.trim())
            putString(KEY_USUARIO_ID, config.usuarioId.trim())
            putString(KEY_TOKEN, config.token.trim())
            putLong(KEY_TOKEN_EXPIRES_AT_MS, config.tokenExpiresAtMs)
        }
    }

    fun isConfigured(context: Context): Boolean = readConfig(context).isConfigured

    fun isTokenExpired(config: ExtensionConfig): Boolean {
        val expiresAt = config.tokenExpiresAtMs
        if (expiresAt <= 0L) return true
        return System.currentTimeMillis() >= expiresAt
    }

    fun login(context: Context, baseUrlRaw: String, usernameRaw: String, passwordRaw: String): LoginResult {
        val baseUrl = baseUrlRaw.trim()
        val username = usernameRaw.trim()
        val password = passwordRaw.trim()

        if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
            return LoginResult(success = false, message = "Completa URL, usuario y password.")
        }

        val endpoint = baseUrl.trimEnd('/') + "/api/extensions/soldado-widget/login"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7000
            readTimeout = 7000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }

        return try {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(body.toString())
            }

            val status = connection.responseCode
            val responseText = readResponseBody(connection, status)
            val json = parseJsonSafe(responseText)

            if (status !in 200..299) {
                val message = json?.optString("error")?.trim().orEmpty()
                val displayMessage = when {
                    message.isNotEmpty() -> message
                    responseText.contains("<!DOCTYPE") || responseText.contains("<html") -> "Error del servidor (HTTP $status, posible redireccion/HTML). Verifica URL."
                    status == 307 -> "Redireccion del servidor (HTTP 307). Verifica URL de Deposito_ART1."
                    status == 401 || status == 403 -> "Credenciales invalidas o no autorizadas (HTTP $status)."
                    else -> "Error HTTP $status: ${responseText.take(100)}"
                }
                return LoginResult(
                    success = false,
                    message = displayMessage
                )
            }

            val accessToken = json?.optString("accessToken")?.trim().orEmpty()
            val expiresAtRaw = json?.optString("expiresAt")?.trim().orEmpty()
            val usuario = json?.optJSONObject("usuario")
            val usuarioId = usuario?.optString("usuarioId")?.trim().orEmpty()

            if (accessToken.isBlank() || usuarioId.isBlank() || expiresAtRaw.isBlank()) {
                return LoginResult(
                    success = false,
                    message = "Respuesta invalida del servidor. JSON incompleto: ${responseText.take(200)}"
                )
            }

            val expiresAtMs = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    java.time.Instant.parse(expiresAtRaw).toEpochMilli()
                } else {
                    // Fallback for older APIs if needed, or use a library like ThreeTenABP
                    0L 
                }
            } catch (_: Throwable) {
                0L
            }

            if (expiresAtMs <= 0L && expiresAtRaw.isNotEmpty()) {
                 // Simple attempt to parse if Instant fails or API < 26
                 // For now, let's assume API 26+ or provide a basic alternative
            }

            val config = ExtensionConfig(
                baseUrl = baseUrl,
                username = username,
                usuarioId = usuarioId,
                token = accessToken,
                tokenExpiresAtMs = expiresAtMs
            )

            saveConfig(context, config)

            LoginResult(
                success = true,
                message = "Sesion iniciada para $username.",
                config = config
            )
        } catch (_: Throwable) {
            LoginResult(success = false, message = "No se pudo conectar con Deposito_ART1.")
        } finally {
            connection.disconnect()
        }
    }

    fun fetchSnapshot(context: Context): ReminderSnapshot? {
        val config = readConfig(context)
        if (!config.isConfigured) return null

        val endpoint =
            config.baseUrl.trimEnd('/') +
                "/api/extensions/soldado-widget/recordatorios?limit=3"

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

    fun fetchTasks(context: Context): TaskListResponse? {
        val config = readConfig(context)
        if (!config.isConfigured) return null

        val endpoint =
            config.baseUrl.trimEnd('/') +
                "/api/extensions/soldado-widget/tareas?includeCompleted=true&limit=100"

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

            val usuarioId = json.optString("usuarioId", "")
            val username = json.optString("username", "")
            val generatedAt = json.optString("generatedAt", "")
            val total = json.optInt("total", 0)

            val tareasArray = json.optJSONArray("tareas") ?: return null
            val tareas = mutableListOf<ReminderTask>()

            for (i in 0 until tareasArray.length()) {
                val tareaJson = tareasArray.optJSONObject(i) ?: continue
                tareas.add(
                    ReminderTask(
                        tareaId = tareaJson.optString("tareaId", ""),
                        titulo = tareaJson.optString("titulo", ""),
                        nota = tareaJson.optString("nota", ""),
                        fechaLimite = tareaJson.optString("fechaLimite", null),
                        prioridad = tareaJson.optString("prioridad", "NORMAL"),
                        completada = tareaJson.optBoolean("completada", false),
                        updatedAt = tareaJson.optString("updatedAt", "")
                    )
                )
            }

            TaskListResponse(
                usuarioId = usuarioId,
                username = username,
                generatedAt = generatedAt,
                total = total,
                tareas = tareas
            )
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun completeTask(context: Context, tareaId: String): Boolean {
        val config = readConfig(context)
        if (!config.isConfigured) return false

        val endpoint =
            config.baseUrl.trimEnd('/') +
                "/api/extensions/soldado-widget/tareas/$tareaId/complete"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.token}")
        }

        return try {
            val status = connection.responseCode
            status in 200..299
        } catch (_: Throwable) {
            false
        } finally {
            connection.disconnect()
        }
    }

    fun createTask(
        context: Context,
        titulo: String,
        nota: String = "",
        fechaLimite: String? = null,
        prioridad: String = "NORMAL"
    ): ReminderTask? {
        val config = readConfig(context)
        if (!config.isConfigured) return null

        val endpoint = config.baseUrl.trimEnd('/') + "/api/extensions/soldado-widget/tareas/create"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7000
            readTimeout = 7000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.token}")
        }

        return try {
            val body = JSONObject().apply {
                put("titulo", titulo)
                put("nota", nota.ifEmpty { JSONObject.NULL })
                if (fechaLimite != null) put("fechaLimite", fechaLimite)
                put("prioridad", prioridad)
            }

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(body.toString())
            }

            val status = connection.responseCode
            if (status !in 200..299) return null

            val responseText = readResponseBody(connection, status)
            val json = JSONObject(responseText)

            ReminderTask(
                tareaId = json.optString("tareaId", ""),
                titulo = json.optString("titulo", ""),
                nota = json.optString("nota", ""),
                fechaLimite = json.optString("fechaLimite", null),
                prioridad = json.optString("prioridad", "NORMAL"),
                completada = json.optBoolean("completada", false),
                updatedAt = json.optString("updatedAt", "")
            )
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun updateTask(
        context: Context,
        tareaId: String,
        titulo: String? = null,
        nota: String? = null,
        fechaLimite: String? = null,
        prioridad: String? = null,
        completada: Boolean? = null
    ): ReminderTask? {
        val config = readConfig(context)
        if (!config.isConfigured) return null

        val endpoint = config.baseUrl.trimEnd('/') + "/api/extensions/soldado-widget/tareas/$tareaId"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 7000
            readTimeout = 7000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.token}")
        }

        return try {
            val body = JSONObject()
            if (titulo != null) body.put("titulo", titulo)
            if (nota != null) body.put("nota", nota.ifEmpty { JSONObject.NULL })
            if (fechaLimite != null) body.put("fechaLimite", fechaLimite)
            if (prioridad != null) body.put("prioridad", prioridad)
            if (completada != null) body.put("completada", completada)

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(body.toString())
            }

            val status = connection.responseCode
            if (status !in 200..299) return null

            val responseText = readResponseBody(connection, status)
            val json = JSONObject(responseText)

            ReminderTask(
                tareaId = json.optString("tareaId", ""),
                titulo = json.optString("titulo", ""),
                nota = json.optString("nota", ""),
                fechaLimite = json.optString("fechaLimite", null),
                prioridad = json.optString("prioridad", "NORMAL"),
                completada = json.optBoolean("completada", false),
                updatedAt = json.optString("updatedAt", "")
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
            putLong("$KEY_LAST_SYNC_PREFIX$appWidgetId", System.currentTimeMillis())

            if (snapshot == null) {
                remove("$KEY_PENDING_PREFIX$appWidgetId")
                remove("$KEY_URGENT_PREFIX$appWidgetId")
                remove("$KEY_TOP_TITLE_PREFIX$appWidgetId")
                return@edit
            }

            putInt("$KEY_PENDING_PREFIX$appWidgetId", snapshot.pendingCount)
            putInt("$KEY_URGENT_PREFIX$appWidgetId", snapshot.urgentCount)
            putString("$KEY_TOP_TITLE_PREFIX$appWidgetId", snapshot.topTitle)
        }
    }

    fun clearSnapshotForWidget(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove("$KEY_PENDING_PREFIX$appWidgetId")
            remove("$KEY_URGENT_PREFIX$appWidgetId")
            remove("$KEY_TOP_TITLE_PREFIX$appWidgetId")
            remove("$KEY_LAST_SYNC_PREFIX$appWidgetId")
        }
    }

    fun readLastSyncTime(context: Context, appWidgetId: Int): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_LAST_SYNC_PREFIX$appWidgetId", 0L)
    }

    fun reminderLine(context: Context, appWidgetId: Int): String {
        if (!isConfigured(context)) return context.getString(R.string.reminder_line_unconfigured)

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
        if (!isConfigured(context)) return context.getString(R.string.soldado_phrase_idle)

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

    fun phraseWithTasks(context: Context): String {
        if (!isConfigured(context)) return context.getString(R.string.soldado_phrase_idle)

        val response = fetchTasks(context) ?: return phrase(context, 0)
        
        val pendingTasks = response.tareas.filter { !it.completada }
        if (pendingTasks.isEmpty()) {
            return listOf(
                R.string.task_phrase_done_0,
                R.string.reminder_phrase_clear
            ).random { Random }.let { context.getString(it) }
        }

        // Seleccionar por prioridad y cantidad
        val firstTask = pendingTasks.first()
        val urgentTasks = pendingTasks.filter { it.prioridad == "URGENTE" }
        val totalPending = pendingTasks.size

        return when {
            // Tarea urgente única
            urgentTasks.size == 1 && totalPending == 1 -> {
                listOf(
                    R.string.task_phrase_urgent_0,
                    R.string.task_phrase_urgent_1,
                    R.string.task_phrase_urgent_2
                ).random { Random }.let { 
                    context.getString(it, truncate(firstTask.titulo, 30))
                }
            }
            // Múltiples tareas urgentes
            urgentTasks.isNotEmpty() -> {
                context.getString(
                    R.string.task_phrase_urgent_3,
                    truncate(firstTask.titulo, 30)
                )
            }
            // Múltiples tareas normales
            totalPending > 1 -> {
                listOf(
                    R.string.task_phrase_multiple_0,
                    R.string.task_phrase_multiple_1,
                    R.string.task_phrase_multiple_2
                ).random { Random }.let {
                    context.getString(it, totalPending, truncate(firstTask.titulo, 25))
                }
            }
            // Una tarea normal
            else -> {
                listOf(
                    R.string.task_phrase_directive_0,
                    R.string.task_phrase_directive_1,
                    R.string.task_phrase_reminder_0,
                    R.string.task_phrase_reminder_1
                ).random { Random }.let {
                    context.getString(it, truncate(firstTask.titulo, 35))
                }
            }
        }
    }

    private fun <T> List<T>.random(getRandom: () -> Random): T {
        return this[getRandom().nextInt(size)]
    }

    private fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        return value.take(maxLength - 3) + "..."
    }

    private fun parseJsonSafe(value: String): JSONObject? {
        return try {
            JSONObject(value)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readResponseBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return stream.bufferedReader().use { it.readText() }
    }

    private const val PREFS_NAME = "soldado_widget_prefs"
    private const val KEY_BASE_URL = "extension_base_url"
    private const val KEY_USERNAME = "extension_username"
    private const val KEY_USUARIO_ID = "extension_usuario_id"
    private const val KEY_TOKEN = "extension_token"
    private const val KEY_TOKEN_EXPIRES_AT_MS = "extension_token_expires_at_ms"

    private const val KEY_PENDING_PREFIX = "reminder_pending_"
    private const val KEY_URGENT_PREFIX = "reminder_urgent_"
    private const val KEY_TOP_TITLE_PREFIX = "reminder_top_title_"
    private const val KEY_LAST_SYNC_PREFIX = "reminder_last_sync_"
}
