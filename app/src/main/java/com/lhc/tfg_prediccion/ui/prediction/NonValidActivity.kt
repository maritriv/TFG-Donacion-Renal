package com.lhc.tfg_prediccion.ui.prediction

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lhc.tfg_prediccion.databinding.ActivityNonvalidBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.Document
import com.itextpdf.layout.properties.TextAlignment

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.lhc.tfg_prediccion.R
import java.text.SimpleDateFormat
import java.util.Locale


class NonValidActivity: AppCompatActivity() {
    private lateinit var binding: ActivityNonvalidBinding
    private var fecha = Timestamp.now() // cojo fecha para incluirla en la base de datos y en el PDF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNonvalidBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // cargo texto dinamico usando binding
        val scaleAnimation: Animation = AnimationUtils.loadAnimation(this, R.anim.donantenovalido)
        binding.textoDonanteNoValido.startAnimation(scaleAnimation)


        // cojo valores del intent
        val edad = intent.getStringExtra("edad")
        val auxFem = intent.getStringExtra("fem")
        val capnometria = intent.getStringExtra("capnometria")
        val causa_cardiaca = intent.getStringExtra("causa_cardiaca")
        val cardio_manual = intent.getStringExtra("cardio_manual")
        val recuperacionPulso = intent.getStringExtra("rec_pulso")
        val userUid = intent.getStringExtra("userUid")
        val name = intent.getStringExtra("username")

        // cambio a si o no el valor femenino
        val femenino: String
        if(auxFem == "Mujer"){
            femenino = "Si"
        }
        else{
            femenino = "No"
        }

        // guardo la prediccion en la base de datos
        guardarPrediccion(edad!!, femenino, capnometria!!, causa_cardiaca!!, cardio_manual!!, recuperacionPulso!!, userUid!!, name!!)

        // boton volver al menu principal
        binding.buttonBackToMenu.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("edad", edad)
            intent.putExtra("fem", femenino)
            intent.putExtra("capnometria", capnometria)
            intent.putExtra("causa_cardiaca", causa_cardiaca)
            intent.putExtra("cardio_manual", cardio_manual)
            intent.putExtra("rec_pulso", recuperacionPulso)
            intent.putExtra("userUid", userUid)
            intent.putExtra("userName", name)
            startActivity(intent)
        }

        // boton descargar PDF con datos de la prediccion
        binding.downloadPDF.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
                Toast.makeText(this, "Necesita una versión Android igual o superior a 10", Toast.LENGTH_SHORT).show()
            }
            else{
                generarPDF(edad, femenino, capnometria, causa_cardiaca, cardio_manual, recuperacionPulso, name)
            }
        }
    }

    private fun guardarPrediccion(
        edad: String, femenino: String, capnometria: String, causa_cardiaca: String,
        cardio_manual: String, recuperacionPulso: String, userUid: String, name: String
    ){
        val db = FirebaseFirestore.getInstance()
        val prediccion = hashMapOf(
            "nombre_medico" to name,
            "uid_medico" to userUid,
            "edad" to edad,
            "femenino" to femenino,
            "capnometria" to capnometria,
            "causa_cardiaca" to causa_cardiaca,
            "cardio_manual" to cardio_manual,
            "rec_pulso" to recuperacionPulso,
            "fecha" to fecha,
            "valido" to "No",
            "modelos" to mutableListOf<String>(), // lista vacia para añadir los modelos de IA
            "no_modelos" to mutableListOf("Modelo1", "Modelo2", "Modelo3", "Modelo4") // lista de los modelos en los que NO esta la prediccion
        )

        db.collection("predicciones")
            .add(prediccion)
            .addOnSuccessListener {
                Toast.makeText(this, "Predicción guardada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar en Firestore", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generarPDF(
        edad: String, femenino: String, capnometria: String, causa_cardiaca: String,
        cardio_manual: String, recuperacionPulso: String, name: String
    ) {
        try {
            // Crear el archivo PDF en el almacenamiento privado
            val fileName = "Reporte_${fecha}.pdf" // nombre único del archivo
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) // Siempre en "Descargas"
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    val pdfDocument = PdfDocument(PdfWriter(outputStream))
                    val document = Document(pdfDocument, PageSize.A4)

                    // Título
                    val title = Paragraph("RESULTADOS PREDICCIÓN DONANTE DE RIÑÓN")
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(16f)
                    document.add(title)

                    // Información del médico y fecha
                    val fechaLegible = getFormattedDate(fecha)
                    val doctorInfo = Paragraph("Nombre médico: $name\nFecha: $fechaLegible")
                        .setTextAlignment(TextAlignment.LEFT)
                        .setFontSize(12f)
                    document.add(doctorInfo)
                    document.add(Paragraph("\n"))

                    // Datos del posible donante
                    val donorInfo = Paragraph("DATOS POSIBLE DONANTE:")
                        .setBold()
                        .setTextAlignment(TextAlignment.LEFT)
                        .setFontSize(14f)
                    document.add(donorInfo)

                    document.add(Paragraph("Edad: $edad").setTextAlignment(TextAlignment.LEFT))
                    document.add(Paragraph("Sexo femenino: $femenino").setTextAlignment(TextAlignment.LEFT))
                    document.add(Paragraph("Capnometría: $capnometria").setTextAlignment(TextAlignment.LEFT))
                    document.add(Paragraph("Causa cardiaca: $causa_cardiaca").setTextAlignment(TextAlignment.LEFT))
                    document.add(Paragraph("Cardiocompresión extrahospitalaria manual: $cardio_manual").setTextAlignment(TextAlignment.LEFT))
                    document.add(Paragraph("Recuperación circulación: $recuperacionPulso").setTextAlignment(TextAlignment.LEFT))

                    // Resultado predicción
                    val prediction = Paragraph("PREDICCIÓN: NO VÁLIDO")
                        .setBold()
                        .setTextAlignment(TextAlignment.LEFT)
                        .setFontSize(12f)
                    document.add(prediction)

                    // Cerrar el documento
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

}