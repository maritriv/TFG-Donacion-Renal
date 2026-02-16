package com.lhc.tfg_prediccion.ui.historial

import android.os.Bundle
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.data.model.Prediccion
import com.lhc.tfg_prediccion.databinding.ActivityHistorialBinding
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

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val predictions = mutableListOf<Prediccion>()
    private var currentFilter: HistorialFilter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
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
            val doctorName = auth.currentUser?.displayName
                ?: auth.currentUser?.email
                ?: "Médico actual"

            PredictionCsvExporter.exportCsv(
                activity = this,
                predictions = getFilteredList(),
                doctorName = doctorName,
                userUid = auth.currentUser?.uid
            )
        }
    }

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
                renderPredictions(getFilteredList())
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Error al cargar las predicciones", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFilteredList(): List<Prediccion> {
        val filter = currentFilter ?: return predictions.toList()
        return predictions.filter { it.matchesFilter(filter) }
    }

    private fun renderPredictions(list: List<Prediccion>) {
        val table = binding.tablePredictions

        // Mantener cabecera (fila 0)
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

        fun TableRow.addCell(text: String, bold: Boolean = false) {
            TextView(this@HistorialActivity).apply {
                this.text = text
                textSize = 12f
                setPadding(16, 24, 16, 24)
                typeface = android.graphics.Typeface.create(
                    "sans-serif-condensed",
                    if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
                layoutParams = params
                this@addCell.addView(this)
            }
        }

        fun dashIfBlank(s: String?): String = if (s.isNullOrBlank()) "—" else s

        fun formatIndice(d: Double?): String =
            if (d == null) "—" else String.format(Locale.US, "%.3f", d)

        list.forEach { pred ->
            val fila = TableRow(this)

            // Modo canónico
            val mode = pred.prediction_mode
                ?: modeFromLabelLoose(pred.momento_prediccion_legible ?: "")
            val canonicalMoment = modeToLabel(mode)
            val colesterol = dashIfBlank(pred.colesterol)
            val adrenalinaN = dashIfBlank(pred.adrenalina_n)
            val imc = dashIfBlank(pred.imc)
            val indiceStr = formatIndice(pred.indice)

            // ---- columnas ----
            fila.addCell(contador.toString())

            fila.addCell(dashIfBlank(pred.edad))
            fila.addCell(mapSexo(pred.femenino))
            fila.addCell(dashIfBlank(pred.capnometria))

            // Nuevas
            fila.addCell(colesterol)
            fila.addCell(adrenalinaN)
            fila.addCell(imc)

            // Clásicas
            fila.addCell(dashIfBlank(pred.causa_cardiaca))
            fila.addCell(dashIfBlank(pred.cardio_manual))
            fila.addCell(dashIfBlank(pred.rec_pulso))

            fila.addCell(canonicalMoment)
            fila.addCell(mapResultado(pred.valido))

            // Nueva: índice
            fila.addCell(indiceStr)

            // Informe PDF
            TextView(this).apply {
                text = "PDF"
                textSize = 12f
                setPadding(16, 24, 16, 24)
                layoutParams = params
                setTextColor(ContextCompat.getColor(this@HistorialActivity, R.color.dark_blue))

                setOnClickListener {
                    val doctorName = auth.currentUser?.displayName
                        ?: auth.currentUser?.email
                        ?: "Médico actual"

                    generatePredictionPdf(
                        this@HistorialActivity,
                        PdfPrediction(
                            doctorName = doctorName,
                            fecha = pred.fecha,
                            predictionMode = pred.prediction_mode,
                            momentoCanonico = canonicalMoment,
                            edad = pred.edad ?: "",
                            femenino = pred.femenino ?: "",
                            capnometria = pred.capnometria ?: "",
                            causaCardiaca = pred.causa_cardiaca ?: "",
                            cardioManual = pred.cardio_manual ?: "",
                            recPulso = pred.rec_pulso ?: "",
                            valido = pred.valido.equals("Si", ignoreCase = true),
                            indice = pred.indice,
                            colesterol = pred.colesterol,
                            adrenalinaN = pred.adrenalina_n,
                            imc = pred.imc
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