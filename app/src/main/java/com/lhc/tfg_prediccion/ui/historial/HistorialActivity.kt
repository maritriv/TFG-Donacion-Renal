package com.lhc.tfg_prediccion.ui.historial

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
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

// -------------------- DATA CLASS DE FILTRO --------------------

data class HistorialFilter(
    val minEdad: Int? = null,
    val maxEdad: Int? = null,
    val sexo: String? = null,          // "H", "M" o null
    val minCapno: Int? = null,
    val maxCapno: Int? = null,
    val causaCardiaca: Boolean? = null,
    val cardioManual: Boolean? = null,
    val recPulso: Boolean? = null,
    val valido: Boolean? = null
)

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

        // Botón "Ordenar"
        binding.btnSort.setOnClickListener {
            showSortDialog()
        }

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

                // Pintamos inicial sin filtro
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
    // ORDENAR
    // -------------------------------------------------------------
    private fun showSortDialog() {
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

        val base = getFilteredList()

        val listaOrdenada = when (orden) {
            "Edad" -> base.sortedBy { it.edad?.toIntOrNull() ?: 0 }
            "Capnometría" -> base.sortedBy { it.capnometria?.toIntOrNull() ?: 0 }
            "Momento" -> base.sortedBy {
                val mode = it.prediction_mode
                    ?: modeFromLabelLoose(it.momento_prediccion_legible ?: "")
                when (mode) {
                    MODE_BEFORE -> 0   // Antes de la RCP
                    MODE_MID    -> 1   // A los 20 minutos
                    MODE_AFTER  -> 2   // Después
                    else        -> 3
                }
            }
            else -> base
        }

        renderPredictions(listaOrdenada)
    }

    // -------------------------------------------------------------
    // FILTRAR
    // -------------------------------------------------------------
    private fun showFilterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_filter_historial, null)

        // Edad
        val etEdadMin = view.findViewById<EditText>(R.id.etEdadMin)
        val etEdadMax = view.findViewById<EditText>(R.id.etEdadMax)

        // Sexo
        val spSexo = view.findViewById<Spinner>(R.id.spSexo)
        val sexoOpciones = arrayOf("Cualquiera", "Hombre", "Mujer")
        spSexo.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sexoOpciones
        )

        // Capnometría
        val etCapnoMin = view.findViewById<EditText>(R.id.etCapnoMin)
        val etCapnoMax = view.findViewById<EditText>(R.id.etCapnoMax)

        // Spinners Sí/No/Cualquiera
        val siNoOpciones = arrayOf("Cualquiera", "Sí", "No")
        val validoNoValido = arrayOf("Cualquiera", "Válido", "No válido")

        val spCausaCard = view.findViewById<Spinner>(R.id.spCausaCardiaca)
        spCausaCard.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            siNoOpciones
        )

        val spCardioManual = view.findViewById<Spinner>(R.id.spCardioManual)
        spCardioManual.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            siNoOpciones
        )

        val spRecPulso = view.findViewById<Spinner>(R.id.spRecPulso)
        spRecPulso.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            siNoOpciones
        )

        val spValido = view.findViewById<Spinner>(R.id.spValido)
        spValido.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            validoNoValido
        )

        // Rellenar con currentFilter si ya hubiera uno
        currentFilter?.let { f ->
            etEdadMin.setText(f.minEdad?.toString() ?: "")
            etEdadMax.setText(f.maxEdad?.toString() ?: "")

            when (f.sexo) {
                "H" -> spSexo.setSelection(1)
                "M" -> spSexo.setSelection(2)
                else -> spSexo.setSelection(0)
            }

            etCapnoMin.setText(f.minCapno?.toString() ?: "")
            etCapnoMax.setText(f.maxCapno?.toString() ?: "")

            fun Spinner.setFromBool(b: Boolean?) {
                setSelection(
                    when (b) {
                        null -> 0
                        true -> 1
                        false -> 2
                    }
                )
            }

            spCausaCard.setFromBool(f.causaCardiaca)
            spCardioManual.setFromBool(f.cardioManual)
            spRecPulso.setFromBool(f.recPulso)
            spValido.setFromBool(f.valido)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Filtrar predicciones")
            .setView(view)
            .setPositiveButton("Aplicar") { _, _ ->
                fun EditText.toIntOrNullTrimmed(): Int? {
                    val txt = text.toString().trim()
                    return if (txt.isEmpty()) null else txt.toIntOrNull()
                }

                val minEdad = etEdadMin.toIntOrNullTrimmed()
                val maxEdad = etEdadMax.toIntOrNullTrimmed()

                val sexo = when (spSexo.selectedItem.toString()) {
                    "Hombre" -> "H"
                    "Mujer" -> "M"
                    else -> null
                }

                val minCapno = etCapnoMin.toIntOrNullTrimmed()
                val maxCapno = etCapnoMax.toIntOrNullTrimmed()

                fun spinnerToBool(sp: Spinner): Boolean? = when (sp.selectedItem.toString()) {
                    "Sí" -> true
                    "No" -> false
                    else -> null
                }

                fun spinnerValidoToBool(sp: Spinner): Boolean? = when (sp.selectedItem.toString()) {
                    "Válido" -> true
                    "No Válido", "No válido" -> false
                    else -> null
                }

                val filter = HistorialFilter(
                    minEdad = minEdad,
                    maxEdad = maxEdad,
                    sexo = sexo,
                    minCapno = minCapno,
                    maxCapno = maxCapno,
                    causaCardiaca = spinnerToBool(spCausaCard),
                    cardioManual = spinnerToBool(spCardioManual),
                    recPulso = spinnerToBool(spRecPulso),
                    valido = spinnerValidoToBool(spValido)
                )

                currentFilter = filter
                renderPredictions(getFilteredList())
            }
            .setNeutralButton("Limpiar") { _, _ ->
                currentFilter = null
                renderPredictions(predictions)
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

    // -------------------------------------------------------------
    // Utilidades de mapeo / filtrado
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

    private fun stringSiNoToBool(value: String?): Boolean? {
        val v = value?.trim()?.lowercase(Locale.getDefault()) ?: return null
        return when (v) {
            "si", "sí", "true", "1" -> true
            "no", "false", "0" -> false
            else -> null
        }
    }

    // Comprueba si UNA predicción cumple el filtro
    private fun Prediccion.matchesFilter(f: HistorialFilter): Boolean {
        // Edad
        val edadInt = this.edad?.toIntOrNull()
        if (f.minEdad != null && (edadInt == null || edadInt < f.minEdad)) return false
        if (f.maxEdad != null && (edadInt == null || edadInt > f.maxEdad)) return false

        // Sexo
        if (f.sexo != null) {
            val sexoCanonico = mapSexo(this.femenino)
            if (sexoCanonico != f.sexo) return false
        }

        // Capnometría
        val capnoInt = this.capnometria?.toIntOrNull()
        if (f.minCapno != null && (capnoInt == null || capnoInt < f.minCapno)) return false
        if (f.maxCapno != null && (capnoInt == null || capnoInt > f.maxCapno)) return false

        fun matchesBoolField(filterVal: Boolean?, fieldStr: String?): Boolean {
            if (filterVal == null) return true
            val v = stringSiNoToBool(fieldStr)
            return v != null && v == filterVal
        }

        if (!matchesBoolField(f.causaCardiaca, this.causa_cardiaca)) return false
        if (!matchesBoolField(f.cardioManual, this.cardio_manual)) return false
        if (!matchesBoolField(f.recPulso, this.rec_pulso)) return false
        if (!matchesBoolField(f.valido, this.valido)) return false

        return true
    }
}