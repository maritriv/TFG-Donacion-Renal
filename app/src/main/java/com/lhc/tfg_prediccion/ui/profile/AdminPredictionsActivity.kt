package com.lhc.tfg_prediccion.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.data.model.Prediccion
import com.lhc.tfg_prediccion.databinding.ActivityAdminPredictionsBinding
import com.lhc.tfg_prediccion.ui.prediction.modeFromLabelLoose
import com.lhc.tfg_prediccion.ui.prediction.modeToLabel
import com.lhc.tfg_prediccion.ui.util.HistorialFilter
import com.lhc.tfg_prediccion.ui.util.mapResultado
import com.lhc.tfg_prediccion.ui.util.mapSexo
import com.lhc.tfg_prediccion.ui.util.matchesFilter
import com.lhc.tfg_prediccion.ui.util.showFilterDialog
import com.lhc.tfg_prediccion.ui.util.showSortDialog
import com.lhc.tfg_prediccion.util.PdfPrediction
import com.lhc.tfg_prediccion.util.PredictionCsvExporter
import com.lhc.tfg_prediccion.util.generatePredictionPdf
import java.util.Locale

class AdminPredictionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPredictionsBinding
    private val db = FirebaseFirestore.getInstance()

    private val predictions = mutableListOf<Prediccion>()
    private var currentFilter: HistorialFilter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPredictionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvProfileTitle.text = getString(R.string.historial_predicciones)

        binding.backRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }

        loadPredictions()

        binding.btnSort.setOnClickListener {
            showSortDialog(
                activity = this,
                baseListProvider = { getFilteredList() }
            ) { listaOrdenada ->
                renderPredictions(listaOrdenada)
            }
        }

        binding.btnFilter.setOnClickListener {
            showFilterDialog(
                activity = this,
                currentFilter = currentFilter
            ) { nuevoFiltro ->
                currentFilter = nuevoFiltro
                renderPredictions(getFilteredList())
            }
        }

        binding.btnExport.setOnClickListener {
            PredictionCsvExporter.exportCsv(
                activity = this,
                predictions = getFilteredList(),
                doctorName = "Todos los médicos",
                userUid = null
            )
        }
    }

    private fun loadPredictions() {
        db.collection("predicciones")
            .get()
            .addOnSuccessListener { snapshot ->
                predictions.clear()
                predictions.addAll(
                    snapshot.documents.mapNotNull { it.toObject(Prediccion::class.java) }
                )
                renderPredictions(getFilteredList())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar las predicciones", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFilteredList(): List<Prediccion> {
        val filter = currentFilter ?: return predictions
        return predictions.filter { it.matchesFilter(filter) }
    }

    private fun dashIfBlank(s: String?): String =
        if (s.isNullOrBlank()) "—" else s

    private fun formatIndice(d: Double?): String =
        if (d == null) "—" else String.format(Locale.US, "%.3f", d)

    private fun renderPredictions(list: List<Prediccion>) {
        val table = binding.tablePredictions

        if (table.childCount > 1) {
            table.removeViews(1, table.childCount - 1)
        }

        binding.tvShowingRows.text = "Mostrando ${list.size} filas"

        if (list.isEmpty()) {
            binding.tvNoPredictions.visibility = View.VISIBLE
            binding.scrollPredictions.visibility = View.GONE
            return
        } else {
            binding.tvNoPredictions.visibility = View.GONE
            binding.scrollPredictions.visibility = View.VISIBLE
        }

        var contador = 1
        val params = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 16, 8, 16)
        }

        fun TableRow.addCell(text: String) {
            TextView(this@AdminPredictionsActivity).apply {
                this.text = text
                textSize = 12f
                setPadding(16, 24, 16, 24)
                layoutParams = params
                this@addCell.addView(this)
            }
        }

        list.forEach { pred ->
            val fila = TableRow(this)

            val mode = pred.prediction_mode
                ?: modeFromLabelLoose(pred.momento_prediccion_legible ?: "")
            val moment = modeToLabel(mode)
            val indiceStr = formatIndice(pred.indice)

            fila.addCell(contador.toString())
            fila.addCell(dashIfBlank(pred.edad))
            fila.addCell(mapSexo(pred.femenino))
            fila.addCell(dashIfBlank(pred.capnometria))
            fila.addCell(dashIfBlank(pred.causa_cardiaca))
            fila.addCell(dashIfBlank(pred.cardio_manual))
            fila.addCell(dashIfBlank(pred.rec_pulso))
            fila.addCell(moment)
            fila.addCell(mapResultado(pred.valido))
            fila.addCell(indiceStr)

            TextView(this).apply {
                text = "PDF"
                textSize = 12f
                setPadding(16, 24, 16, 24)
                layoutParams = params
                setTextColor(
                    ContextCompat.getColor(
                        this@AdminPredictionsActivity,
                        R.color.dark_blue
                    )
                )

                setOnClickListener {
                    generatePredictionPdf(
                        this@AdminPredictionsActivity,
                        PdfPrediction(
                            doctorName = "Desconocido",
                            fecha = pred.fecha,
                            predictionMode = pred.prediction_mode,
                            momentoCanonico = moment,
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
}