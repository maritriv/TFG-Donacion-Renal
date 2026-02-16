package com.lhc.tfg_prediccion.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.lhc.tfg_prediccion.data.model.Prediccion
import com.lhc.tfg_prediccion.ui.prediction.MODE_BEFORE
import com.lhc.tfg_prediccion.ui.prediction.MODE_MID
import com.lhc.tfg_prediccion.ui.prediction.modeFromLabelLoose
import com.lhc.tfg_prediccion.ui.prediction.modeToLabel
import java.io.OutputStreamWriter
import java.text.Normalizer
import java.util.Locale

object PredictionCsvExporter {

    fun exportCsv(
        activity: Activity,
        predictions: List<Prediccion>,
        doctorName: String?,
        userUid: String?
    ) {
        if (predictions.isEmpty()) {
            Toast.makeText(activity, "No hay predicciones para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val displayName = (doctorName ?: "medico")
                .ifBlank { "medico" }
                .replace('/', '-')
                .replace('\\', '-')

            val fileName = "predicciones_${displayName}_${System.currentTimeMillis()}.csv"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = activity.contentResolver
            val uri = resolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            )

            if (uri == null) {
                Toast.makeText(activity, "No se pudo crear el archivo CSV", Toast.LENGTH_SHORT).show()
                return
            }

            resolver.openOutputStream(uri)?.use { outputStream ->
                val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)

                // Cabecera
                writer.write(
                    "Edad,Femenino,Capnometria,Colesterol,Adrenalina_n,IMC," +
                            "Causa_cardiaca,Cardio_manual,Recuperacion_pulso," +
                            "Prediction_mode,Momento,Valido,Indice,UID_medico,Fecha\n"
                )

                fun stripAccents(s: String): String {
                    val normalized = Normalizer.normalize(s, Normalizer.Form.NFD)
                    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                }

                // CSV RFC4180 simple: quita saltos, quita tildes, y si hay coma/quote -> comillas
                fun csv(value: String?): String {
                    val raw = (value ?: "")
                        .replace("\r", " ")
                        .replace("\n", " ")
                        .trim()

                    val noAccents = stripAccents(raw)

                    val mustQuote = noAccents.contains(',') || noAccents.contains('"')
                    return if (!mustQuote) {
                        noAccents
                    } else {
                        "\"" + noAccents.replace("\"", "\"\"") + "\""
                    }
                }

                fun formatIndice(d: Double?): String =
                    if (d == null) "" else String.format(Locale.US, "%.3f", d)

                predictions.forEach { pred ->
                    val mode = pred.prediction_mode
                        ?: modeFromLabelLoose(pred.momento_prediccion_legible ?: "")

                    // Etiqueta canónica (del ModelCoefficients) + sin tildes
                    val canonicalMomentLabel = modeToLabel(mode)

                    // Variables por modo (vacías si no aplican)
                    val col = when (mode) {
                        MODE_BEFORE, MODE_MID -> pred.colesterol ?: ""
                        else -> ""
                    }
                    val adrenalina = when (mode) {
                        MODE_MID -> pred.adrenalina_n ?: ""
                        else -> ""
                    }
                    val imc = when (mode) {
                        MODE_MID -> pred.imc ?: ""
                        else -> ""
                    }

                    val uidOut = pred.uid_medico.ifBlank { userUid ?: "" }

                    val row = listOf(
                        csv(pred.edad),
                        csv(pred.femenino),
                        csv(pred.capnometria),

                        csv(col),
                        csv(adrenalina),
                        csv(imc),

                        csv(pred.causa_cardiaca),
                        csv(pred.cardio_manual),
                        csv(pred.rec_pulso),

                        csv(mode),                 // BEFORE_RCP / MID_RCP / AFTER_RCP
                        csv(canonicalMomentLabel), // "Inicio..." / "Mitad..." / "Despues..." (sin tildes)
                        csv(pred.valido),
                        csv(formatIndice(pred.indice)),

                        csv(uidOut),
                        csv(formatDisplayDate(pred.fecha))
                    ).joinToString(",")

                    writer.write(row)
                    writer.write("\n")
                }

                writer.flush()
                writer.close()

                Toast.makeText(activity, "CSV guardado en Descargas", Toast.LENGTH_SHORT).show()
                openCsvFile(activity, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, "Error al generar el CSV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCsvFile(activity: Activity, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "No hay aplicación para abrir archivos CSV", Toast.LENGTH_SHORT).show()
        }
    }
}