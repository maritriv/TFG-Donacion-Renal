package com.lhc.tfg_prediccion.ui.prediction

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.databinding.ActivityImportBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity
import androidx.documentfile.provider.DocumentFile
import android.graphics.drawable.ColorDrawable
import com.google.android.material.button.MaterialButton
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.util.PrediccionDedup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding
    private var userUid: String? = null
    private var name: String? = null

    // Estado en memoria (pre-validado y parseado)
    private val rowsReady = mutableListOf<RowParsed>()
    private var currentCsvUri: Uri? = null
    private var fecha = Timestamp.now()

    /** Fila parseada y lista para calcular/guardar */
    private data class RowParsed(
        val edad: Int,
        val capno: Int,
        val fem: Int,      // 0/1
        val cardio: Int,   // 0/1
        val rec: Int,      // 0/1
        val causa: Int,    // 0/1
        val mode: String   // BEFORE/MID/AFTER
    )

    // ------------ Ciclo de vida ------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userUid = intent.getStringExtra("userUid")
        name = intent.getStringExtra("userName")

        resetUI()

        // Área clicable para elegir CSV
        binding.filePickerArea.setOnClickListener { seleccionarCsv() }

        // X para limpiar selección (siempre disponible tras elegir archivo)
        binding.btnClearFile.setOnClickListener {
            resetUI()
            toast("Archivo deseleccionado")
        }

        // Ayuda
        binding.btnHelp.setOnClickListener { mostrarAyudaCsv() }

        // Volver
        binding.btnVolver.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("userUid", userUid)
                putExtra("userName", name)
            })
        }

        // Añadir (solo si hay filas parseadas)
        binding.btnAddPredictions.setOnClickListener {
            if (rowsReady.isEmpty()) {
                toast("No hay datos válidos. Selecciona un CSV válido.")
                return@setOnClickListener
            }
            importarFilasEnFirestore(rowsReady)
        }
    }

    // ------------ Selector de archivos ------------
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

        // Obtener nombre real del archivo
        val docFile = DocumentFile.fromSingleUri(this, uri)
        val displayName = docFile?.name ?: "Archivo CSV seleccionado"
        binding.tvFileName.text = displayName

        // Mostrar botón "X" para deseleccionar
        binding.btnClearFile.visibility = View.VISIBLE

        // Validar y parsear CSV
        validarYParsearCSV(uri)
    }

    // ------------ Validación + parseo (no escribe nada en BD) ------------
    private fun validarYParsearCSV(uri: Uri) {
        try {
            rowsReady.clear()

            val allLines = contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readLines()
                ?: run {
                    toast("CSV inválido: no se pudo abrir el archivo.")
                    // Deja visible la X para reintentar
                    binding.btnClearFile.visibility = View.VISIBLE
                    return
                }

            if (allLines.isEmpty()) {
                toast("CSV inválido: archivo vacío.")
                binding.btnClearFile.visibility = View.VISIBLE
                return
            }

            val sep = if (allLines.first().contains(",")) "," else ";"
            val header = allLines.first().split(sep).map { it.trim() }
            val dataLines = allLines.drop(1)

            val esExportApp =
                header.any { it.equals("Edad", true) } &&
                        header.any { it.equals("Femenino", true) } &&
                        header.any { it.equals("Capnometria", true) }

            // Índices (exportado por la app)
            fun idx(name: String) = header.indexOfFirst { it.equals(name, true) }
            val idxEdad  = idx("Edad")
            val idxFem   = idx("Femenino")
            val idxCap   = idx("Capnometria")
            val idxCausa = idx("Causa_cardiaca")
            val idxCard  = idx("Cardio_manual")
            val idxRec   = idx("Recuperacion_pulso")
            val idxMom   = idx("Momento")

            fun to01(text: String): Int? {
                val t = text.trim().lowercase()
                return when {
                    t == "1" || t == "si" || t == "sí" || t == "true" -> 1
                    t == "0" || t == "no" || t == "false" -> 0
                    else -> text.toIntOrNull()
                }
            }

            fun cardioTo01(text: String): Int? {
                val t = text.trim().lowercase()
                return when (t) {
                    "manual" -> 1
                    "mecánica", "mecanica" -> 0
                    // por si te llega en formato antiguo
                    "si", "sí", "true", "1" -> 1
                    "no", "false", "0" -> 0
                    else -> text.toIntOrNull()
                }
            }

            var pocasCols = false
            var demasiadasCols = false
            var valoresInvalidos = false

            dataLines.forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = raw.split(sep).map { it.trim() }

                if (esExportApp) {
                    if (cols.size < header.size) { pocasCols = true; return@forEach }

                    fun col(i: Int) = cols.getOrNull(i) ?: ""

                    val edad   = col(idxEdad).toIntOrNull()
                    val capno  = col(idxCap).toIntOrNull()
                    val fem    = to01(col(idxFem))
                    val cardio = cardioTo01(col(idxCard))
                    val rec    = to01(col(idxRec))
                    val causa  = to01(col(idxCausa))
                    val mode   = modeFromLabelLoose(col(idxMom))

                    if (edad == null || capno == null || fem == null || cardio == null || rec == null || causa == null) {
                        valoresInvalidos = true
                        return@forEach
                    }
                    rowsReady += RowParsed(edad, capno, fem, cardio, rec, causa, mode)

                } else {
                    // Clásico: 6 o 7
                    if (cols.size < 6) { pocasCols = true; return@forEach }
                    if (cols.size > 7) { demasiadasCols = true; return@forEach }

                    val edad   = cols[0].toIntOrNull()
                    val capno  = cols[1].toIntOrNull()
                    val fem    = cols[2].toIntOrNull()
                    val cardio = cardioTo01(cols[3])
                    val rec    = cols[4].toIntOrNull()
                    val causa  = cols[5].toIntOrNull()
                    val mode   = if (cols.size == 7) modeFromLabelLoose(cols[6]) else MODE_AFTER // <—

                    if (edad == null || capno == null || fem == null || cardio == null || rec == null || causa == null) {
                        valoresInvalidos = true
                        return@forEach
                    }
                    rowsReady += RowParsed(edad, capno, fem, cardio, rec, causa, mode)
                }
            }

            // Resultado
            when {
                rowsReady.isEmpty() && pocasCols ->
                    toast("CSV inválido: columnas insuficientes. Revisa el formato del CSV.")
                rowsReady.isEmpty() && demasiadasCols ->
                    toast("CSV inválido: demasiadas columnas. Revisa el formato del CSV.")
                rowsReady.isEmpty() && valoresInvalidos ->
                    toast("CSV inválido: valores no numéricos o fuera de rango.")
                rowsReady.isEmpty() ->
                    toast("CSV inválido: error desconocido. Selecciona otro archivo.")
                else -> {
                    // OK
                    binding.btnAddPredictions.apply {
                        visibility = View.VISIBLE
                        isEnabled = true
                        text = "AÑADIR ${rowsReady.size} FILAS"
                    }
                    // La X sigue visible por si quiere deseleccionar
                    binding.btnClearFile.visibility = View.VISIBLE
                    toast("CSV válido")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            toast("CSV inválido: error desconocido. Selecciona otro archivo.")
            // La X permanece visible para reintentar
            binding.btnClearFile.visibility = View.VISIBLE
        }
    }

    // ------------ Importar en Firestore lo ya parseado ------------
    private fun importarFilasEnFirestore(rows: List<RowParsed>) {
        if (rows.isEmpty()) { toast("No hay filas para importar"); return }

        val db = FirebaseFirestore.getInstance()

        // 1) Calcular docId para cada fila + detectar duplicados dentro del CSV
        val rowsWithId = rows.map { r ->
            val femTxt    = if (r.fem == 1) "Si" else "No"
            val cardioTxt = if (r.cardio == 1) "Manual" else "Mecánica"
            val recTxt    = if (r.rec == 1) "Si" else "No"
            val causaTxt  = if (r.causa == 1) "Si" else "No"

            // OJO: valido depende del modelo (lo calculamos después), así que de momento lo dejamos vacío
            // y lo calculamos luego para construir el docId final.
            r to listOf(femTxt, cardioTxt, recTxt, causaTxt)
        }

        // Generamos docId ya con valido (porque tu PrediccionDedup incluye "valido" en la clave)
        val computed = rowsWithId.map { (r, texts) ->
            val femTxt = texts[0]
            val cardioTxt = texts[1]
            val recTxt = texts[2]
            val causaTxt = texts[3]

            val (valido, indice) = calcularIndiceYValidez(
                mode = r.mode,
                edad = r.edad,
                femenino = r.fem,
                capnometria = r.capno,
                causacardiaca = r.causa,
                cardiomanual = r.cardio,
                recuperacion = r.rec,
                tiempoMin = 0
            )

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

            Triple(r, docId, Pair(valido, indice))
        }

        // Duplicados dentro del CSV (mismo docId)
        val grouped = computed.groupBy { it.second }
        val unique = mutableListOf<Triple<RowParsed, String, Pair<Boolean, Double>>>()
        var duplicatesInCsv = 0
        grouped.values.forEach { list ->
            unique += list.first()
            duplicatesInCsv += (list.size - 1)
        }

        // 2) Comprobar cuáles ya existen en Firestore
        startImportUI()

        val docRefs = unique.map { (_, docId, _) ->
            db.collection("predicciones").document(docId)
        }

        // Leemos todos (si son muchos, se puede paginar, pero para TFG suele ir bien)
        val tasks = docRefs.map { it.get() }

        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks)
            .addOnSuccessListener { snaps ->
                val existingIds = snaps.filter { it.exists() }.map { it.id }.toHashSet()

                val toImport = mutableListOf<Triple<RowParsed, String, Pair<Boolean, Double>>>()
                var duplicatesInFirestore = 0

                unique.forEach { triple ->
                    val docId = triple.second
                    if (existingIds.contains(docId)) duplicatesInFirestore++ else toImport += triple
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
                            "Hay $totalDuplicates filas duplicadas que no se van a poder importar.\n" +
                                    "¿Deseas importar igualmente el resto (${toImport.size} filas)?"
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
                toast("No se pudo comprobar duplicados. Inténtalo de nuevo.")
            }
    }

    private fun importarFilasNoDuplicadas(rows: List<Triple<RowParsed, String, Pair<Boolean, Double>>>) {
        if (rows.isEmpty()) { toast("No hay filas para importar"); return }

        startImportUI()

        val db = FirebaseFirestore.getInstance()

        var processed = 0
        var importadas = 0
        var validas = 0
        var noValidas = 0

        rows.forEach { (r, docId, result) ->
            val (valido, indice) = result

            val femTxt    = if (r.fem == 1) "Si" else "No"
            val cardioTxt = if (r.cardio == 1) "Manual" else "Mecánica"
            val recTxt    = if (r.rec == 1) "Si" else "No"
            val causaTxt  = if (r.causa == 1) "Si" else "No"
            val validoTxt = if (valido) "Si" else "No"

            val pred = hashMapOf(
                "doc_id" to docId,
                "nombre_medico" to name,
                "uid_medico" to userUid,
                "edad" to r.edad.toString(),
                "femenino" to femTxt,
                "capnometria" to r.capno.toString(),
                "causa_cardiaca" to causaTxt,
                "cardio_manual" to cardioTxt,
                "rec_pulso" to recTxt,
                "fecha" to fecha,
                "valido" to validoTxt,
                "indice" to indice,
                "prediction_mode" to r.mode,
                "momento_prediccion_legible" to modeToLabel(r.mode),
                "modelos" to mutableListOf<String>(),
                "no_modelos" to mutableListOf("Modelo1","Modelo2","Modelo3","Modelo4")
            )

            val docRef = db.collection("predicciones").document(docId)

            // Transacción: si existe -> no guarda
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) false else { tx.set(docRef, pred); true }
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
        }.addOnCompleteListener { finImportUI() }
    }

    // ------------ UI helpers ------------
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

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun mostrarAyudaCsv() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_csv_help, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Fondo transparente para que solo se vea la CardView redondeada
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        // Botón ENTENDIDO cierra el diálogo
        val btnOk = dialogView.findViewById<MaterialButton>(R.id.btnEntendido)
        btnOk.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}