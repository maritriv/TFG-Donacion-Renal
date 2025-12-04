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
import com.lhc.tfg_prediccion.util.PdfPrediction
import com.lhc.tfg_prediccion.util.PredictionCsvExporter
import com.lhc.tfg_prediccion.util.generatePredictionPdf
import com.lhc.tfg_prediccion.ui.util.HistorialFilter
import com.lhc.tfg_prediccion.ui.util.mapResultado
import com.lhc.tfg_prediccion.ui.util.mapSexo
import com.lhc.tfg_prediccion.ui.util.matchesFilter
import com.lhc.tfg_prediccion.ui.util.showFilterDialog
import com.lhc.tfg_prediccion.ui.util.showSortDialog

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Lista en memoria para poder ordenar/filtrar sin volver a Firestore
    private val predictions = mutableListOf<Prediccion>()

    // Filtro actual (null = sin filtros)
    private var currentFilter: HistorialFilter? = null

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

        // Botón "Ordenar" usando util
        binding.btnSort.setOnClickListener {
            showSortDialog(
                activity = this,
                baseListProvider = { getFilteredList() }   // lo que está visible ahora
            ) { listaOrdenada ->
                renderPredictions(listaOrdenada)
            }
        }

        // Botón "Filtrar" usando util
        binding.btnFilter.setOnClickListener {
            showFilterDialog(
                activity = this,
                currentFilter = currentFilter
            ) { nuevoFiltro ->
                currentFilter = nuevoFiltro
                renderPredictions(getFilteredList())
            }
        }

        // Botón "Exportar"
        binding.btnExport.setOnClickListener {
            val doctorName = auth.currentUser?.displayName
                ?: auth.currentUser?.email
                ?: "Médico actual"

            PredictionCsvExporter.exportCsv(
                activity = this,
                predictions = getFilteredList(), // exportamos lo que se ve
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

                // Pintamos inicial aplicando filtro actual (si lo hubiera)
                renderPredictions(getFilteredList())
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Error al cargar las predicciones", Toast.LENGTH_SHORT).show()
            }
    }

    // Devuelve la lista filtrada según currentFilter (o todas si es null)
    private fun getFilteredList(): List<Prediccion> {
        val filter = currentFilter ?: return predictions.toList()
        return predictions.filter { it.matchesFilter(filter) }
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

            // Sexo (util compartido)
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

            // Resultado (util compartido)
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
}