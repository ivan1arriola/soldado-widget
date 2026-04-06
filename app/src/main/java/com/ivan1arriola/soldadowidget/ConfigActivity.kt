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
        val inputUsuarioId = findViewById<EditText>(R.id.inputUsuarioId)
        val inputToken = findViewById<EditText>(R.id.inputToken)
        val btnSave = findViewById<Button>(R.id.btnSaveExtension)
        val btnSync = findViewById<Button>(R.id.btnSyncExtension)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val statusText = findViewById<TextView>(R.id.extensionStatusText)

        // Cargar config actual
        val config = ReminderSync.readConfig(this)
        inputApiBaseUrl.setText(config.baseUrl)
        inputUsuarioId.setText(config.usuarioId)
        inputToken.setText(config.token)

        btnSave.setOnClickListener {
            val newConfig = ReminderSync.ExtensionConfig(
                baseUrl = inputApiBaseUrl.text.toString(),
                usuarioId = inputUsuarioId.text.toString(),
                token = inputToken.text.toString()
            )
            ReminderSync.saveConfig(this, newConfig)
            statusText.text = getString(R.string.extension_status_saved)
            triggerWidgetRefresh()
        }

        btnSync.setOnClickListener {
            syncNow(statusText)
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun syncNow(statusView: TextView) {
        val config = ReminderSync.readConfig(this)
        if (!config.isConfigured) {
            statusView.text = getString(R.string.extension_status_unconfigured)
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
