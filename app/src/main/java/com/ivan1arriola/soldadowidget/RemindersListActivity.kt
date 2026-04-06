package com.ivan1arriola.soldadowidget

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class RemindersListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders_list)

        val root = findViewById<View>(R.id.remindersListRoot)
        applySystemInsets(root)

        val titleText = findViewById<TextView>(R.id.remindersListTitle)
        val statusText = findViewById<TextView>(R.id.remindersListStatus)
        val progressBar = findViewById<ProgressBar>(R.id.remindersListProgress)
        val tasksContainer = findViewById<LinearLayout>(R.id.remindersListContainer)
        val btnRefresh = findViewById<Button>(R.id.btnRefreshReminders)
        val btnBack = findViewById<Button>(R.id.btnBackReminders)
        val btnCreateTarea = findViewById<Button>(R.id.btnCreateTarea)

        btnBack.setOnClickListener {
            finish()
        }

        btnRefresh.setOnClickListener {
            loadTasks(progressBar, statusText, tasksContainer)
        }

        btnCreateTarea.setOnClickListener {
            startActivity(Intent(this, CreateEditTareaActivity::class.java))
        }

        loadTasks(progressBar, statusText, tasksContainer)
    }

    private fun loadTasks(
        progressBar: ProgressBar,
        statusText: TextView,
        tasksContainer: LinearLayout
    ) {
        if (!ReminderSync.isConfigured(this)) {
            statusText.text = getString(R.string.reminder_list_unconfigured)
            progressBar.visibility = View.GONE
            return
        }

        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.reminder_list_loading)
        tasksContainer.removeAllViews()

        Thread {
            val response = ReminderSync.fetchTasks(this)
            runOnUiThread {
                progressBar.visibility = View.GONE
                if (response == null) {
                    statusText.text = getString(R.string.reminder_list_load_failed)
                    return@runOnUiThread
                }

                if (response.tareas.isEmpty()) {
                    statusText.text = getString(R.string.reminder_list_empty)
                    return@runOnUiThread
                }

                statusText.text = getString(R.string.reminder_list_count, response.tareas.size)
                displayTasks(tasksContainer, response.tareas)
            }
        }.start()
    }

    private fun displayTasks(container: LinearLayout, tareas: List<ReminderSync.ReminderTask>) {
        for (tarea in tareas) {
            val taskView = layoutInflater.inflate(R.layout.item_reminder_task, container, false)

            val titleView = taskView.findViewById<TextView>(R.id.taskTitle)
            val notaView = taskView.findViewById<TextView>(R.id.taskNote)
            val fechaView = taskView.findViewById<TextView>(R.id.taskFecha)
            val prioridadView = taskView.findViewById<TextView>(R.id.taskPrioridad)
            val completaBtn = taskView.findViewById<Button>(R.id.btnCompleteTask)

            titleView.text = tarea.titulo
            notaView.text = tarea.nota.ifEmpty { "(sin nota)" }
            notaView.visibility = if (tarea.nota.isEmpty()) View.GONE else View.VISIBLE

            if (tarea.fechaLimite != null) {
                fechaView.text = "Vence: ${tarea.fechaLimite}"
                fechaView.visibility = View.VISIBLE
            } else {
                fechaView.visibility = View.GONE
            }

            prioridadView.text = tarea.prioridad
            prioridadView.setBackgroundColor(
                if (tarea.prioridad == "URGENTE")
                    android.graphics.Color.parseColor("#C62828")
                else
                    android.graphics.Color.parseColor("#6B8E23")
            )

            completaBtn.isEnabled = !tarea.completada
            completaBtn.text = if (tarea.completada) "✓ Completada" else "Marcar completa"

            completaBtn.setOnClickListener {
                completaBtn.isEnabled = false
                completaBtn.text = "Guardando..."
                Thread {
                    val success = ReminderSync.completeTask(this, tarea.tareaId)
                    runOnUiThread {
                        if (success) {
                            completaBtn.isEnabled = false
                            completaBtn.text = "✓ Completada"
                        } else {
                            completaBtn.isEnabled = true
                            completaBtn.text = "Error al guardar"
                        }
                    }
                }.start()
            }

            // Long click para editar tarea
            taskView.setOnLongClickListener {
                val intent = Intent(this, CreateEditTareaActivity::class.java).apply {
                    putExtra("tarea_id", tarea.tareaId)
                    putExtra("tarea_titulo", tarea.titulo)
                    putExtra("tarea_nota", tarea.nota)
                    putExtra("tarea_fechaLimite", tarea.fechaLimite)
                    putExtra("tarea_prioridad", tarea.prioridad)
                }
                startActivity(intent)
                true
            }

            container.addView(taskView)
        }
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
