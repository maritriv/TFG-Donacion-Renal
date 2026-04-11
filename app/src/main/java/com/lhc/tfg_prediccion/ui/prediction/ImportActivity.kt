package com.lhc.tfg_prediccion.ui.prediction

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityImportBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity
import com.lhc.tfg_prediccion.util.PrediccionDedup
import java.text.Normalizer
import java.util.Locale

class ImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding
    private var userUid: String? = null
    private var name: String? = null

    private val rowsReady = mutableListOf<RowParsed>()
    private var currentCsvUri: Uri? = null

    private data class RowParsed(
        val edad: Int,
        val capno: Int,
        val fem: Int,      // 0/1
        val cardio: Int,   // 0/1 -> Manual=1, Mecánica=0
        val rec: Int,      // 0/1
        val causa: Int,    // 0/1
        val mode: String   // MODE_MID / MODE_AFTER
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userUid = intent.getStringExtra("userUid")
        name = intent.getStringExtra("userName")

        resetUI()

        binding.filePickerArea.setOnClickListener { seleccionarCsv() }

        binding.btnClearFile.setOnClickListener {
            resetUI()
            toast("Archivo deseleccionado")
        }

        binding.btnHelp.setOnClickListener { mostrarAyudaCsv() }

        binding.btnVolver.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("userUid", userUid)
                putExtra("userName", name)
            })
        }

        binding.btnAddPredictions.setOnClickListener {
            if (rowsReady.isEmpty()) {
                toast("No hay datos válidos. Selecciona un CSV válido.")
                return@setOnClickListener
            }
            importarFilasEnFirestore(rowsReady)
        }
    }

    private fun seleccionarCsv() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePicker.launch(intent)
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val uri = result.data?.data ?: return@registerForActivityResult
        currentCsvUri = uri

        val docFile = DocumentFile.fromSingleUri(this, uri)
        binding.tvFileName.text = docFile?.name ?: "Archivo CSV seleccionado"
        binding.btnClearFile.visibility = View.VISIBLE

        validarYParsearCSV(uri)
    }

    private fun validarYParsearCSV(uri: Uri) {
        try {
            rowsReady.clear()

            val allLines = contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readLines()
                ?: run {
                    toast("CSV inválido: no se pudo abrir el archivo.")
                    return
                }

            if (allLines.isEmpty()) {
                toast("CSV inválido: archivo vacío.")
                return
            }

            val sep = if (allLines.first().contains(",")) "," else ";"
            val header = allLines.first().split(sep).map { it.trim() }
            val dataLines = allLines.drop(1)

            val requiredCols = listOf(
                "Edad",
                "Femenino",
                "Capnometria",
                "Causa_cardiaca",
                "Cardio_manual",
                "Recuperacion_pulso",
                "Prediction_mode"
            )

            fun idx(name: String) = header.indexOfFirst { it.equals(name, ignoreCase = true) }

            val missing = requiredCols.filter { idx(it) == -1 }
            if (missing.isNotEmpty()) {
                toast("CSV inválido: faltan columnas: ${missing.joinToString(", ")}")
                return
            }

            val idxEdad = idx("Edad")
            val idxFem = idx("Femenino")
            val idxCap = idx("Capnometria")
            val idxCausa = idx("Causa_cardiaca")
            val idxCard = idx("Cardio_manual")
            val idxRec = idx("Recuperacion_pulso")
            val idxMode = idx("Prediction_mode")

            fun col(cols: List<String>, i: Int): String =
                cols.getOrNull(i)?.trim().orEmpty()

            fun to01(text: String): Int? {
                val t = normalize(text)
                return when (t) {
                    "1", "si", "true" -> 1
                    "0", "no", "false" -> 0
                    else -> text.trim().toIntOrNull()
                }
            }

            fun femeninoTo01(text: String): Int? {
                val t = normalize(text)
                return when (t) {
                    "si", "1", "true", "mujer", "femenino" -> 1
                    "no", "0", "false", "hombre", "masculino" -> 0
                    else -> text.trim().toIntOrNull()
                }
            }

            fun cardioTo01(text: String): Int? {
                val t = normalize(text)
                return when (t) {
                    "manual", "1", "si", "true" -> 1
                    "mecanica", "0", "no", "false" -> 0
                    else -> text.trim().toIntOrNull()
                }
            }

            fun parseMode(text: String): String {
                val t = text.trim().uppercase(Locale.getDefault())
                return when (t) {
                    MODE_MID, MODE_AFTER -> t
                    else -> {
                        when (modeFromLabelLoose(text)) {
                            MODE_MID -> MODE_MID
                            else -> MODE_AFTER
                        }
                    }
                }
            }

            var pocasCols = false
            var valoresInvalidos = false

            dataLines.forEach { raw ->
                if (raw.isBlank()) return@forEach

                val cols = raw.split(sep).map { it.trim() }

                if (cols.size < header.size) {
                    pocasCols = true
                    return@forEach
                }

                val edad = col(cols, idxEdad).toIntOrNull()
                val capno = col(cols, idxCap).toIntOrNull()
                val fem = femeninoTo01(col(cols, idxFem))
                val cardio = cardioTo01(col(cols, idxCard))
                val rec = to01(col(cols, idxRec))
                val causa = to01(col(cols, idxCausa))
                val mode = parseMode(col(cols, idxMode))

                if (edad == null || capno == null || fem == null || cardio == null || rec == null || causa == null) {
                    valoresInvalidos = true
                    return@forEach
                }

                if (mode != MODE_MID && mode != MODE_AFTER) {
                    valoresInvalidos = true
                    return@forEach
                }

                rowsReady += RowParsed(
                    edad = edad,
                    capno = capno,
                    fem = fem,
                    cardio = cardio,
                    rec = rec,
                    causa = causa,
                    mode = mode
                )
            }

            when {
                rowsReady.isEmpty() && pocasCols ->
                    toast("CSV inválido: columnas insuficientes. Revisa el formato.")
                rowsReady.isEmpty() && valoresInvalidos ->
                    toast("CSV inválido: valores no válidos.")
                rowsReady.isEmpty() ->
                    toast("CSV inválido: error desconocido. Selecciona otro archivo.")
                else -> {
                    binding.btnAddPredictions.visibility = View.VISIBLE
                    binding.btnAddPredictions.isEnabled = true
                    binding.btnAddPredictions.text = "AÑADIR ${rowsReady.size} FILAS"
                    toast("CSV válido")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            toast("CSV inválido: error al leer el archivo.")
        }
    }

    private fun importarFilasEnFirestore(rows: List<RowParsed>) {
        if (rows.isEmpty()) {
            toast("No hay filas para importar")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val uid = userUid ?: run {
            toast("Usuario desconocido")
            return
        }

        startImportUI()

        db.collection("predicciones")
            .whereEqualTo("uid_medico", uid)
            .get()
            .addOnSuccessListener { snapshot ->

                val existingKeys = HashSet<String>(snapshot.size())

                snapshot.documents.forEach { d ->
                    val data = d.data ?: return@forEach

                    val key = buildDupKey(
                        uidMedico = data["uid_medico"] as? String,
                        mode = data["prediction_mode"] as? String ?: "",
                        edad = data["edad"] as? String ?: "",
                        femenino = data["femenino"] as? String ?: "",
                        capno = data["capnometria"] as? String ?: "",
                        causa = data["causa_cardiaca"] as? String ?: "",
                        cardio = data["cardio_manual"] as? String ?: "",
                        rec = data["rec_pulso"] as? String ?: "",
                        valido = data["valido"] as? String ?: ""
                    )
                    existingKeys.add(key)
                }

                val computed = rows.map { r ->
                    val femTxt = sexoLabel(r.fem)
                    val cardioTxt = cardioLabel(r.cardio)
                    val recTxt = yesNoLabel(r.rec)
                    val causaTxt = yesNoLabel(r.causa)

                    val (valido, indice) = when (r.mode) {
                        MODE_MID -> calcularIndiceYValidez(
                            mode = MODE_MID,
                            mitad = InputsMitad(
                                edad = r.edad,
                                femenino = r.fem,
                                capnometria = r.capno,
                                causaCardiaca = r.causa,
                                cardioManual = r.cardio,
                                recuperacion = r.rec
                            )
                        )
                        else -> calcularIndiceYValidez(
                            mode = MODE_AFTER,
                            after = InputsAfter(
                                edad = r.edad,
                                femenino = r.fem,
                                capnometria = r.capno,
                                causaCardiaca = r.causa,
                                cardioManual = r.cardio,
                                recuperacion = r.rec,
                                tiempoMin = 0
                            )
                        )
                    }

                    val validoTxt = if (valido) "Si" else "No"

                    val docId = PrediccionDedup.docId(
                        predictionMode = r.mode,
                        edad = r.edad.toString(),
                        femenino = femTxt,
                        capnometria = r.capno.toString(),
                        causaCardiaca = causaTxt,
                        cardioManual = cardioTxt,
                        recPulso = recTxt,
                        valido = validoTxt
                    )

                    val dupKey = buildDupKey(
                        uidMedico = uid,
                        mode = r.mode,
                        edad = r.edad.toString(),
                        femenino = femTxt,
                        capno = r.capno.toString(),
                        causa = causaTxt,
                        cardio = cardioTxt,
                        rec = recTxt,
                        valido = validoTxt
                    )

                    Triple(r, docId, Triple(valido, indice, dupKey))
                }

                val groupedCsv = computed.groupBy { it.third.third }
                val uniqueCsv = mutableListOf<Triple<RowParsed, String, Triple<Boolean, Double, String>>>()
                var duplicatesInCsv = 0

                groupedCsv.values.forEach { list ->
                    uniqueCsv += list.first()
                    duplicatesInCsv += (list.size - 1)
                }

                val toImport = mutableListOf<Triple<RowParsed, String, Pair<Boolean, Double>>>()
                var duplicatesInFirestore = 0

                uniqueCsv.forEach { (r, docId, triple) ->
                    val valido = triple.first
                    val indice = triple.second
                    val dupKey = triple.third

                    if (existingKeys.contains(dupKey)) {
                        duplicatesInFirestore++
                    } else {
                        toImport += Triple(r, docId, Pair(valido, indice))
                    }
                }

                finImportUI()

                val totalDuplicates = duplicatesInCsv + duplicatesInFirestore

                if (toImport.isEmpty()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Importación cancelada")
                        .setMessage("No es posible importar estas filas porque son filas duplicadas.")
                        .setPositiveButton("Entendido", null)
                        .show()
                    return@addOnSuccessListener
                }

                if (totalDuplicates > 0) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Filas duplicadas detectadas")
                        .setMessage(
                            "Hay $totalDuplicates filas duplicadas que NO se importarán.\n" +
                                    "¿Deseas importar el resto (${toImport.size} filas)?"
                        )
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Importar resto") { _, _ ->
                            importarFilasNoDuplicadas(toImport)
                        }
                        .show()
                } else {
                    importarFilasNoDuplicadas(toImport)
                }
            }
            .addOnFailureListener {
                finImportUI()
                toast("No se pudieron cargar predicciones existentes para deduplicar.")
            }
    }

    private fun importarFilasNoDuplicadas(rows: List<Triple<RowParsed, String, Pair<Boolean, Double>>>) {
        if (rows.isEmpty()) {
            toast("No hay filas para importar")
            return
        }

        startImportUI()

        val db = FirebaseFirestore.getInstance()
        val fechaImport = Timestamp.now()

        var processed = 0
        var importadas = 0
        var validas = 0
        var noValidas = 0

        rows.forEach { (r, docId, result) ->
            val (valido, indice) = result

            val femTxt = sexoLabel(r.fem)
            val cardioTxt = cardioLabel(r.cardio)
            val recTxt = yesNoLabel(r.rec)
            val causaTxt = yesNoLabel(r.causa)
            val validoTxt = if (valido) "Si" else "No"

            val pred = hashMapOf<String, Any>(
                "doc_id" to docId,
                "nombre_medico" to (name ?: ""),
                "uid_medico" to (userUid ?: ""),
                "edad" to r.edad.toString(),
                "femenino" to femTxt,
                "capnometria" to r.capno.toString(),
                "causa_cardiaca" to causaTxt,
                "cardio_manual" to cardioTxt,
                "rec_pulso" to recTxt,
                "fecha" to fechaImport,
                "valido" to validoTxt,
                "indice" to indice,
                "prediction_mode" to r.mode,
                "momento_prediccion_legible" to modeToLabel(r.mode),
                "modelos" to mutableListOf<String>(),
                "no_modelos" to mutableListOf("Modelo1", "Modelo2", "Modelo3", "Modelo4")
            )

            val docRef = db.collection("predicciones").document(docId)

            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    false
                } else {
                    tx.set(docRef, pred)
                    true
                }
            }.addOnSuccessListener { created ->
                if (created) {
                    importadas++
                    if (valido) validas++ else noValidas++
                }
            }.addOnCompleteListener {
                processed++
                binding.barraProgreso.progress = ((processed.toFloat() / rows.size) * 100).toInt()

                if (processed == rows.size) {
                    if (importadas > 0) {
                        actualizarContadores(validas, noValidas)
                    } else {
                        toast("No se importó ninguna fila (todas duplicadas).")
                        resetUI()
                        finImportUI()
                    }
                }
            }
        }
    }

    private fun actualizarContadores(validas: Int, noValidas: Int) {
        val total = validas + noValidas
        val db = FirebaseFirestore.getInstance()
        val uid = userUid ?: run {
            toast("No se pudo actualizar contadores (usuario desconocido)")
            finImportUI()
            return
        }

        db.collection("users").document(uid).update(
            mapOf(
                "numeroPredicciones" to FieldValue.increment(total.toLong()),
                "predicciones_validas" to FieldValue.increment(validas.toLong()),
                "predicciones_no_validas" to FieldValue.increment(noValidas.toLong())
            )
        ).addOnSuccessListener {
            toast("Importación completada: $total filas")
            resetUI()
        }.addOnFailureListener {
            toast("Importadas $total filas, pero no se pudieron actualizar los contadores")
            resetUI()
        }.addOnCompleteListener {
            finImportUI()
        }
    }

    private fun resetUI() {
        rowsReady.clear()
        currentCsvUri = null
        binding.tvFileName.text = "Selecciona un archivo CSV…"
        binding.btnAddPredictions.visibility = View.GONE
        binding.btnAddPredictions.isEnabled = false
        binding.btnClearFile.visibility = View.GONE
        binding.barraProgreso.visibility = View.GONE
        binding.barraProgreso.progress = 0
    }

    private fun startImportUI() {
        binding.barraProgreso.visibility = View.VISIBLE
        binding.barraProgreso.progress = 0
        binding.btnAddPredictions.isEnabled = false
        binding.filePickerArea.isEnabled = false
        binding.btnClearFile.isEnabled = false
    }

    private fun finImportUI() {
        binding.barraProgreso.visibility = View.GONE
        binding.filePickerArea.isEnabled = true
        binding.btnClearFile.isEnabled = true
        binding.btnAddPredictions.isEnabled = rowsReady.isNotEmpty()
    }

    private fun normalize(value: String?): String {
        val s = (value ?: "").trim().lowercase(Locale.getDefault())
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    private fun cardioLabel(cardio01: Int): String =
        if (cardio01 == 1) "Manual" else "Mecánica"

    private fun yesNoLabel(value01: Int): String =
        if (value01 == 1) "Si" else "No"

    private fun sexoLabel(fem01: Int): String =
        if (fem01 == 1) "Si" else "No"

    private fun buildDupKey(
        uidMedico: String?,
        mode: String,
        edad: String,
        femenino: String,
        capno: String,
        causa: String,
        cardio: String,
        rec: String,
        valido: String
    ): String {
        return listOf(
            normalize(uidMedico),
            normalize(mode),
            normalize(edad),
            normalize(femenino),
            normalize(capno),
            normalize(causa),
            normalize(cardio),
            normalize(rec),
            normalize(valido)
        ).joinToString("|")
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun mostrarAyudaCsv() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_csv_help, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<MaterialButton>(R.id.btnEntendido)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}