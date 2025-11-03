package com.lhc.tfg_prediccion.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

// ====================== Nombre completo (utils) ======================
object UserUtils {
    /**
     * Carga el nombre completo desde Firestore con los mismos campos que usas:
     *  - "name" + "lastname"
     * Si falla, usa el fallback.
     */
    fun cargarNombreCompleto(
        db: FirebaseFirestore,
        uid: String?,
        nameFallback: String?,
        onResult: (String) -> Unit
    ) {
        val uidSafe = uid ?: return onResult(nameFallback ?: "")
        db.collection("users").document(uidSafe).get()
            .addOnSuccessListener { doc ->
                val n = doc.getString("name") ?: nameFallback ?: ""
                val ap = doc.getString("lastname") ?: ""
                val full = listOf(n, ap)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { n }
                onResult(full)
            }
            .addOnFailureListener {
                onResult(nameFallback ?: "")
            }
    }
}

// ====================== Modelo de datos PDF ======================
data class PdfPrediction(
    val doctorName: String,
    val fecha: Timestamp,
    val momentoCanonico: String,
    val edad: String,
    val femenino: String,
    val capnometria: String,
    val causaCardiaca: String,
    val cardioManual: String,
    val recPulso: String,
    val valido: Boolean,
    val indice: Double? = null
)

// ====================== Helpers ======================
fun formatFilenameDate(ts: Timestamp): String {
    val sdf = SimpleDateFormat("dd-MM-yyyy_HH-mm", Locale.getDefault())
    return sdf.format(ts.toDate())
}

fun formatDisplayDate(ts: Timestamp): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(ts.toDate())
}

fun cleanDoctorName(name: String): String {
    var result = Normalizer.normalize(name, Normalizer.Form.NFD)
    result = result.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "") // quita tildes
    result = result.replace("[^A-Za-z0-9_ ]".toRegex(), "") // símbolos raros
    result = result.trim().replace("\\s+".toRegex(), "_")   // espacios → _
    return result.ifBlank { "Medico" }
}

private fun safeFileName(base: String, doctorName: String, ts: Timestamp): String {
    val date = formatFilenameDate(ts)
    val doc = cleanDoctorName(doctorName)
    return "${base}_${date}_$doc.pdf"
}

// ====================== Generación PDF ======================
fun generatePredictionPdf(context: Context, data: PdfPrediction): Uri? {
    return try {
        val fileName = safeFileName("Reporte", data.doctorName, data.fecha)

        val resolver = context.contentResolver
        val collection: Uri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                // En < Q, usamos el mismo endpoint aunque RELATIVE_PATH no aplique.
                MediaStore.Files.getContentUri("external")
            }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1) // marcado mientras escribimos
            }
        }

        val itemUri = resolver.insert(collection, values)
        if (itemUri == null) {
            Toast.makeText(context, "No se pudo crear el archivo en Descargas", Toast.LENGTH_SHORT).show()
            return null
        }

        resolver.openOutputStream(itemUri)?.use { os ->
            val pdf = PdfDocument(PdfWriter(os))
            val doc = Document(pdf, PageSize.A4)

            // Encabezado
            doc.add(
                Paragraph("RESULTADOS PREDICCIÓN DONANTE DE RIÑÓN")
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(16f)
            )

            val displayDate = formatDisplayDate(data.fecha)
            doc.add(
                Paragraph("Nombre médico: ${data.doctorName}\nFecha: $displayDate")
                    .setTextAlignment(TextAlignment.LEFT)
                    .setFontSize(12f)
            )
            doc.add(Paragraph("\n"))

            // Cuerpo
            doc.add(
                Paragraph("DATOS POSIBLE DONANTE:")
                    .setBold()
                    .setTextAlignment(TextAlignment.LEFT)
                    .setFontSize(14f)
            )
            doc.add(Paragraph("Momento de la predicción: ${data.momentoCanonico}"))
            doc.add(Paragraph("Edad: ${data.edad}"))
            doc.add(Paragraph("Sexo femenino: ${data.femenino}"))
            doc.add(Paragraph("Capnometría: ${data.capnometria}"))
            doc.add(Paragraph("Causa cardiaca: ${data.causaCardiaca}"))
            doc.add(Paragraph("Cardiocompresión extrahospitalaria manual: ${data.cardioManual}"))
            doc.add(Paragraph("Recuperación circulación: ${data.recPulso}"))

            data.indice?.let {
                doc.add(Paragraph("Índice calculado: " + String.format(Locale.getDefault(), "%.3f", it)))
            }

            val resultado = if (data.valido) "PREDICCIÓN: VÁLIDO" else "PREDICCIÓN: NO VÁLIDO"
            doc.add(Paragraph(resultado).setBold().setFontSize(12f))

            doc.close()
        }

        // Finaliza IS_PENDING para que aparezca en Downloads inmediatamente
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, done, null, null)
        }

        Toast.makeText(context, "PDF guardado en Descargas", Toast.LENGTH_SHORT).show()

        // Abrir el PDF
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(itemUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            // Sin visor: al menos ya está guardado
        }

        itemUri
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al generar el PDF", Toast.LENGTH_SHORT).show()
        return null
    }
}