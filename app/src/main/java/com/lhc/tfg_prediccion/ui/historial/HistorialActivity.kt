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
import android.widget.Button
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
// para generar el PDF
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

// para exportar en CSV
import java.io.OutputStreamWriter


class HistorialActivity: AppCompatActivity() {
    private lateinit var binding: ActivityHistorialBinding
    private var userUid: String? = null
    private var name: String? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userUid = intent.getStringExtra("userUid")
        name = intent.getStringExtra("userName")

        // boton volver pagina principal
        val boton_volver = findViewById<Button>(R.id.btn_volver)
        boton_volver.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("userName", name)
            intent.putExtra("userUid", userUid)
            startActivity(intent)
        }

        // boton exportar
        val boton_exportar = findViewById<Button>(R.id.btn_csv)
        boton_exportar.setOnClickListener {
            generarCSV()
        }

        // configuracion spinner
        val opciones = arrayOf("Fecha", "Edad", "Capnometría")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opciones)
        binding.orderSpinner.adapter = adapter
        val spinner = findViewById<Spinner>(R.id.order_spinner)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                var orden = binding.orderSpinner.selectedItem.toString() // cojo valor del spinner
                cargarHistorial(orden)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                // no se hace nada
            }
        }

    }

    private fun cargarHistorial(orden: String){
        val tabla = findViewById<TableLayout>(R.id.tabla_historial)
        tabla.removeAllViews() // Limpiar la tabla

        // Asegurar que el encabezado siempre se añada
        val filaEncabezado = TableRow(this)
        val params = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(8, 16, 8, 16)

        // Lista de encabezados
        val encabezados = listOf("", "Edad", "Fem", "Capnometría", "Causa cardiaca", "Cardiocompresión", "RecPulso", "Resultado", "Informe", "Eliminar")
        for (titulo in encabezados) {
            val celda = TextView(this)
            celda.text = titulo
            celda.textSize = 16f
            celda.setPadding(16, 24, 16, 24)
            celda.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            celda.layoutParams = params
            filaEncabezado.addView(celda)
        }

        // añadir encabezado a la tabla
        tabla.addView(filaEncabezado)
        // cargo las predicciones de la base de datos segun el userUid del medico
        db.collection("predicciones").whereEqualTo("uid_medico", userUid)
            .get()
            .addOnCompleteListener{ firestoreTask ->
                if (firestoreTask.isSuccessful){
                    val predicciones = firestoreTask.result
                    val listaPredicciones = mutableListOf<Prediccion>()

                    for (aux in predicciones!!) {
                        val pred = aux.toObject(Prediccion::class.java)
                        listaPredicciones.add(pred)
                    }

                    // ordenar lista segun orden spinner
                    val listaOrdenada = when (orden) {
                        "Fecha" -> listaPredicciones.sortedBy { it.fecha }
                        "Edad" -> listaPredicciones.sortedBy { it.edad.toIntOrNull() ?: 0 }
                        "Capnometría" -> listaPredicciones.sortedBy { it.capnometria.toIntOrNull() ?: 0 }
                        else -> listaPredicciones
                    }

                    // añadir filas a la tabla
                    var contador = 1
                    for (pred in listaOrdenada){
                        nuevaFila(tabla, contador, pred)
                        // linea despues de cada fila
                        val separator = View(this)
                        val params = TableRow.LayoutParams(
                            TableRow.LayoutParams.MATCH_PARENT,
                            1 // altura de la línea
                        )
                        params.setMargins(8, 4, 8, 4) // margenes
                        separator.layoutParams = params
                        separator.setBackgroundColor(
                            ContextCompat.getColor(
                                this,
                                android.R.color.darker_gray
                            )
                        )
                        tabla.addView(separator) // espacio
                        contador++
                    }
                    
                }

            }
    }

    private fun nuevaFila(tabla: TableLayout, contador: Int, pred: Prediccion) {
        val fila = TableRow(this)

        // celdas
        val params = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(8, 16, 8, 16) // espacio entre celdas

        // fondo celdas
        if (contador%2 == 0){
            fila.setBackgroundColor(ContextCompat.getColor(this, R.color.blue1celdas))
        }
        else{
            fila.setBackgroundColor(ContextCompat.getColor(this, R.color.blue2celdas))
        }

        // contador para visulizar numero de predicciones
        val n = TextView(this)
        n.text = contador.toString()
        n.textSize = 14f
        n.setPadding(16, 24, 16, 24) // altura
        n.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        n.gravity = Gravity.CENTER  // centrar el texto horizontal y vertical
        n.textAlignment = View.TEXT_ALIGNMENT_CENTER
        n.layoutParams = params
        fila.addView(n)


        // edad
        val edad = TextView(this)
        edad.text = pred.edad
        edad.textSize = 16f
        edad.setPadding(16, 24, 16, 24)
        edad.gravity = Gravity.CENTER  // Centrar el texto horizontal y vertical
        edad.textAlignment = View.TEXT_ALIGNMENT_CENTER
        edad.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        edad.layoutParams = params
        fila.addView(edad)

        // femenino
        val femenino = TextView(this)
        femenino.text = pred.femenino
        femenino.textSize = 16f
        femenino.setPadding(16, 24, 16, 24)
        femenino.gravity = Gravity.CENTER  // Centrar el texto horizontal y vertical
        femenino.textAlignment = View.TEXT_ALIGNMENT_CENTER
        femenino.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        femenino.layoutParams = params
        fila.addView(femenino)

        // capnometria
        val capnometria = TextView(this)
        capnometria.text = pred.capnometria
        capnometria.textSize = 16f
        capnometria.setPadding(16, 24, 16, 24)
        capnometria.gravity = Gravity.CENTER  // Centrar el texto horizontal y vertical
        capnometria.textAlignment = View.TEXT_ALIGNMENT_CENTER
        capnometria.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        capnometria.layoutParams = params
        fila.addView(capnometria)

        // causa cardiaca
        val causa = TextView(this)
        causa.text = pred.causa_cardiaca
        causa.textSize = 16f
        causa.setPadding(16, 24, 16, 24)
        causa.gravity = Gravity.CENTER  // Centrar el texto horizontal y vertical
        causa.textAlignment = View.TEXT_ALIGNMENT_CENTER
        causa.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        causa.layoutParams = params
        fila.addView(causa)

        // cardiocompresión
        val cardiocomp = TextView(this)
        cardiocomp.text = pred.cardio_manual
        cardiocomp.textSize = 16f
        cardiocomp.setPadding(16, 24, 16, 24)
        cardiocomp.gravity = Gravity.CENTER  // Centrar el texto horizontal y vertical
        cardiocomp.textAlignment = View.TEXT_ALIGNMENT_CENTER
        cardiocomp.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        cardiocomp.layoutParams = params
        fila.addView(cardiocomp)

        // recuperación pulso
        val rec = TextView(this)
        rec.text = pred.rec_pulso
        rec.textSize = 16f
        rec.setPadding(16, 24, 16, 24)
        rec.gravity = Gravity.CENTER  // Centrar el texto horizontal y vertical
        rec.textAlignment = View.TEXT_ALIGNMENT_CENTER
        rec.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        rec.layoutParams = params
        fila.addView(rec)

        // resultado
        val resultado = TextView(this)
        resultado.text = pred.valido
        resultado.textSize = 16f
        resultado.setPadding(16, 24, 16, 24)
        resultado.gravity = Gravity.CENTER  // posicion en la celda
        resultado.textAlignment = View.TEXT_ALIGNMENT_CENTER
        resultado.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        resultado.layoutParams = params
        fila.addView(resultado)

        // icono informe
        val informe = ImageView(this)
        informe.isClickable = true
        informe.isFocusable = true // lo puede seleccionar
        informe.setImageResource(R.drawable.download)
        val params_icono = TableRow.LayoutParams(55, 55).apply {
            gravity = Gravity.CENTER
        } // altura y anchura icono + posicion en la celda
        informe.layoutParams = params_icono
        informe.setOnClickListener {
            generarPDF(
                pred.edad,
                pred.femenino,
                pred.capnometria,
                pred.causa_cardiaca,
                pred.cardio_manual,
                pred.rec_pulso,
                name?: "Desconocido",
                pred.fecha,
                pred.valido
            )
        }
        fila.addView(informe)

        // icono eliminar
        val eliminar = ImageView(this)
        eliminar.isClickable = true
        eliminar.isFocusable = true
        eliminar.setImageResource(R.drawable.basuraic)
        val params_icono_eliminar = TableRow.LayoutParams(55, 55).apply {
            gravity = Gravity.CENTER
        } // altura y anchura icono + posicion en la celda
        eliminar.layoutParams = params_icono_eliminar
        eliminar.setOnClickListener {
            // dialogo de confirmacion
            val dialogo = AlertDialog.Builder(this)
                .setTitle("Confirmar eliminación")
                .setMessage("¿Está seguro de que quiere eliminar esta predicción?")
                .setPositiveButton("Si") { dialogInterface, _ ->
                    eliminarPrediccion(pred)
                    dialogInterface.dismiss()
                }
                .setNegativeButton("No") { dialogInterface, _ ->
                    dialogInterface.dismiss() // cerrar dialogo
                }
                .create()
            dialogo.show()
        }
        fila.addView(eliminar)


        // añadir fila a la tabla
        tabla.addView(fila)
    }

    private fun generarPDF(
        edad: String, femenino: String, capnometria: String, causa_cardiaca: String,
        cardio_manual: String, recuperacionPulso: String, name: String, fecha: Timestamp, valido: String
    ) {
        try {
            // creo el archivo PDF en el almacenamiento privado
            val fileName = "Reporte_${fecha}.pdf" // nombre con fecha para que sea unico
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) // abrir directamente siempre "Descargas"
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    val pdfDocument = PdfDocument(PdfWriter(outputStream))
                    val document = Document(pdfDocument, PageSize.A4)

                    // titulo
                    val title = Paragraph("RESULTADOS PREDICCIÓN DONANTE DE RIÑÓN")
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(16f)
                    document.add(title)

                    // informacion del medico y fecha
                    val fechaLegible = getFormattedDate(fecha)
                    val doctorInfo = Paragraph("Nombre médico: $name\nFecha: $fechaLegible")
                        .setTextAlignment(TextAlignment.LEFT)
                        .setFontSize(12f)
                    document.add(doctorInfo)
                    document.add(Paragraph("\n"))

                    // datos del posible donante
                    val donorInfo = Paragraph("DATOS POSIBLE DONANTE:")
                        .setBold()
                        .setTextAlignment(TextAlignment.LEFT)
                        .setFontSize(14f)
                    document.add(donorInfo)

                    document.add(Paragraph("Edad: $edad").setTextAlignment(TextAlignment.LEFT))
                    document.add(
                        Paragraph("Sexo femenino: $femenino").setTextAlignment(
                            TextAlignment.LEFT))
                    document.add(
                        Paragraph("Capnometría: $capnometria").setTextAlignment(
                            TextAlignment.LEFT))
                    document.add(
                        Paragraph("Causa cardiaca: $causa_cardiaca").setTextAlignment(
                            TextAlignment.LEFT))
                    document.add(
                        Paragraph("Cardiocompresión extrahospitalaria manual: $cardio_manual").setTextAlignment(
                            TextAlignment.LEFT))
                    document.add(
                        Paragraph("Recuperación circulación: $recuperacionPulso").setTextAlignment(
                            TextAlignment.LEFT))

                    // resultado prediccion
                    if(valido == "Si"){
                        val prediction = Paragraph("PREDICCIÓN: VÁLIDO")
                            .setBold()
                            .setTextAlignment(TextAlignment.LEFT)
                            .setFontSize(12f)
                        document.add(prediction)
                    }
                    else{
                        val prediction = Paragraph("PREDICCIÓN: NO VÁLIDO")
                            .setBold()
                            .setTextAlignment(TextAlignment.LEFT)
                            .setFontSize(12f)
                        document.add(prediction)
                    }
                    // cerrar el documento
                    document.close()
                    Toast.makeText(this, "PDF guardado en Descargas", Toast.LENGTH_SHORT).show()
                    abrirPDF(it) // abre automáticamente el PDF después de guardarlo
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
        return sdf.format(timestamp.toDate()) // dar formato legible
    }

    private fun eliminarPrediccion(pred: Prediccion){ // no podemos acceder el id de la prediccion, asi que primero lo buscamos y luego lo eliminamos
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
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) { // si se encuentra la prediccion
                    for (document in querySnapshot) {
                        val id_prediccion = document.id
                        // eliminar prediccion
                        db.collection("predicciones").document(id_prediccion).delete()
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    "Predicción eliminada correctamente",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this,
                                    "No se ha podido eliminar la predicción",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
            }
        // disminuir contadores
        if(pred.valido == "Si"){
            actualizarContadorPredicciones(true)
        }
        else{
            actualizarContadorPredicciones(false)
        }
    }

    private fun actualizarContadorPredicciones(valido: Boolean){
        var updates = mutableMapOf<String, Any>()
        if(valido){
            updates["numeroPredicciones"] = FieldValue.increment(-1)
            updates["predicciones_validas"] = FieldValue.increment(-1)
        }
        else{
            updates["numeroPredicciones"] = FieldValue.increment(-1)
            updates["predicciones_no_validas"] = FieldValue.increment(-1)
        }
        userUid?.let { uid ->
            val userRef = db.collection("users").document(uid)
            userRef.update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Contadores actualizados", Toast.LENGTH_SHORT).show()
                    // cargo de nuevo el historial
                    val intent = Intent(this, HistorialActivity::class.java)
                    intent.putExtra("userUid", userUid)
                    intent.putExtra("userName", name)
                    startActivity(intent)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al actualizar los contadores", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun generarCSV(){
        db.collection("predicciones").whereEqualTo("uid_medico", userUid)
            .get()
            .addOnCompleteListener { firestoreTask ->
                if (firestoreTask.isSuccessful) {
                    val predicciones = firestoreTask.result
                    val listaPredicciones = mutableListOf<Prediccion>()

                    for (aux in predicciones!!) {
                        val pred = aux.toObject(Prediccion::class.java) // paso a objeto Prediccion para que sea mas sencillo trabajar con el
                        listaPredicciones.add(pred)
                    }

                    try{
                        val fileName = "historialPredicciones_${name}_${System.currentTimeMillis()}.csv" // nombre unico con nombre medico y fecha actual
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) // abrir directamente siempre "Descargas"
                        }
                        val resolver = contentResolver // acceder al sistema de archivos
                        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues) // crear archivo

                        uri?.let {
                            resolver.openOutputStream(it)?.use { outputStream ->
                                val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)

                                // encabezado en la primera fila
                                writer.write("Edad,Femenino,Capnometria,Causa_cardiaca,Cardio_manual,Recuperacion_pulso,Valido,UID_medico,Fecha\n")

                                // contenido
                                for (pred in listaPredicciones) {
                                    val row = listOf(
                                        pred.edad,
                                        pred.femenino,
                                        pred.capnometria,
                                        pred.causa_cardiaca,
                                        pred.cardio_manual,
                                        pred.rec_pulso,
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
    }

    private fun abrirArchivoCSV(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "text/csv")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay aplicación para abrir archivos CSV", Toast.LENGTH_SHORT).show()
        }
    }

}