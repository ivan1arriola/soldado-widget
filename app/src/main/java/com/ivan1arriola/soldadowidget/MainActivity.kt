package com.ivan1arriola.soldadowidget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.core.content.edit
import android.widget.EditText
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class MainActivity : AppCompatActivity() {

    private var taps = 0
    private var guardMode = false

    private val soldierFrames = listOf(
        R.drawable.soldado_frame_0,
        R.drawable.soldado_frame_1,
        R.drawable.soldado_frame_2,
        R.drawable.soldado_frame_3
    )

    private val tapPhraseRes = listOf(
        R.string.soldado_phrase_1,
        R.string.soldado_phrase_2,
        R.string.soldado_phrase_3,
        R.string.soldado_phrase_4,
        R.string.soldado_phrase_5,
        R.string.soldado_phrase_idle
    )

    private val orderReactions = listOf(
        R.drawable.soldado_frame_1 to R.string.soldado_phrase_order_march,
        R.drawable.soldado_frame_0 to R.string.soldado_phrase_order_scan,
        R.drawable.soldado_frame_2 to R.string.soldado_phrase_order_salute
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(R.id.rootLayout)
        applySystemInsets(rootLayout)

        val soldierContainer = findViewById<View>(R.id.soldierContainer)
        val soldierImage = findViewById<ImageView>(R.id.mainSoldierImage)
        val soldierPhrase = findViewById<TextView>(R.id.mainSoldierPhrase)
        val btnOrder = findViewById<Button>(R.id.btnOrder)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val inputApiBaseUrl = findViewById<EditText>(R.id.inputApiBaseUrl)
        val inputUsuarioId = findViewById<EditText>(R.id.inputUsuarioId)
        val inputToken = findViewById<EditText>(R.id.inputToken)
        val btnSaveExtension = findViewById<Button>(R.id.btnSaveExtension)
        val btnSyncExtension = findViewById<Button>(R.id.btnSyncExtension)
        val extensionStatusText = findViewById<TextView>(R.id.extensionStatusText)

        val initialConfig = ReminderSync.readConfig(this)
        inputApiBaseUrl.setText(initialConfig.baseUrl)
        inputUsuarioId.setText(initialConfig.usuarioId)
        inputToken.setText(initialConfig.token)
        extensionStatusText.text = if (initialConfig.isConfigured) {
            getString(R.string.reminder_line_pending, 0)
        } else {
            getString(R.string.extension_status_unconfigured)
        }

        btnSaveExtension.setOnClickListener {
            val newConfig = ReminderSync.ExtensionConfig(
                baseUrl = inputApiBaseUrl.text.toString(),
                usuarioId = inputUsuarioId.text.toString(),
                token = inputToken.text.toString()
            )
            ReminderSync.saveConfig(this, newConfig)
            extensionStatusText.text = getString(R.string.extension_status_saved)
            triggerWidgetRefresh()
            syncExtensionNow(extensionStatusText)
        }

        btnSyncExtension.setOnClickListener {
            syncExtensionNow(extensionStatusText)
        }

        // Toque corto: reaccion aleatoria o modo guardia.
        soldierContainer.setOnClickListener {
            taps++
            FeedbackEffects.playTap(this)

            // Animacion de "salto" o escala al tocar
            soldierImage.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .withEndAction {
                    soldierImage.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()

            if (guardMode) {
                soldierImage.setImageResource(R.drawable.soldado_frame_2)
                soldierPhrase.text = getString(R.string.soldado_phrase_guard_on)
                return@setOnClickListener
            }

            val nextFrame = when {
                taps > 15 -> 3
                taps > 8 -> (1..2).random()
                else -> (0..2).random()
            }

            val phrase = when {
                taps == 1 -> getString(R.string.soldado_phrase_surprise)
                taps > 15 -> getString(R.string.soldado_phrase_sleep)
                taps > 10 -> getString(R.string.soldado_phrase_angry)
                taps > 5 -> getString(R.string.soldado_phrase_tired)
                else -> getString(tapPhraseRes.random())
            }

            soldierImage.setImageResource(soldierFrames[nextFrame])
            soldierPhrase.text = phrase
        }

        // Toque largo: activa/desactiva modo guardia.
        soldierContainer.setOnLongClickListener {
            guardMode = !guardMode
            FeedbackEffects.playLongPress(this)
            if (guardMode) {
                soldierImage.setImageResource(R.drawable.soldado_frame_2)
                soldierPhrase.text = getString(R.string.soldado_phrase_guard_on)
            } else {
                soldierImage.setImageResource(R.drawable.soldado_frame_0)
                soldierPhrase.text = getString(R.string.soldado_phrase_guard_off)
            }
            true
        }

        // Boton extra de interaccion: da una orden y el soldado reacciona.
        btnOrder.setOnClickListener {
            FeedbackEffects.playCommand(this)
            val (frameRes, phraseRes) = orderReactions.random()
            soldierImage.setImageResource(frameRes)
            soldierPhrase.text = getString(phraseRes)
        }

        btnReset.setOnClickListener {
            FeedbackEffects.playTap(this)
            taps = 0
            guardMode = false
            soldierImage.setImageResource(R.drawable.soldado_frame_0)
            soldierPhrase.text = getString(R.string.soldado_phrase_idle)

            // Limpiar tambien las SharedPreferences del widget.
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit {
                clear()
            }

            extensionStatusText.text = getString(R.string.extension_status_unconfigured)
            inputApiBaseUrl.setText("")
            inputUsuarioId.setText("")
            inputToken.setText("")
            triggerWidgetRefresh()
        }
    }

    private fun syncExtensionNow(statusView: TextView) {
        val config = ReminderSync.readConfig(this)
        if (!config.isConfigured) {
            statusView.text = getString(R.string.extension_status_unconfigured)
            return
        }

        statusView.text = getString(R.string.btn_sync_extension_label)
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
        val componentName = android.content.ComponentName(this, SoldadoWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(componentName)
        if (ids.isEmpty()) return

        val intent = android.content.Intent(this, SoldadoWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }

    private fun applySystemInsets(root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft + bars.left,
                top = initialTop + bars.top,
                right = initialRight + bars.right,
                bottom = initialBottom + bars.bottom
            )
            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    companion object {
        private const val PREFS_NAME = "soldado_widget_prefs"
    }
}
