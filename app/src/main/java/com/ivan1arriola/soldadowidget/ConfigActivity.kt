package com.ivan1arriola.soldadowidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class ConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val root = findViewById<View>(R.id.configRoot)
        applySystemInsets(root)

        val inputApiBaseUrl = findViewById<EditText>(R.id.inputApiBaseUrl)
        val inputUsername = findViewById<EditText>(R.id.inputUsername)
        val inputPassword = findViewById<EditText>(R.id.inputPassword)
        val btnSave = findViewById<Button>(R.id.btnSaveExtension)
        val btnLogin = findViewById<Button>(R.id.btnLoginExtension)
        val btnSync = findViewById<Button>(R.id.btnSyncExtension)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnViewTasks = findViewById<Button>(R.id.btnViewTasks)
        val statusText = findViewById<TextView>(R.id.extensionStatusText)

        // Cargar config actual
        val config = ReminderSync.readConfig(this)
        inputApiBaseUrl.setText(config.baseUrl)
        inputUsername.setText(config.username)

        btnSave.setOnClickListener {
            val currentConfig = ReminderSync.readConfig(this)
            val newConfig = ReminderSync.ExtensionConfig(
                baseUrl = inputApiBaseUrl.text.toString(),
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
            loginNow(
                statusText = statusText,
                baseUrl = inputApiBaseUrl.text.toString(),
                username = inputUsername.text.toString(),
                password = inputPassword.text.toString()
            )
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
    }

    private fun syncNow(statusView: TextView) {
        val config = ReminderSync.readConfig(this)
        if (config.baseUrl.isBlank() || config.username.isBlank()) {
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

    private fun loginNow(statusText: TextView, baseUrl: String, username: String, password: String) {
        statusText.text = getString(R.string.extension_status_authenticating)
        Thread {
            val result = ReminderSync.login(
                context = this,
                baseUrlRaw = baseUrl,
                usernameRaw = username,
                passwordRaw = password
            )
            runOnUiThread {
                statusText.text = result.message
                if (result.success) {
                    triggerWidgetRefresh()
                }
            }
        }.start()
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
}
