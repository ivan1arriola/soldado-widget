package com.ivan1arriola.soldadowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlin.random.Random
import java.util.concurrent.Executors

class SoldadoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val frame = loadFrame(context, appWidgetId)
            val phrase = ReminderSync.phrase(context, appWidgetId)
            updateWidget(context, appWidgetManager, appWidgetId, frame, phrase, options)
            requestSync(context, appWidgetId, force = false)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val frame = loadFrame(context, appWidgetId)
        val phrase = ReminderSync.phrase(context, appWidgetId)
        updateWidget(context, appWidgetManager, appWidgetId, frame, phrase, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_WIDGET_TAP) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                handleTap(context, appWidgetId)
            } else {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, SoldadoWidgetProvider::class.java)
                )
                ids.forEach { id -> handleTap(context, id) }
            }
        }
    }

    private fun handleTap(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        FeedbackEffects.playTap(context)
        
        val lastTapTime = prefs.getLong("last_tap_$appWidgetId", 0L)
        val currentTime = System.currentTimeMillis()
        var taps = prefs.getInt("taps_$appWidgetId", 0)
        
        if (currentTime - lastTapTime > 30000) {
            taps = 0
        }
        taps++
        
        val nextFrame = when {
            taps > 15 -> 3 // Agotado
            taps > 8 -> listOf(1, 2, 3).random() // Inquieto sin deformar
            else -> listOf(0, 1, 2).random() // Normal estable
        }
        
        prefs.edit()
            .putInt("taps_$appWidgetId", taps)
            .putInt("frame_$appWidgetId", nextFrame)
            .putLong("last_tap_$appWidgetId", currentTime)
            .apply()

        val phrase = when {
            taps == 1 -> context.getString(R.string.soldado_phrase_surprise)
            taps > 15 -> context.getString(R.string.soldado_phrase_sleep)
            taps > 10 -> context.getString(R.string.soldado_phrase_angry)
            taps > 5 -> context.getString(R.string.soldado_phrase_tired)
            else -> TAP_PHRASE_RES.randomPhrase(context)
        }

        val manager = AppWidgetManager.getInstance(context)
        val options = manager.getAppWidgetOptions(appWidgetId)
        updateWidget(context, manager, appWidgetId, nextFrame, phrase, options)
        requestSync(context, appWidgetId, force = false)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        appWidgetIds.forEach { id -> 
            editor.remove("frame_$id")
            editor.remove("taps_$id")
            editor.remove("last_tap_$id")
            ReminderSync.clearSnapshotForWidget(context, id)
        }
        editor.apply()
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        frame: Int,
        phrase: String,
        options: Bundle
    ) {
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val layoutRes = if (minHeight >= 140) R.layout.widget_soldado_tall else R.layout.widget_soldado
        val views = RemoteViews(context.packageName, layoutRes)

        // Escalado por altura, siempre cuadrado para evitar deformación.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val soldierSizeDp = when {
                minHeight >= 260 -> 150f
                minHeight >= 200 -> 124f
                minHeight >= 140 -> 98f
                else -> 68f
            }
            views.setViewLayoutWidth(R.id.soldierImage, soldierSizeDp, android.util.TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.soldierImage, soldierSizeDp, android.util.TypedValue.COMPLEX_UNIT_DIP)
        }

        views.setImageViewResource(R.id.soldierImage, SOLDIER_FRAMES[frame])
        views.setTextViewText(R.id.soldierPhrase, phrase)

        val titles = ReminderSync.getTopTitles(context, appWidgetId)
        val isLarge = minHeight >= 140

        if (titles.isEmpty()) {
            views.setTextViewText(R.id.soldierReminder, ReminderSync.reminderLine(context, appWidgetId))
            views.setViewVisibility(R.id.soldierReminder2, View.GONE)
        } else {
            views.setTextViewText(R.id.soldierReminder, titles[0])
            if (isLarge && titles.size > 1) {
                views.setTextViewText(R.id.soldierReminder2, titles[1])
                views.setViewVisibility(R.id.soldierReminder2, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.soldierReminder2, View.GONE)
            }
        }

        views.setOnClickPendingIntent(R.id.widgetRoot, clickIntent(context, appWidgetId))
        views.setOnClickPendingIntent(R.id.soldierImage, clickIntent(context, appWidgetId))
        views.setOnClickPendingIntent(R.id.soldierPhrase, openAppIntent(context, appWidgetId))
        views.setOnClickPendingIntent(R.id.tasksContainer, openAppIntent(context, appWidgetId))

        manager.updateAppWidget(appWidgetId, views)
    }

    private fun requestSync(context: Context, appWidgetId: Int, force: Boolean) {
        if (!ReminderSync.isConfigured(context)) return

        val now = System.currentTimeMillis()
        val lastSync = ReminderSync.readLastSyncTime(context, appWidgetId)
        if (!force && now - lastSync < MIN_SYNC_INTERVAL_MS) return

        ioExecutor.execute {
            val snapshot = ReminderSync.fetchSnapshot(context)
            ReminderSync.saveSnapshotForWidget(context, appWidgetId, snapshot)

            val frame = loadFrame(context, appWidgetId)
            val phrase = ReminderSync.phraseWithTasks(context)
            
            val manager = AppWidgetManager.getInstance(context)
            val options = manager.getAppWidgetOptions(appWidgetId)
            updateWidget(context, manager, appWidgetId, frame, phrase, options)
        }
    }

    private fun clickIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, SoldadoWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_TAP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadFrame(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("frame_$appWidgetId", 0)
    }

    private fun openAppIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId + OPEN_APP_REQUEST_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun List<Int>.randomPhrase(context: Context): String {
        val idx = Random.nextInt(size)
        return context.getString(this[idx])
    }

    companion object {
        private const val PREFS_NAME = "soldado_widget_prefs"
        private const val ACTION_WIDGET_TAP = "com.ivan1arriola.soldadowidget.ACTION_WIDGET_TAP"
        private const val OPEN_APP_REQUEST_OFFSET = 10_000
        private const val MIN_SYNC_INTERVAL_MS = 60_000L

        private val ioExecutor = Executors.newSingleThreadExecutor()

        private val SOLDIER_FRAMES = listOf(
            R.drawable.soldado_frame_0,
            R.drawable.soldado_frame_1,
            R.drawable.soldado_frame_2,
            R.drawable.soldado_frame_3
        )

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
