package com.ivan1arriola.soldadowidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.util.UUID

class ConfigActivity : AppCompatActivity() {

    private lateinit var inputApiBaseUrl: EditText
    private lateinit var inputUsername: EditText
    private lateinit var inputPassword: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val root = findViewById<View>(R.id.configRoot)
        applySystemInsets(root)

        inputApiBaseUrl = findViewById(R.id.inputApiBaseUrl)
        inputUsername = findViewById(R.id.inputUsername)
        inputPassword = findViewById(R.id.inputPassword)
        val btnSave = findViewById<Button>(R.id.btnSaveExtension)
        val btnLogin = findViewById<Button>(R.id.btnLoginExtension)
        val btnSync = findViewById<Button>(R.id.btnSyncExtension)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnViewTasks = findViewById<Button>(R.id.btnViewTasks)
        statusText = findViewById(R.id.extensionStatusText)

        // Cargar config actual
        val config = ReminderSync.readConfig(this)
        val normalizedConfig = config.copy(baseUrl = FIXED_BASE_URL)
        if (normalizedConfig.baseUrl != config.baseUrl) {
            ReminderSync.saveConfig(this, normalizedConfig)
        }

        inputApiBaseUrl.setText(FIXED_BASE_URL)
        inputApiBaseUrl.isEnabled = false
        inputApiBaseUrl.isFocusable = false
        inputApiBaseUrl.isFocusableInTouchMode = false
        inputApiBaseUrl.isClickable = false
        inputUsername.setText(config.username)

        btnSave.setOnClickListener {
            val currentConfig = ReminderSync.readConfig(this)
            val newConfig = ReminderSync.ExtensionConfig(
                baseUrl = FIXED_BASE_URL,
                username = inputUsername.text.toString(),
                usuarioId = currentConfig.usuarioId,
                token = currentConfig.token,
                tokenExpiresAtMs = currentConfig.tokenExpiresAtMs
            )
            ReminderSync.saveConfig(this, newConfig)
            statusText.text = getString(R.string.extension_status_ready_to_login)
            triggerWidgetRefresh()
        }

        btnLogin.setOnClickListener {
            startWebLogin()
        }

        btnSync.setOnClickListener {
            syncNow(statusText)
        }

        btnViewTasks.setOnClickListener {
            val intent = Intent(this, RemindersListActivity::class.java)
            startActivity(intent)
        }

        btnBack.setOnClickListener {
            finish()
        }

        handleWebLoginCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWebLoginCallback(intent)
    }

    private fun syncNow(statusView: TextView) {
        val config = ReminderSync.readConfig(this)
        if (config.username.isBlank()) {
            statusView.text = getString(R.string.extension_status_unconfigured)
            return
        }

        if (ReminderSync.isTokenExpired(config)) {
            statusView.text = getString(R.string.extension_status_session_expired)
            return
        }

        statusView.text = getString(R.string.extension_status_syncing)
        Thread {
            val snapshot = ReminderSync.fetchSnapshot(this)
            runOnUiThread {
                if (snapshot == null) {
                    statusView.text = getString(R.string.extension_status_sync_fail)
                } else {
                    statusView.text = getString(
                        R.string.extension_status_sync_ok,
                        snapshot.pendingCount,
                        snapshot.urgentCount
                    )
                    triggerWidgetRefresh()
                }
            }
        }.start()
    }

    private fun startWebLogin() {
        val state = UUID.randomUUID().toString()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit { putString(KEY_WIDGET_WEB_AUTH_STATE, state) }

        val endpoint =
            FIXED_BASE_URL.trimEnd('/') +
                "/api/extensions/soldado-widget/oauth/authorize?redirect_uri=" +
                Uri.encode(WIDGET_WEB_AUTH_REDIRECT_URI) +
                "&state=" +
                Uri.encode(state)

        statusText.text = getString(R.string.extension_status_opening_web_auth)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(endpoint)))
    }

    private fun handleWebLoginCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != WIDGET_WEB_AUTH_SCHEME || uri.host != WIDGET_WEB_AUTH_HOST) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val expectedState = prefs.getString(KEY_WIDGET_WEB_AUTH_STATE, "")?.trim().orEmpty()
        val incomingState = uri.getQueryParameter("state")?.trim().orEmpty()

        val error = uri.getQueryParameter("error")?.trim().orEmpty()
        if (error.isNotEmpty()) {
            statusText.text = "Login web rechazado: $error"
            return
        }

        if (expectedState.isNotEmpty() && incomingState != expectedState) {
            statusText.text = getString(R.string.extension_status_web_auth_state_error)
            return
        }

        val token = uri.getQueryParameter("accessToken")?.trim().orEmpty()
        val expiresAt = uri.getQueryParameter("expiresAt")?.trim().orEmpty()
        val usuarioId = uri.getQueryParameter("usuarioId")?.trim().orEmpty()
        val username = uri.getQueryParameter("username")?.trim().orEmpty()

        val result = ReminderSync.saveWebAuthLogin(
            context = this,
            baseUrlRaw = FIXED_BASE_URL,
            accessTokenRaw = token,
            expiresAtRaw = expiresAt,
            usuarioIdRaw = usuarioId,
            usernameRaw = username
        )

        statusText.text = result.message
        if (result.success) {
            prefs.edit { remove(KEY_WIDGET_WEB_AUTH_STATE) }
            if (username.isNotBlank()) {
                inputUsername.setText(username)
            }
            inputPassword.setText("")
            triggerWidgetRefresh()
        }
    }

    private fun triggerWidgetRefresh() {
        val manager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SoldadoWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(componentName)
        val intent = Intent(this, SoldadoWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }

    private fun applySystemInsets(root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    companion object {
        private const val PREFS_NAME = "soldado_widget_prefs"
        private const val KEY_WIDGET_WEB_AUTH_STATE = "widget_web_auth_state"
        private const val WIDGET_WEB_AUTH_SCHEME = "soldadowidget"
        private const val WIDGET_WEB_AUTH_HOST = "auth"
        private const val WIDGET_WEB_AUTH_REDIRECT_URI = "soldadowidget://auth/callback"
        private const val FIXED_BASE_URL = "https://deposito-apt1.vercel.app/"
    }
}
