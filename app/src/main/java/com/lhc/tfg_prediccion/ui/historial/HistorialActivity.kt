package com.lhc.tfg_prediccion.ui.historial

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.data.model.Prediccion
import com.lhc.tfg_prediccion.databinding.ActivityHistorialBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity
import java.text.SimpleDateFormat
import java.util.Locale
import java.io.OutputStreamWriter

// Utilidades canónicas de modos
import com.lhc.tfg_prediccion.ui.prediction.MODE_BEFORE
import com.lhc.tfg_prediccion.ui.prediction.MODE_MID
import com.lhc.tfg_prediccion.ui.prediction.MODE_AFTER
import com.lhc.tfg_prediccion.ui.prediction.modeToLabel
import com.lhc.tfg_prediccion.ui.prediction.modeFromLabelLoose

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private var userUid: String? = null
    private var name: String? = null
    private var fullName: String? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userUid = intent.getStringExtra("userUid")
        name = intent.getStringExtra("userName")

        // Cargar nombre completo (si no vino ya en el intent)
        cargarNombreCompleto()

        // Volver
        findViewById<TextView>(R.id.btn_volver).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("userName", fullName ?: name)
                putExtra("userUid", userUid)
            })
        }

        // Exportar CSV
        findViewById<TextView>(R.id.btn_csv).setOnClickListener { generarCSV() }

        // Spinner ordenar
        val opciones = arrayOf("Fecha", "Edad", "Capnometría", "Momento")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opciones)
        binding.orderSpinner.adapter = adapter

        val spinner = findViewById<Spinner>(R.id.order_spinner)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val orden = binding.orderSpinner.selectedItem.toString()
                cargarHistorial(orden)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    /** Intenta construir "Nombre Apellidos" desde Firestore si no vino en el intent. */
    private fun cargarNombreCompleto() {
        val uid = userUid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val n = doc.getString("name") ?: name ?: ""
                val ap = doc.getString("lastname")
                    ?: ""
                fullName = listOf(n, ap).filter { it.isNotBlank() }.joinToString(" ").ifBlank { n }
            }
            .addOnFailureListener {
                fullName = name
            }
    }

    private fun cargarHistorial(orden: String) {
        val tabla = findViewById<TableLayout>(R.id.tabla_historial)
        tabla.removeAllViews()

        // Encabezado
        val filaEncabezado = TableRow(this)
        val params = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 16, 8, 16) }

        val encabezados = listOf(
            "",
            "Edad", "Fem", "Capnometría",
            "Causa cardiaca", "Cardiocompresión", "RecPulso",
            "Momento", "Resultado", "Informe", "Eliminar"
        )
        encabezados.forEach { titulo ->
            TextView(this).apply {
                text = titulo
                textSize = 16f
                setPadding(16, 24, 16, 24)
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                layoutParams = params
                filaEncabezado.addView(this)
            }
        }
        tabla.addView(filaEncabezado)

        // Datos
        db.collection("predicciones").whereEqualTo("uid_medico", userUid)
            .get()
            .addOnSuccessListener { snap ->
                val lista = snap.map { it.toObject(Prediccion::class.java) }

                // Ordenación
                val listaOrdenada = when (orden) {
                    "Fecha" -> lista.sortedBy { it.fecha }
                    "Edad" -> lista.sortedBy { it.edad.toIntOrNull() ?: 0 }
                    "Capnometría" -> lista.sortedBy { it.capnometria.toIntOrNull() ?: 0 }
                    "Momento" -> lista.sortedBy {
                        val mode = it.prediction_mode
                            ?: modeFromLabelLoose(it.momento_prediccion_legible ?: "")
                        when (mode) {
                            MODE_BEFORE -> 0
                            MODE_MID    -> 1
                            MODE_AFTER  -> 2
                            else        -> 3
                        }
                    }
                    else -> lista
                }

                var contador = 1
                listaOrdenada.forEach { pred ->
                    nuevaFila(tabla, contador, pred)

                    // Separador
                    View(this).apply {
                        layoutParams = TableRow.LayoutParams(
                            TableRow.LayoutParams.MATCH_PARENT, 1
                        ).apply { setMargins(8, 4, 8, 4) }
                        setBackgroundColor(
                            ContextCompat.getColor(
                                this@HistorialActivity, android.R.color.darker_gray
                            )
                        )
                        tabla.addView(this)
                    }
                    contador++
                }
            }
    }

    private fun nuevaFila(tabla: TableLayout, contador: Int, pred: Prediccion) {
        val fila = TableRow(this)

        val params = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 16, 8, 16) }

        // Fondo alterno
        fila.setBackgroundColor(
            ContextCompat.getColor(
                this, if (contador % 2 == 0) R.color.blue1celdas else R.color.blue2celdas
            )
        )

        fun addCell(texto: String, bold: Boolean = false, center: Boolean = true) {
            TextView(this).apply {
                text = texto
                textSize = 16f
                setPadding(16, 24, 16, 24)
                gravity = if (center) Gravity.CENTER else Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                typeface = Typeface.create(
                    "sans-serif-condensed",
                    if (bold) Typeface.BOLD else Typeface.NORMAL
                )
                layoutParams = params
                fila.addView(this)
            }
        }

        // # (contador)
        addCell(contador.toString(), center = true)

        // Edad, Femenino, Capno, Causa, Cardio, Rec
        addCell(pred.edad)
        addCell(pred.femenino)
        addCell(pred.capnometria)
        addCell(pred.causa_cardiaca)
        addCell(pred.cardio_manual)
        addCell(pred.rec_pulso)

        // Momento — SIEMPRE CANÓNICO
        val canonicalMoment = modeToLabel(
            pred.prediction_mode ?: modeFromLabelLoose(pred.momento_prediccion_legible ?: "")
        )
        addCell(canonicalMoment)

        // Resultado
        addCell(pred.valido)

        // Informe (PDF)
        ImageView(this).apply {
            isClickable = true
            isFocusable = true
            setImageResource(R.drawable.download)
            layoutParams = TableRow.LayoutParams(55, 55).apply { gravity = Gravity.CENTER }
            setOnClickListener {
                generarPDF(
                    pred.edad, pred.femenino, pred.capnometria, pred.causa_cardiaca,
                    pred.cardio_manual, pred.rec_pulso, fullName ?: "Desconocido",
                    pred.fecha, pred.valido, canonicalMoment
                )
            }
            fila.addView(this)
        }

        // Eliminar
        ImageView(this).apply {
            isClickable = true
            isFocusable = true
            setImageResource(R.drawable.basuraic)
            layoutParams = TableRow.LayoutParams(55, 55).apply { gravity = Gravity.CENTER }
            setOnClickListener {
                AlertDialog.Builder(this@HistorialActivity)
                    .setTitle("Confirmar eliminación")
                    .setMessage("¿Está seguro de que quiere eliminar esta predicción?")
                    .setPositiveButton("Si") { d, _ ->
                        eliminarPrediccion(pred)
                        d.dismiss()
                    }
                    .setNegativeButton("No") { d, _ -> d.dismiss() }
                    .create()
                    .show()
            }
            fila.addView(this)
        }

        tabla.addView(fila)
    }

    private fun generarPDF(
        edad: String, femenino: String, capnometria: String, causa_cardiaca: String,
        cardio_manual: String, recuperacionPulso: String, nombre: String,
        fecha: Timestamp, valido: String, momentoCanonico: String
    ) {
        try {
            val sdf = SimpleDateFormat("dd-MM-yyyy_HH-mm", Locale.getDefault())
            val fechaLegible = sdf.format(fecha.toDate())
            val nombreLimpio = nombre.replace(" ", "_")  // quita espacios para que no dé problemas
            val fileName = "Reporte_${fechaLegible}_$nombreLimpio.pdf"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    val pdfDocument = PdfDocument(PdfWriter(outputStream))
                    val document = Document(pdfDocument, PageSize.A4)

                    val title = Paragraph("RESULTADOS PREDICCIÓN DONANTE DE RIÑÓN")
                        .setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(16f)
                    document.add(title)

                    val fechaLegible = getFormattedDate(fecha)
                    val doctorInfo = Paragraph("Nombre médico: $nombre\nFecha: $fechaLegible")
                        .setTextAlignment(TextAlignment.LEFT).setFontSize(12f)
                    document.add(doctorInfo)
                    document.add(Paragraph("\n"))

                    val donorInfo = Paragraph("DATOS POSIBLE DONANTE:")
                        .setBold().setTextAlignment(TextAlignment.LEFT).setFontSize(14f)
                    document.add(donorInfo)

                    document.add(Paragraph("Momento de la predicción: $momentoCanonico"))
                    document.add(Paragraph("Edad: $edad"))
                    document.add(Paragraph("Sexo femenino: $femenino"))
                    document.add(Paragraph("Capnometría: $capnometria"))
                    document.add(Paragraph("Causa cardiaca: $causa_cardiaca"))
                    document.add(Paragraph("Cardiocompresión extrahospitalaria manual: $cardio_manual"))
                    document.add(Paragraph("Recuperación circulación: $recuperacionPulso"))

                    val prediction = if (valido == "Si") "PREDICCIÓN: VÁLIDO" else "PREDICCIÓN: NO VÁLIDO"
                    document.add(Paragraph(prediction).setBold().setFontSize(12f))

                    document.close()
                    Toast.makeText(this, "PDF guardado en Descargas", Toast.LENGTH_SHORT).show()
                    abrirPDF(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al generar el PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirPDF(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Abrir PDF con"))
    }

    private fun getFormattedDate(timestamp: Timestamp): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }

    private fun eliminarPrediccion(pred: Prediccion) {
        db.collection("predicciones")
            .whereEqualTo("edad", pred.edad)
            .whereEqualTo("capnometria", pred.capnometria)
            .whereEqualTo("femenino", pred.femenino)
            .whereEqualTo("causa_cardiaca", pred.causa_cardiaca)
            .whereEqualTo("cardio_manual", pred.cardio_manual)
            .whereEqualTo("rec_pulso", pred.rec_pulso)
            .whereEqualTo("nombre_medico", name)
            .whereEqualTo("valido", pred.valido)
            .whereEqualTo("uid_medico", userUid)
            .whereEqualTo("fecha", pred.fecha)
            .limit(1)
            .get()
            .addOnSuccessListener { qs ->
                if (!qs.isEmpty) {
                    for (doc in qs) {
                        db.collection("predicciones").document(doc.id).delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Predicción eliminada correctamente", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "No se ha podido eliminar la predicción", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }

        // Disminuir contadores
        actualizarContadorPredicciones(pred.valido == "Si")
    }

    private fun actualizarContadorPredicciones(valido: Boolean) {
        val updates = mutableMapOf<String, Any>().apply {
            put("numeroPredicciones", FieldValue.increment(-1))
            if (valido) put("predicciones_validas", FieldValue.increment(-1))
            else put("predicciones_no_validas", FieldValue.increment(-1))
        }
        userUid?.let { uid ->
            db.collection("users").document(uid).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Contadores actualizados", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HistorialActivity::class.java).apply {
                        putExtra("userUid", userUid)
                        putExtra("userName", fullName ?: name)
                    })
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al actualizar los contadores", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun generarCSV() {
        db.collection("predicciones").whereEqualTo("uid_medico", userUid)
            .get()
            .addOnSuccessListener { snap ->
                val lista = snap.map { it.toObject(Prediccion::class.java) }

                try {
                    // Nombre del archivo con nombre y apellidos si están disponibles
                    val displayName = (fullName ?: name ?: "medico")
                        .replace('/', '-')
                        .replace('\\', '-')
                    val fileName = "historialPredicciones_${displayName}_${System.currentTimeMillis()}.csv"

                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = contentResolver
                    val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

                    uri?.let {
                        resolver.openOutputStream(it)?.use { outputStream ->
                            val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)

                            // Cabecera (formato app, re-importable)
                            writer.write("Edad,Femenino,Capnometria,Causa_cardiaca,Cardio_manual,Recuperacion_pulso,Momento,Valido,UID_medico,Fecha\n")

                            // Filas con Momento canónico
                            lista.forEach { pred ->
                                val canonicalMoment = modeToLabel(
                                    pred.prediction_mode ?: modeFromLabelLoose(pred.momento_prediccion_legible ?: "")
                                )
                                val row = listOf(
                                    pred.edad,
                                    pred.femenino,
                                    pred.capnometria,
                                    pred.causa_cardiaca,
                                    pred.cardio_manual,
                                    pred.rec_pulso,
                                    canonicalMoment,
                                    pred.valido,
                                    pred.uid_medico,
                                    getFormattedDate(pred.fecha)
                                ).joinToString(",")
                                writer.write("$row\n")
                            }

                            writer.flush()
                            writer.close()
                            Toast.makeText(this, "CSV guardado en Descargas", Toast.LENGTH_SHORT).show()
                            abrirArchivoCSV(it)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error al generar el CSV", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun abrirArchivoCSV(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay aplicación para abrir archivos CSV", Toast.LENGTH_SHORT).show()
        }
    }
}