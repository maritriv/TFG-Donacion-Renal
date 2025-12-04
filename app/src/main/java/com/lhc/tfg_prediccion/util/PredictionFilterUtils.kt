package com.lhc.tfg_prediccion.ui.util

import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.data.model.Prediccion
import com.lhc.tfg_prediccion.ui.prediction.MODE_AFTER
import com.lhc.tfg_prediccion.ui.prediction.MODE_BEFORE
import com.lhc.tfg_prediccion.ui.prediction.MODE_MID
import com.lhc.tfg_prediccion.ui.prediction.modeFromLabelLoose
import java.util.Locale

// -------------------- DATA CLASS DE FILTRO COMPARTIDA --------------------
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

// -------------------- MAPEOS COMUNES --------------------

fun mapSexo(femenino: String?): String {
    val value = femenino?.trim()?.lowercase(Locale.getDefault()) ?: return ""
    return when (value) {
        "si", "1", "true", "f", "femenino", "mujer" -> "M" // Mujer
        "no", "0", "false", "masculino", "hombre" -> "H"  // Hombre
        else -> femenino ?: ""
    }
}

fun mapResultado(valido: String?): String {
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

// -------------------- FILTRADO --------------------

fun Prediccion.matchesFilter(f: HistorialFilter): Boolean {
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

// -------------------- ORDENACIÓN --------------------

fun sortPredictions(base: List<Prediccion>, orden: String): List<Prediccion> {
    return when (orden) {
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
}

// -------------------- DIÁLOGO ORDENAR COMPARTIDO --------------------

fun showSortDialog(
    activity: AppCompatActivity,
    baseListProvider: () -> List<Prediccion>,
    onResult: (List<Prediccion>) -> Unit
) {
    val opciones = arrayOf("Edad", "Capnometría", "Momento")

    val spinner = AppCompatSpinner(activity).apply {
        val adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            opciones
        )
        this.adapter = adapter

        background = ContextCompat.getDrawable(
            activity,
            R.drawable.bg_input
        )

        setPopupBackgroundDrawable(
            ContextCompat.getDrawable(
                activity,
                R.drawable.bg_panel_full_rounded
            )
        )

        setPadding(32, 16, 32, 16)
    }

    val container = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 32, 48, 16)
        gravity = Gravity.CENTER_HORIZONTAL
        addView(spinner)
    }

    val dialog = AlertDialog.Builder(activity)
        .setTitle("Ordenar predicciones por")
        .setView(container)
        .setPositiveButton("Aplicar") { _, _ ->
            val seleccion = spinner.selectedItem.toString()
            val baseList = baseListProvider()
            val listaOrdenada = sortPredictions(baseList, seleccion)
            onResult(listaOrdenada)
        }
        .setNegativeButton("Cancelar", null)
        .create()

    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(
                activity,
                R.drawable.bg_panel_full_rounded
            )
        )
    }

    dialog.show()
}

// -------------------- DIÁLOGO FILTRAR COMPARTIDO --------------------

fun showFilterDialog(
    activity: AppCompatActivity,
    currentFilter: HistorialFilter?,
    onFilterChanged: (HistorialFilter?) -> Unit
) {
    val view = activity.layoutInflater.inflate(R.layout.dialog_filter_historial, null)

    // Edad
    val etEdadMin = view.findViewById<EditText>(R.id.etEdadMin)
    val etEdadMax = view.findViewById<EditText>(R.id.etEdadMax)

    // Sexo
    val spSexo = view.findViewById<Spinner>(R.id.spSexo)
    val sexoOpciones = arrayOf("Cualquiera", "Hombre", "Mujer")
    spSexo.adapter = ArrayAdapter(
        activity,
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
        activity,
        android.R.layout.simple_spinner_dropdown_item,
        siNoOpciones
    )

    val spCardioManual = view.findViewById<Spinner>(R.id.spCardioManual)
    spCardioManual.adapter = ArrayAdapter(
        activity,
        android.R.layout.simple_spinner_dropdown_item,
        siNoOpciones
    )

    val spRecPulso = view.findViewById<Spinner>(R.id.spRecPulso)
    spRecPulso.adapter = ArrayAdapter(
        activity,
        android.R.layout.simple_spinner_dropdown_item,
        siNoOpciones
    )

    val spValido = view.findViewById<Spinner>(R.id.spValido)
    spValido.adapter = ArrayAdapter(
        activity,
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

    val dialog = AlertDialog.Builder(activity)
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

            onFilterChanged(filter)
        }
        .setNeutralButton("Limpiar") { _, _ ->
            onFilterChanged(null)
        }
        .setNegativeButton("Cancelar", null)
        .create()

    dialog.setOnShowListener {
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(
                activity,
                R.drawable.bg_panel_full_rounded
            )
        )
    }

    dialog.show()
}