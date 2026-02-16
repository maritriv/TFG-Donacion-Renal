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

// ====================== Nombre completo ======================
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

    // NUEVO: modo (BEFORE_RCP / MID_RCP / AFTER_RCP)
    val predictionMode: String? = null,

    val momentoCanonico: String,
    val edad: String,
    val femenino: String,
    val capnometria: String,
    val causaCardiaca: String,
    val cardioManual: String,
    val recPulso: String,
    val valido: Boolean,
    val indice: Double? = null,

    // NUEVOS: solo se muestran si aplica
    val colesterol: String? = null,
    val adrenalinaN: String? = null,
    val imc: String? = null
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
                MediaStore.Files.getContentUri("external")
            }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val itemUri = resolver.insert(collection, values)
        if (itemUri == null) {
            Toast.makeText(context, "Error al crear archivo", Toast.LENGTH_SHORT).show()
            return null
        }

        resolver.openOutputStream(itemUri)?.use { os ->
            val pdf = PdfDocument(PdfWriter(os))
            val doc = Document(pdf, PageSize.A4)

            // =============================================================
            // TÍTULO PRINCIPAL
            // =============================================================
            doc.add(
                Paragraph("RESULTADOS DE LA PREDICCIÓN DE DONANTE DE RIÑÓN")
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18f)
            )
            doc.add(Paragraph("\n"))

            // =============================================================
            // DATOS DEL PROFESIONAL
            // =============================================================
            val displayDate = formatDisplayDate(data.fecha)
            doc.add(
                Paragraph("DATOS DEL PROFESIONAL SANITARIO RESPONSABLE:")
                    .setBold()
                    .setFontSize(14f)
            )
            doc.add(
                Paragraph(
                    "Nombre del profesional sanitario responsable: ${data.doctorName}\n" +
                            "Fecha y hora de la predicción: $displayDate"
                ).setFontSize(12f)
            )
            doc.add(Paragraph("\n"))

            // =============================================================
            // DATOS DEL POSIBLE DONANTE (según modo)
            // =============================================================
            doc.add(
                Paragraph("DATOS DEL POSIBLE DONANTE:")
                    .setBold()
                    .setFontSize(14f)
            )

            fun dashIfBlank(s: String?): String = if (s.isNullOrBlank()) "—" else s

            // Colesterol puede venir como "0/1" o "Si/No"
            fun colesterolToText(v: String?): String {
                val t = (v ?: "").trim()
                return when (t) {
                    "1" -> "Sí"
                    "0" -> "No"
                    else -> dashIfBlank(t)
                }
            }

            val mode = (data.predictionMode ?: "").trim()
            val isBefore = mode == "BEFORE_RCP"
            val isMid = mode == "MID_RCP"
            val isAfter = (mode == "AFTER_RCP" || mode.isBlank()) // fallback por compatibilidad

            // Siempre mostramos el momento canónico
            doc.add(Paragraph("Momento de la predicción: ${data.momentoCanonico}"))

            when {
                // ---------------- BEFORE ----------------
                isBefore -> {
                    doc.add(Paragraph("Edad: ${dashIfBlank(data.edad)} años"))
                    doc.add(Paragraph("Capnometría (inicio): ${dashIfBlank(data.capnometria)}"))
                    doc.add(Paragraph("Colesterol: ${colesterolToText(data.colesterol)}"))
                }

                // ---------------- MID ----------------
                isMid -> {
                    doc.add(Paragraph("Capnometría (punto medio): ${dashIfBlank(data.capnometria)}"))
                    doc.add(Paragraph("Colesterol: ${colesterolToText(data.colesterol)}"))
                    doc.add(Paragraph("Adrenalina (n): ${dashIfBlank(data.adrenalinaN)}"))
                    doc.add(Paragraph("IMC: ${dashIfBlank(data.imc)}"))

                    val causa = if (data.causaCardiaca == "Si") "Cardíaca" else "No cardiaca"
                    doc.add(Paragraph("Causa principal del evento: $causa"))

                    val cardio = if (data.cardioManual == "Manual") "Manual" else "Mecánica"
                    doc.add(Paragraph("RCP extrahospitalaria: $cardio"))
                }

                // ---------------- AFTER ----------------
                else -> {
                    doc.add(Paragraph("Edad: ${dashIfBlank(data.edad)} años"))

                    val sexo = if (data.femenino == "Si" || data.femenino.equals("Mujer", true)) "Femenino" else "Masculino"
                    doc.add(Paragraph("Sexo: $sexo"))

                    doc.add(Paragraph("Capnometría (transferencia): ${dashIfBlank(data.capnometria)}"))

                    val causa = if (data.causaCardiaca == "Si") "Cardíaca" else "No cardiaca"
                    doc.add(Paragraph("Causa principal del evento: $causa"))

                    val cardio = if (data.cardioManual == "Manual") "Manual" else "Mecánica"
                    doc.add(Paragraph("RCP extrahospitalaria: $cardio"))

                    val rec = if (data.recPulso == "Si") "Sí" else "No"
                    doc.add(Paragraph("Recuperación de la circulación espontánea (ROSC): $rec"))
                }
            }

            data.indice?.let {
                doc.add(Paragraph("Índice calculado: ${String.format(Locale.US, "%.3f", it)}"))
            }

            doc.add(Paragraph("\n"))

            // =============================================================
            // RESULTADO FINAL
            // =============================================================
            val resultado = if (data.valido)
                "RESULTADO DE LA PREDICCIÓN: DONANTE VÁLIDO"
            else
                "RESULTADO DE LA PREDICCIÓN: DONANTE NO VÁLIDO"

            doc.add(
                Paragraph(resultado)
                    .setBold()
                    .setFontSize(14f)
            )

            doc.close()
        }

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
        } catch (_: Exception) {}

        itemUri
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al generar el PDF", Toast.LENGTH_SHORT).show()
        return null
    }
}