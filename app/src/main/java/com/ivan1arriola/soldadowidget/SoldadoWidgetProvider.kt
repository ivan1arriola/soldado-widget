package com.ivan1arriola.soldadowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlin.math.max
import java.util.concurrent.Executors
import kotlin.random.Random

class SoldadoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            renderWidget(context, appWidgetManager, id, ReminderSync.phrase(context, id))
            requestSync(context, id, force = false)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        renderWidget(context, appWidgetManager, appWidgetId, ReminderSync.phrase(context, appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TAP) return

        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            handleInteraction(context, id)
        }
    }

    private fun handleInteraction(context: Context, appWidgetId: Int) {
        FeedbackEffects.playTap(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastTapTime = prefs.getLong("last_tap_$appWidgetId", 0L)
        var taps = prefs.getInt("taps_$appWidgetId", 0)

        if (now - lastTapTime > TAP_RESET_WINDOW_MS) {
            taps = 0
        }
        taps++
        val isDoubleTap = lastTapTime > 0L && now - lastTapTime <= DOUBLE_TAP_WINDOW_MS

        val mood = moodForTapCount(taps)
        val reactionFrame = reactionFrameForTapCount(taps, isDoubleTap)
        val reactionDuration = if (isDoubleTap) DOUBLE_TAP_REACTION_DURATION_MS else TAP_REACTION_DURATION_MS

        prefs.edit()
            .putInt("taps_$appWidgetId", taps)
            .putString("mood_$appWidgetId", mood.name)
            .putLong("last_tap_$appWidgetId", now)
            .putInt("tap_reaction_frame_$appWidgetId", reactionFrame)
            .putLong("tap_reaction_until_$appWidgetId", now + reactionDuration)
            .apply()

        val phrase = when {
            isDoubleTap -> context.getString(R.string.soldado_phrase_double_tap)
            taps == 1 -> context.getString(R.string.soldado_phrase_surprise)
            taps > 15 -> context.getString(R.string.soldado_phrase_sleep)
            taps > 10 -> context.getString(R.string.soldado_phrase_angry)
            taps > 5 -> context.getString(R.string.soldado_phrase_tired)
            else -> TAP_PHRASE_RES.randomPhrase(context)
        }

        val manager = AppWidgetManager.getInstance(context)
        renderWidget(context, manager, appWidgetId, phrase)
        requestSync(context, appWidgetId, force = false)
    }

    private fun renderWidget(context: Context, manager: AppWidgetManager, id: Int, phrase: String) {
        val layoutRes = if (isTallWidget(manager, id)) R.layout.widget_soldado_tall else R.layout.widget_soldado
        val views = RemoteViews(context.packageName, layoutRes)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val mood = SoldierMood.from(prefs.getString("mood_$id", SoldierMood.CALM.name))
        val stats = ReminderSync.readWidgetStats(context, id)
        val now = System.currentTimeMillis()
        val reactionUntil = prefs.getLong("tap_reaction_until_$id", 0L)
        val reactionFrame = prefs.getInt("tap_reaction_frame_$id", 0)
        val frameRes = if (now <= reactionUntil && reactionFrame != 0) {
            reactionFrame
        } else {
            frameFor(mood, id)
        }

        views.setImageViewResource(R.id.soldierImage, frameRes)
        views.setInt(R.id.soldierImage, "setImageAlpha", 255)

        val badgeText = when {
            !ReminderSync.isConfigured(context) -> context.getString(R.string.widget_state_unconfigured)
            stats.urgentCount > 0 -> context.getString(R.string.widget_indicator_urgent, stats.pendingCount, stats.urgentCount)
            stats.pendingCount > 0 -> context.getString(R.string.widget_indicator_pending, stats.pendingCount)
            else -> context.getString(R.string.widget_indicator_clear)
        }
        views.setTextViewText(R.id.moodBadge, badgeText)
        views.setTextColor(
            R.id.moodBadge,
            when {
                !ReminderSync.isConfigured(context) -> Color.parseColor("#F2F2F2")
                stats.urgentCount > 0 -> Color.parseColor("#FFD1C2")
                stats.pendingCount > 0 -> Color.parseColor("#DDF5C8")
                else -> Color.parseColor("#F4FFF2")
            }
        )

        views.setTextViewText(R.id.soldierPhrase, phrase)
        views.setTextColor(R.id.soldierPhrase, mood.phraseColor)

        val reminderColor = when {
            stats.urgentCount > 0 -> Color.parseColor("#FFD9C8")
            stats.pendingCount > 0 -> Color.parseColor("#DDF5C8")
            else -> mood.reminderColor
        }
        views.setTextColor(R.id.soldierReminder, reminderColor)
        views.setTextColor(R.id.soldierReminder2, reminderColor)

        val titles = ReminderSync.getTopTitles(context, id)
        if (titles.isNotEmpty()) {
            views.setTextViewText(R.id.soldierReminder, titles[0])
            if (titles.size > 1) {
                views.setTextViewText(R.id.soldierReminder2, titles[1])
                views.setViewVisibility(R.id.soldierReminder2, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.soldierReminder2, View.GONE)
            }
        } else {
            views.setTextViewText(R.id.soldierReminder, context.getString(R.string.reminder_line_unconfigured))
            views.setViewVisibility(R.id.soldierReminder2, View.GONE)
        }

        val tapIntent = Intent(context, SoldadoWidgetProvider::class.java).apply {
            action = ACTION_TAP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        views.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getBroadcast(
            context, id, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        views.setOnClickPendingIntent(R.id.soldierImage, PendingIntent.getBroadcast(
            context, id, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        views.setOnClickPendingIntent(R.id.moodBadge, PendingIntent.getBroadcast(
            context, id, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))

        val appIntent = Intent(context, MainActivity::class.java)
        views.setOnClickPendingIntent(R.id.soldierPhrase, PendingIntent.getActivity(
            context, id + 100, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        views.setOnClickPendingIntent(R.id.tasksContainer, PendingIntent.getActivity(
            context, id + 200, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))

        manager.updateAppWidget(id, views)
    }

    private fun isTallWidget(manager: AppWidgetManager, appWidgetId: Int): Boolean {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val effectiveHeight = max(minHeight, maxHeight)
        return effectiveHeight >= 180
    }

    private fun frameFor(mood: SoldierMood, appWidgetId: Int): Int {
        val frameOrder = when (mood) {
            SoldierMood.CALM -> intArrayOf(1, 4, 1, 5, 1, 4)
            SoldierMood.ALERT -> intArrayOf(1, 5, 1, 4, 5, 1)
            SoldierMood.TIRED -> intArrayOf(2, 2, 3, 2, 2, 4)
            SoldierMood.ANGRY -> intArrayOf(5, 1, 5, 4, 5, 1)
            SoldierMood.SLEEPY -> intArrayOf(2, 2, 2, 3, 2, 2)
        }
        val tick = ((System.currentTimeMillis() / 750L) + appWidgetId).toInt()
        val frameIndex = frameOrder[tick % frameOrder.size]
        return when (frameIndex) {
            1 -> R.drawable.soldado_frame_1
            2 -> R.drawable.soldado_frame_2
            3 -> R.drawable.soldado_frame_3
            4 -> R.drawable.soldado_frame_4
            else -> R.drawable.soldado_frame_5
        }
    }

    private fun reactionFrameForTapCount(taps: Int, isDoubleTap: Boolean): Int {
        if (isDoubleTap) return R.drawable.soldado_frame_5

        return when {
            taps > 15 -> R.drawable.soldado_frame_3
            taps > 10 -> R.drawable.soldado_frame_5
            taps > 5 -> R.drawable.soldado_frame_2
            else -> listOf(
                R.drawable.soldado_frame_1,
                R.drawable.soldado_frame_4,
                R.drawable.soldado_frame_5
            ).random()
        }
    }

    private fun requestSync(context: Context, appWidgetId: Int, force: Boolean) {
        if (!ReminderSync.isConfigured(context)) return

        val now = System.currentTimeMillis()
        val lastSync = ReminderSync.readLastSyncTime(context, appWidgetId)
        if (!force && now - lastSync < MIN_SYNC_INTERVAL_MS) return

        ioExecutor.execute {
            try {
                val snapshot = ReminderSync.fetchSnapshot(context)
                ReminderSync.saveSnapshotForWidget(context, appWidgetId, snapshot)
                val phrase = ReminderSync.phraseWithTasks(context)
                renderWidget(context, AppWidgetManager.getInstance(context), appWidgetId, phrase)
            } catch (_: Throwable) {
                // Mantener widget funcional incluso si falla red.
            }
        }
    }

    private fun moodForTapCount(taps: Int): SoldierMood {
        return when {
            taps > 15 -> SoldierMood.SLEEPY
            taps > 10 -> SoldierMood.ANGRY
            taps > 5 -> SoldierMood.TIRED
            taps == 1 -> SoldierMood.ALERT
            else -> listOf(SoldierMood.CALM, SoldierMood.ALERT).random()
        }
    }

    private fun List<Int>.randomPhrase(context: Context): String {
        val idx = Random.nextInt(size)
        return context.getString(this[idx])
    }

    private enum class SoldierMood(
        val phraseColor: Int,
        val reminderColor: Int,
        val badgeTextColor: Int,
        val widgetBackgroundRes: Int,
        val avatarHaloRes: Int,
        val badgeBackgroundRes: Int,
        val badgeLabelRes: Int
    ) {
        CALM(
            phraseColor = Color.parseColor("#F4FFF2"),
            reminderColor = Color.parseColor("#CFE9BD"),
            badgeTextColor = Color.parseColor("#ECF9DA"),
            widgetBackgroundRes = R.drawable.widget_bg_calm,
            avatarHaloRes = R.drawable.soldier_display_bg_calm,
            badgeBackgroundRes = R.drawable.widget_badge_calm,
            badgeLabelRes = R.string.widget_state_calm
        ),
        ALERT(
            phraseColor = Color.parseColor("#FFF5C9"),
            reminderColor = Color.parseColor("#F8EFB8"),
            badgeTextColor = Color.parseColor("#FFF7CC"),
            widgetBackgroundRes = R.drawable.widget_bg_alert,
            avatarHaloRes = R.drawable.soldier_display_bg_alert,
            badgeBackgroundRes = R.drawable.widget_badge_alert,
            badgeLabelRes = R.string.widget_state_alert
        ),
        TIRED(
            phraseColor = Color.parseColor("#EAEAEA"),
            reminderColor = Color.parseColor("#D8DED3"),
            badgeTextColor = Color.parseColor("#EFEFEF"),
            widgetBackgroundRes = R.drawable.widget_bg_tired,
            avatarHaloRes = R.drawable.soldier_display_bg_tired,
            badgeBackgroundRes = R.drawable.widget_badge_tired,
            badgeLabelRes = R.string.widget_state_tired
        ),
        ANGRY(
            phraseColor = Color.parseColor("#FFD9D9"),
            reminderColor = Color.parseColor("#FFD1C5"),
            badgeTextColor = Color.parseColor("#FFE4DE"),
            widgetBackgroundRes = R.drawable.widget_bg_angry,
            avatarHaloRes = R.drawable.soldier_display_bg_angry,
            badgeBackgroundRes = R.drawable.widget_badge_angry,
            badgeLabelRes = R.string.widget_state_angry
        ),
        SLEEPY(
            phraseColor = Color.parseColor("#DCE7FF"),
            reminderColor = Color.parseColor("#D2E0F0"),
            badgeTextColor = Color.parseColor("#E5EDFF"),
            widgetBackgroundRes = R.drawable.widget_bg_sleepy,
            avatarHaloRes = R.drawable.soldier_display_bg_sleepy,
            badgeBackgroundRes = R.drawable.widget_badge_sleepy,
            badgeLabelRes = R.string.widget_state_sleepy
        );

        companion object {
            fun from(value: String?): SoldierMood {
                return entries.firstOrNull { it.name == value } ?: CALM
            }
        }
    }

    companion object {
        private const val ACTION_TAP = "com.ivan1arriola.soldadowidget.ACTION_TAP"
        private const val PREFS_NAME = "soldado_widget_prefs"
        private const val MIN_SYNC_INTERVAL_MS = 60_000L
        private const val TAP_RESET_WINDOW_MS = 30_000L
        private const val TAP_REACTION_DURATION_MS = 1_200L
        private const val DOUBLE_TAP_WINDOW_MS = 420L
        private const val DOUBLE_TAP_REACTION_DURATION_MS = 1_700L

        private val ioExecutor = Executors.newSingleThreadExecutor()

        private val TAP_PHRASE_RES = listOf(
            R.string.soldado_phrase_1,
            R.string.soldado_phrase_2,
            R.string.soldado_phrase_3,
            R.string.soldado_phrase_4,
            R.string.soldado_phrase_5,
            R.string.soldado_phrase_idle
        )
    }
}
