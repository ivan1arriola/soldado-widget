package com.ivan1arriola.soldadowidget

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateEditTareaActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var etTitulo: EditText
    private lateinit var etNota: EditText
    private lateinit var btnFechaLimite: Button
    private lateinit var rgPrioridad: RadioGroup
    private lateinit var rbNormal: RadioButton
    private lateinit var rbUrgente: RadioButton
    private lateinit var btnGuardar: Button
    private lateinit var btnCancelar: Button

    private var selectedFecha: String? = null
    private var tareaIdToEdit: String? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_edit_tarea)

        tvTitle = findViewById(R.id.tvTitle)
        etTitulo = findViewById(R.id.etTitulo)
        etNota = findViewById(R.id.etNota)
        btnFechaLimite = findViewById(R.id.btnFechaLimite)
        rgPrioridad = findViewById(R.id.rgPrioridad)
        rbNormal = findViewById(R.id.rbNormal)
        rbUrgente = findViewById(R.id.rbUrgente)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnCancelar = findViewById(R.id.btnCancelar)

        // Extraer tarea ID si estamos editando
        tareaIdToEdit = intent.getStringExtra("tarea_id")

        if (tareaIdToEdit != null) {
            tvTitle.text = "Editar Recordatorio"
            btnGuardar.text = "Guardar Cambios"
            cargarTareaParaEditar()
        } else {
            tvTitle.text = "Crear Recordatorio"
            btnGuardar.text = "Crear"
        }

        btnFechaLimite.setOnClickListener { mostrarDatePicker() }
        btnGuardar.setOnClickListener { guardarTarea() }
        btnCancelar.setOnClickListener { finish() }
    }

    private fun cargarTareaParaEditar() {
        val tareaId = tareaIdToEdit ?: return
        val titulo = intent.getStringExtra("tarea_titulo") ?: ""
        val nota = intent.getStringExtra("tarea_nota") ?: ""
        val fechaLimite = intent.getStringExtra("tarea_fechaLimite")
        val prioridad = intent.getStringExtra("tarea_prioridad") ?: "NORMAL"

        etTitulo.setText(titulo)
        etNota.setText(nota)

        if (fechaLimite != null && fechaLimite.isNotEmpty()) {
            selectedFecha = fechaLimite
            btnFechaLimite.text = displayDateFormat.format(dateFormat.parse(fechaLimite) ?: Calendar.getInstance().time)
        }

        if (prioridad == "URGENTE") {
            rbUrgente.isChecked = true
        } else {
            rbNormal.isChecked = true
        }
    }

    private fun mostrarDatePicker() {
        val calendar = Calendar.getInstance()

        if (selectedFecha != null) {
            try {
                calendar.time = dateFormat.parse(selectedFecha!!) ?: Calendar.getInstance().time
            } catch (_: Exception) {}
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val picker = DatePickerDialog(this, { _, y, m, d ->
            calendar.set(y, m, d)
            selectedFecha = dateFormat.format(calendar.time)
            btnFechaLimite.text = displayDateFormat.format(calendar.time)
        }, year, month, day)

        picker.show()
    }

    private fun guardarTarea() {
        val titulo = etTitulo.text.toString().trim()
        if (titulo.isEmpty()) {
            Toast.makeText(this, "El titulo es requerido", Toast.LENGTH_SHORT).show()
            return
        }

        val nota = etNota.text.toString().trim()
        val prioridad = if (rbUrgente.isChecked) "URGENTE" else "NORMAL"

        etTitulo.isEnabled = false
        etNota.isEnabled = false
        btnFechaLimite.isEnabled = false
        rgPrioridad.children.forEach { (it as? RadioButton)?.isEnabled = false }
        btnGuardar.isEnabled = false
        btnCancelar.isEnabled = false

        Thread {
            val result = if (tareaIdToEdit != null) {
                ReminderSync.updateTask(
                    this,
                    tareaIdToEdit!!,
                    titulo = titulo,
                    nota = nota.ifEmpty { null },
                    fechaLimite = selectedFecha,
                    prioridad = prioridad
                )
            } else {
                ReminderSync.createTask(
                    this,
                    titulo,
                    nota,
                    selectedFecha,
                    prioridad
                )
            }

            runOnUiThread {
                if (result != null) {
                    Toast.makeText(
                        this@CreateEditTareaActivity,
                        if (tareaIdToEdit != null) "Recordatorio actualizado" else "Recordatorio creado",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(
                        this@CreateEditTareaActivity,
                        if (tareaIdToEdit != null) "Error al actualizar" else "Error al crear",
                        Toast.LENGTH_SHORT
                    ).show()
                    etTitulo.isEnabled = true
                    etNota.isEnabled = true
                    btnFechaLimite.isEnabled = true
                    rgPrioridad.children.forEach { (it as? RadioButton)?.isEnabled = true }
                    btnGuardar.isEnabled = true
                    btnCancelar.isEnabled = true
                }
            }
        }.start()
    }
}
