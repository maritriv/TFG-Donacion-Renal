package com.lhc.tfg_prediccion.ui.historial

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.data.model.Prediccion
import com.lhc.tfg_prediccion.databinding.ActivityHistorialBinding
import com.lhc.tfg_prediccion.ui.prediction.MODE_AFTER
import com.lhc.tfg_prediccion.ui.prediction.MODE_BEFORE
import com.lhc.tfg_prediccion.ui.prediction.MODE_MID
import com.lhc.tfg_prediccion.ui.prediction.modeFromLabelLoose
import com.lhc.tfg_prediccion.ui.prediction.modeToLabel
import com.lhc.tfg_prediccion.util.PdfPrediction
import com.lhc.tfg_prediccion.util.PredictionCsvExporter
import com.lhc.tfg_prediccion.util.generatePredictionPdf
import java.util.Locale

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Lista en memoria para poder ordenar sin volver a Firestore
    private val predictions = mutableListOf<Prediccion>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Título barra
        binding.tvProfileTitle.text = getString(R.string.historial_predicciones)

        // Navegación
        binding.backRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }

        // Cargar SOLO las predicciones del médico logueado
        loadPredictions()

        // Botón "Filtrar"
        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }

        // Botón "Exportar"
        binding.btnExport.setOnClickListener {
            val doctorName = auth.currentUser?.displayName
                ?: auth.currentUser?.email
                ?: "Médico actual"

            PredictionCsvExporter.exportCsv(
                activity = this,
                predictions = predictions,
                doctorName = doctorName,
                userUid = auth.currentUser?.uid
            )
        }
    }

    // -------------------------------------------------------------
    // Cargar predicciones del médico actual
    // -------------------------------------------------------------
    private fun loadPredictions() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("predicciones")
            .whereEqualTo("uid_medico", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents

                predictions.clear()
                predictions.addAll(docs.mapNotNull { it.toObject(Prediccion::class.java) })

                // Pintamos inicial sin filtro
                renderPredictions(predictions)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Error al cargar las predicciones", Toast.LENGTH_SHORT).show()
            }
    }

    // -------------------------------------------------------------
    // Pintar tabla
    // -------------------------------------------------------------
    private fun renderPredictions(list: List<Prediccion>) {
        val table = binding.tablePredictions

        // Eliminar filas de datos, mantener cabecera (fila 0)
        if (table.childCount > 1) {
            table.removeViews(1, table.childCount - 1)
        }

        binding.tvShowingRows.text = "Mostrando ${list.size} filas"

        if (list.isEmpty()) {
            binding.tvNoPredictions.visibility = View.VISIBLE
            return
        } else {
            binding.tvNoPredictions.visibility = View.GONE
        }

        var contador = 1
        val params = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 16, 8, 16)
        }

        fun TableRow.addCell(text: String, bold: Boolean = false) {
            TextView(this@HistorialActivity).apply {
                this.text = text
                textSize = 12f
                setPadding(16, 24, 16, 24)
                typeface = android.graphics.Typeface.create(
                    "sans-serif-condensed",
                    if (bold) android.graphics.Typeface.BOLD
                    else android.graphics.Typeface.NORMAL
                )
                layoutParams = params
                this@addCell.addView(this)
            }
        }

        list.forEach { pred ->
            val fila = TableRow(this)

            // #
            fila.addCell(contador.toString())

            // Edad
            fila.addCell(pred.edad ?: "")

            // Sexo
            fila.addCell(mapSexo(pred.femenino))

            // Capnometría
            fila.addCell(pred.capnometria ?: "")

            // Causa cardiaca
            fila.addCell(pred.causa_cardiaca ?: "")

            // Cardiocompresión
            fila.addCell(pred.cardio_manual ?: "")

            // Rec. del pulso
            fila.addCell(pred.rec_pulso ?: "")

            // Momento — frase canónica
            val mode = pred.prediction_mode
                ?: modeFromLabelLoose(pred.momento_prediccion_legible ?: "")
            val canonicalMoment = modeToLabel(mode)
            fila.addCell(canonicalMoment)

            // Resultado
            fila.addCell(mapResultado(pred.valido))

            // Informe (PDF)
            TextView(this).apply {
                text = "PDF"
                textSize = 12f
                setPadding(16, 24, 16, 24)
                layoutParams = params
                setTextColor(
                    ContextCompat.getColor(
                        this@HistorialActivity,
                        R.color.dark_blue
                    )
                )

                setOnClickListener {
                    val doctorName = auth.currentUser?.displayName
                        ?: auth.currentUser?.email
                        ?: "Médico actual"

                    generatePredictionPdf(
                        this@HistorialActivity,
                        PdfPrediction(
                            doctorName = doctorName,
                            fecha = pred.fecha,
                            momentoCanonico = canonicalMoment,
                            edad = pred.edad ?: "",
                            femenino = pred.femenino ?: "",
                            capnometria = pred.capnometria ?: "",
                            causaCardiaca = pred.causa_cardiaca ?: "",
                            cardioManual = pred.cardio_manual ?: "",
                            recPulso = pred.rec_pulso ?: "",
                            valido = pred.valido.equals("Si", ignoreCase = true),
                            indice = pred.indice
                        )
                    )
                }

                fila.addView(this)
            }

            table.addView(fila)
            contador++
        }
    }

    // -------------------------------------------------------------
    // Filtro / Ordenar
    // -------------------------------------------------------------
    private fun showFilterDialog() {
        val opciones = arrayOf("Edad", "Capnometría", "Momento")

        val spinner = AppCompatSpinner(this).apply {
            val adapter = ArrayAdapter(
                this@HistorialActivity,
                android.R.layout.simple_spinner_dropdown_item,
                opciones
            )
            this.adapter = adapter

            background = ContextCompat.getDrawable(
                this@HistorialActivity,
                R.drawable.bg_input
            )

            setPopupBackgroundDrawable(
                ContextCompat.getDrawable(
                    this@HistorialActivity,
                    R.drawable.bg_panel_full_rounded
                )
            )

            setPadding(32, 16, 32, 16)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            gravity = Gravity.CENTER_HORIZONTAL
            addView(spinner)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Ordenar predicciones por")
            .setView(container)
            .setPositiveButton("Aplicar") { _, _ ->
                val seleccion = spinner.selectedItem.toString()
                applySort(seleccion)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.bg_panel_full_rounded
                )
            )
        }

        dialog.show()
    }

    private fun applySort(orden: String) {
        if (predictions.isEmpty()) return

        val listaOrdenada = when (orden) {
            "Edad" -> predictions.sortedBy { it.edad.toIntOrNull() ?: 0 }
            "Capnometría" -> predictions.sortedBy { it.capnometria.toIntOrNull() ?: 0 }
            "Momento" -> predictions.sortedBy {
                val mode = it.prediction_mode
                    ?: modeFromLabelLoose(it.momento_prediccion_legible ?: "")
                when (mode) {
                    MODE_BEFORE -> 0   // Antes de la RCP
                    MODE_MID    -> 1   // A los 20 minutos
                    MODE_AFTER  -> 2   // Después
                    else        -> 3
                }
            }
            else -> predictions
        }

        renderPredictions(listaOrdenada)
    }

    // -------------------------------------------------------------
    // Utilidades de mapeo
    // -------------------------------------------------------------
    private fun mapSexo(femenino: String?): String {
        val value = femenino?.trim()?.lowercase(Locale.getDefault()) ?: return ""
        return when (value) {
            "si", "1", "true", "f", "femenino", "mujer" -> "M"
            "no", "0", "false", "masculino", "hombre" -> "H"
            else -> femenino ?: ""
        }
    }

    private fun mapResultado(valido: String?): String {
        val value = valido?.trim()?.lowercase(Locale.getDefault()) ?: return ""
        return when (value) {
            "si", "1", "true" -> "Válido"
            "no", "0", "false" -> "No válido"
            else -> valido ?: ""
        }
    }
}