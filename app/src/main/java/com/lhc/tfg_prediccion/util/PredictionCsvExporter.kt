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
import com.lhc.tfg_prediccion.ui.prediction.modeFromLabelLoose
import com.lhc.tfg_prediccion.ui.prediction.modeToLabel
import java.io.OutputStreamWriter

object PredictionCsvExporter {

    /**
     * Exporta una lista de predicciones a CSV en Descargas y lo intenta abrir.
     *
     * @param activity  Activity desde la que se llama (para contentResolver y startActivity)
     * @param predictions lista de Prediccion a exportar
     * @param doctorName nombre que aparecerá en el nombre del archivo (puede ser null)
     * @param userUid uid del médico (puede ser null)
     */
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

                // Cabecera (formato app, re-importable)
                writer.write(
                    "Edad,Femenino,Capnometria,Causa_cardiaca,Cardio_manual," +
                            "Recuperacion_pulso,Momento,Valido,UID_medico,Fecha\n"
                )

                predictions.forEach { pred ->
                    val canonicalMoment = modeToLabel(
                        pred.prediction_mode
                            ?: modeFromLabelLoose(pred.momento_prediccion_legible ?: "")
                    )

                    val row = listOf(
                        pred.edad ?: "",
                        pred.femenino ?: "",
                        pred.capnometria ?: "",
                        pred.causa_cardiaca ?: "",
                        pred.cardio_manual ?: "",
                        pred.rec_pulso ?: "",
                        canonicalMoment,
                        pred.valido ?: "",
                        pred.uid_medico ?: (userUid ?: ""),
                        formatDisplayDate(pred.fecha)  // ya lo usabas en Historial
                    ).joinToString(",")

                    writer.write("$row\n")
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
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "No hay aplicación para abrir archivos CSV", Toast.LENGTH_SHORT).show()
        }
    }
}