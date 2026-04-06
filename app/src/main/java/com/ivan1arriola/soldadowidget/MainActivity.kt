package com.ivan1arriola.soldadowidget

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var taps = 0
    private var guardMode = false
    private var idleAnimator: AnimatorSet? = null
    private var lastShownPhrase: String = ""
    private var reminderTapCounter = 0
    private var reminderRefreshInFlight = false
    private var lastReminderRefreshAtMs = 0L
    private var lastReminderShownAtMs = 0L
    private var cachedPendingCount: Int? = null
    private var cachedUrgentCount: Int? = null
    private var cachedTopTitle: String? = null

    private lateinit var soldierImage: ImageView
    private lateinit var soldierPhrase: TextView
    private lateinit var helpText: TextView
    private lateinit var btnLoginWeb: Button

    private val soldierFrames = listOf(
        R.drawable.soldado_frame_1,
        R.drawable.soldado_frame_2,
        R.drawable.soldado_frame_3,
        R.drawable.soldado_frame_4,
        R.drawable.soldado_frame_5
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
        R.drawable.soldado_frame_4 to R.string.soldado_phrase_order_scan,
        R.drawable.soldado_frame_2 to R.string.soldado_phrase_order_salute
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(R.id.rootLayout)
        applySystemInsets(rootLayout)

        val soldierContainer = findViewById<View>(R.id.soldierContainer)
        soldierImage = findViewById(R.id.mainSoldierImage)
        soldierPhrase = findViewById(R.id.mainSoldierPhrase)
        helpText = findViewById(R.id.helpText)

        val btnOrder = findViewById<Button>(R.id.btnOrder)
        val btnReset = findViewById<Button>(R.id.btnReset)
        btnLoginWeb = findViewById(R.id.btnGoToConfig)
        val btnSync = findViewById<Button>(R.id.btnSync)
        val btnViewTasks = findViewById<Button>(R.id.btnViewTasks)

        btnLoginWeb.setOnClickListener {
            startWebLogin()
        }

        btnSync.setOnClickListener {
            syncNow()
        }

        btnViewTasks.setOnClickListener {
            startActivity(Intent(this, RemindersListActivity::class.java))
        }

        handleWebLoginCallback(intent)
        updateLoginUi()

        soldierContainer.setOnClickListener {
            taps++
            reminderTapCounter++
            FeedbackEffects.playTap(this)

            playTapReaction()
            maybeRefreshPendingSnapshot()

            val nextFrame = when {
                taps > 15 -> 2
                taps > 8 -> (0..4).random()
                else -> listOf(0, 3, 4).random()
            }

            soldierImage.setImageResource(soldierFrames[nextFrame])
            setSoldierPhrase(nextPhraseForTap())
            emphasizePhraseBubble()
        }

        soldierContainer.setOnLongClickListener {
            guardMode = !guardMode
            FeedbackEffects.playLongPress(this)
            if (guardMode) {
                soldierImage.setImageResource(R.drawable.soldado_frame_2)
                setSoldierPhrase(getString(R.string.soldado_phrase_guard_on))
            } else {
                soldierImage.setImageResource(R.drawable.soldado_frame_1)
                setSoldierPhrase(getString(R.string.soldado_phrase_guard_off))
            }
            emphasizePhraseBubble()
            true
        }

        btnOrder.setOnClickListener {
            FeedbackEffects.playCommand(this)
            val (frameRes, phraseRes) = orderReactions.random()
            soldierImage.setImageResource(frameRes)
            setSoldierPhrase(getString(phraseRes))
            playCommandReaction()
            emphasizePhraseBubble()
        }

        btnReset.setOnClickListener {
            FeedbackEffects.playTap(this)
            taps = 0
            guardMode = false
            reminderTapCounter = 0
            soldierImage.setImageResource(R.drawable.soldado_frame_1)
            setSoldierPhrase(getString(R.string.soldado_phrase_idle))
            soldierImage.rotation = 0f
            soldierImage.scaleX = 1f
            soldierImage.scaleY = 1f
            soldierImage.translationY = 0f
            soldierImage.translationX = 0f

            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit {
                clear()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startIdleAnimation()
        updateLoginUi()
    }

    override fun onPause() {
        stopIdleAnimation()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWebLoginCallback(intent)
        updateLoginUi()
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

    private fun startIdleAnimation() {
        stopIdleAnimation()

        val breatheY = ObjectAnimator.ofFloat(soldierImage, View.TRANSLATION_Y, 0f, -7f, 0f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val swayX = ObjectAnimator.ofFloat(soldierImage, View.TRANSLATION_X, 0f, 2.5f, -2.5f, 0f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val gentleScaleX = ObjectAnimator.ofFloat(soldierImage, View.SCALE_X, 1f, 1.04f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val gentleScaleY = ObjectAnimator.ofFloat(soldierImage, View.SCALE_Y, 1f, 0.98f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        idleAnimator = AnimatorSet().apply {
            playTogether(breatheY, swayX, gentleScaleX, gentleScaleY)
            start()
        }
    }

    private fun stopIdleAnimation() {
        idleAnimator?.cancel()
        idleAnimator = null
    }

    private fun playTapReaction() {
        soldierImage.animate().cancel()

        soldierImage.animate()
            .scaleX(1.18f)
            .scaleY(0.9f)
            .rotationBy(7f)
            .setDuration(85)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                soldierImage.animate()
                    .scaleX(0.95f)
                    .scaleY(1.1f)
                    .rotationBy(-11f)
                    .setDuration(110)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .withEndAction {
                        soldierImage.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(130)
                            .setInterpolator(OvershootInterpolator(1.4f))
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun playCommandReaction() {
        soldierImage.animate().cancel()
        soldierImage.animate()
            .rotationBy(10f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(120)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                soldierImage.animate()
                    .rotation(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.3f))
                    .start()
            }
            .start()
    }

    private fun emphasizePhraseBubble() {
        soldierPhrase.animate().cancel()
        soldierPhrase.alpha = 0.78f
        soldierPhrase.scaleX = 0.97f
        soldierPhrase.scaleY = 0.97f
        soldierPhrase.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun nextPhraseForTap(): String {
        reminderPhraseIfDue()?.let { return ensurePhraseChanges(it) }

        val options = mutableListOf<String>()
        if (guardMode) {
            options.add(getString(R.string.soldado_phrase_guard_on))
            options.add(getString(R.string.soldado_phrase_order_scan))
            options.add(getString(R.string.soldado_phrase_3))
        } else {
            when {
                taps == 1 -> options.add(getString(R.string.soldado_phrase_surprise))
                taps > 15 -> options.add(getString(R.string.soldado_phrase_sleep))
                taps > 10 -> options.add(getString(R.string.soldado_phrase_angry))
                taps > 5 -> options.add(getString(R.string.soldado_phrase_tired))
            }
            options.addAll(tapPhraseRes.map { getString(it) })
        }

        val candidates = options.distinct().filter { it != lastShownPhrase }
        return if (candidates.isNotEmpty()) candidates.random() else options.random()
    }

    private fun reminderPhraseIfDue(): String? {
        val pending = cachedPendingCount ?: return null
        if (pending <= 0) return null

        val now = System.currentTimeMillis()
        val enoughTaps = reminderTapCounter >= REMINDER_TAP_INTERVAL
        val enoughTime = now - lastReminderShownAtMs >= REMINDER_MIN_INTERVAL_MS
        if (!enoughTaps || !enoughTime) return null

        reminderTapCounter = 0
        lastReminderShownAtMs = now

        val urgent = cachedUrgentCount ?: 0
        val topTitle = cachedTopTitle?.trim().orEmpty()

        return when {
            urgent > 0 -> getString(R.string.reminder_phrase_urgent, urgent)
            topTitle.isNotEmpty() -> getString(R.string.reminder_phrase_next, truncate(topTitle, 34))
            else -> getString(R.string.reminder_phrase_pending, pending)
        }
    }

    private fun maybeRefreshPendingSnapshot() {
        val now = System.currentTimeMillis()
        val config = ReminderSync.readConfig(this)
        if (!config.isConfigured) return
        if (reminderRefreshInFlight) return
        if (now - lastReminderRefreshAtMs < REMINDER_REFRESH_INTERVAL_MS) return

        reminderRefreshInFlight = true
        lastReminderRefreshAtMs = now

        Thread {
            try {
                val snapshot = ReminderSync.fetchSnapshot(this)
                if (snapshot != null) {
                    runOnUiThread {
                        updateCachedReminder(snapshot.pendingCount, snapshot.urgentCount, snapshot.topTitles.firstOrNull())
                    }
                }
            } finally {
                runOnUiThread { reminderRefreshInFlight = false }
            }
        }.start()
    }

    private fun updateCachedReminder(pending: Int, urgent: Int, title: String?) {
        cachedPendingCount = pending
        cachedUrgentCount = urgent
        cachedTopTitle = title
    }

    private fun ensurePhraseChanges(candidate: String): String {
        if (candidate != lastShownPhrase) return candidate
        val fallback = tapPhraseRes.map { getString(it) }.firstOrNull { it != lastShownPhrase }
        return fallback ?: candidate
    }

    private fun setSoldierPhrase(text: String) {
        val value = ensurePhraseChanges(text)
        soldierPhrase.text = value
        lastShownPhrase = value
    }

    private fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        return value.take(maxLength - 3) + "..."
    }

    private fun syncNow() {
        val config = ReminderSync.readConfig(this)
        if (config.username.isBlank()) {
            setSoldierPhrase(getString(R.string.extension_status_unconfigured))
            emphasizePhraseBubble()
            return
        }

        if (ReminderSync.isTokenExpired(config)) {
            setSoldierPhrase(getString(R.string.extension_status_session_expired))
            emphasizePhraseBubble()
            return
        }

        setSoldierPhrase(getString(R.string.extension_status_syncing))
        emphasizePhraseBubble()

        Thread {
            try {
                val snapshot = ReminderSync.fetchSnapshot(this)
                runOnUiThread {
                    if (snapshot == null) {
                        setSoldierPhrase(getString(R.string.extension_status_sync_fail))
                    } else {
                        updateCachedReminder(snapshot.pendingCount, snapshot.urgentCount, snapshot.topTitles.firstOrNull())
                        setSoldierPhrase(
                            getString(
                                R.string.extension_status_sync_ok,
                                snapshot.pendingCount,
                                snapshot.urgentCount
                            )
                        )
                        triggerWidgetRefresh()
                    }
                    emphasizePhraseBubble()
                }
            } catch (_: Throwable) {
                runOnUiThread {
                    setSoldierPhrase(getString(R.string.extension_status_sync_fail))
                    emphasizePhraseBubble()
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

        setSoldierPhrase(getString(R.string.extension_status_opening_web_auth))
        emphasizePhraseBubble()
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
            setSoldierPhrase(getString(R.string.main_login_rejected, error))
            emphasizePhraseBubble()
            return
        }

        if (expectedState.isNotEmpty() && incomingState != expectedState) {
            setSoldierPhrase(getString(R.string.extension_status_web_auth_state_error))
            emphasizePhraseBubble()
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

        setSoldierPhrase(result.message)
        emphasizePhraseBubble()
        if (result.success) {
            prefs.edit { remove(KEY_WIDGET_WEB_AUTH_STATE) }
            triggerWidgetRefresh()
        }
    }

    private fun updateLoginUi() {
        val config = ReminderSync.readConfig(this)
        val sessionActiva = config.username.isNotBlank() && !ReminderSync.isTokenExpired(config)

        if (sessionActiva) {
            btnLoginWeb.text = getString(R.string.main_btn_logged_as, config.username)
            helpText.text = getString(R.string.main_help_logged, config.username)
        } else {
            btnLoginWeb.text = getString(R.string.main_btn_login_web)
            helpText.text = getString(R.string.main_help)
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

    companion object {
        private const val PREFS_NAME = "soldado_widget_prefs"
        private const val KEY_WIDGET_WEB_AUTH_STATE = "widget_web_auth_state"
        private const val WIDGET_WEB_AUTH_SCHEME = "soldadowidget"
        private const val WIDGET_WEB_AUTH_HOST = "auth"
        private const val WIDGET_WEB_AUTH_REDIRECT_URI = "soldadowidget://auth/callback"
        private const val FIXED_BASE_URL = "https://deposito-apt1.vercel.app/"
        private const val REMINDER_TAP_INTERVAL = 4
        private const val REMINDER_MIN_INTERVAL_MS = 25_000L
        private const val REMINDER_REFRESH_INTERVAL_MS = 90_000L
    }
}
