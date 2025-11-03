package com.lhc.tfg_prediccion.ui.prediction

import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityValidBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity

// Utilidades comunes
import com.lhc.tfg_prediccion.util.PdfPrediction
import com.lhc.tfg_prediccion.util.generatePredictionPdf
import com.lhc.tfg_prediccion.util.UserUtils

// Frases canónicas para el “Momento”
import com.lhc.tfg_prediccion.ui.prediction.modeToLabel

class ValidActivity : AppCompatActivity() {

    private lateinit var binding: ActivityValidBinding

    // 💡 Necesaria para guardar/mostrar nombre + apellidos
    private var fullName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityValidBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animación
        val scaleAnimation: Animation = AnimationUtils.loadAnimation(this, R.anim.donantevalido)
        binding.textoDonantValido.startAnimation(scaleAnimation)

        // Valores del intent
        val edad              = intent.getStringExtra("edad") ?: ""
        val auxFem            = intent.getStringExtra("fem") ?: ""
        val capnometria       = intent.getStringExtra("capnometria") ?: ""
        val causa_cardiaca    = intent.getStringExtra("causa_cardiaca") ?: ""
        val cardio_manual     = intent.getStringExtra("cardio_manual") ?: ""
        val recuperacionPulso = intent.getStringExtra("rec_pulso") ?: ""
        val userUid           = intent.getStringExtra("userUid") ?: ""
        val name              = intent.getStringExtra("username") ?: "Desconocido"

        // Cargar nombre completo de Firestore (asíncrono, con fallback al name del intent)
        UserUtils.cargarNombreCompleto(
            FirebaseFirestore.getInstance(),
            userUid,
            name
        ) { full -> fullName = full }

        val modeCode    = intent.getStringExtra("prediction_mode") ?: "AFTER_RCP"
        val indiceExtra = intent.getDoubleExtra("indice", Double.NaN)
        val indice: Double? = if (indiceExtra.isNaN()) null else indiceExtra

        // Momento canónico y femenino normalizado
        val momentoCanonico = modeToLabel(modeCode)
        val femenino = if (auxFem == "Mujer") "Si" else "No"

        // Guardar predicción en Firestore
        guardarPrediccion(
            edad, femenino, capnometria, causa_cardiaca,
            cardio_manual, recuperacionPulso, userUid, (fullName ?: name),
            modeCode, indice
        )

        // Volver a menú
        binding.buttonBackToMenu.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("edad", edad)
                putExtra("fem", femenino)
                putExtra("capnometria", capnometria)
                putExtra("causa_cardiaca", causa_cardiaca)
                putExtra("cardio_manual", cardio_manual)
                putExtra("rec_pulso", recuperacionPulso)
                putExtra("userUid", userUid)
                putExtra("userName", fullName ?: name) // ← pasa nombre completo si lo tenemos
            })
        }

        // Descargar PDF (usa nombre completo si ya está cargado; si no, usa 'name')
        binding.downloadPDF.setOnClickListener {
            val pdfData = PdfPrediction(
                doctorName = (fullName ?: name),
                fecha = Timestamp.now(),
                momentoCanonico = momentoCanonico,
                edad = edad,
                femenino = femenino,
                capnometria = capnometria,
                causaCardiaca = causa_cardiaca,
                cardioManual = cardio_manual,
                recPulso = recuperacionPulso,
                valido = true,
                indice = indice
            )
            generatePredictionPdf(this, pdfData)
        }
    }

    private fun guardarPrediccion(
        edad: String,
        femenino: String,
        capnometria: String,
        causa_cardiaca: String,
        cardio_manual: String,
        recuperacionPulso: String,
        userUid: String,
        nameOrFullName: String,  // ← ahora aceptamos nombre completo si está
        predictionMode: String,
        indice: Double?
    ) {
        val fecha = Timestamp.now()
        val db = FirebaseFirestore.getInstance()
        val prediccion = hashMapOf(
            "nombre_medico" to nameOrFullName, // ← guardamos nombre completo
            "uid_medico" to userUid,
            "edad" to edad,
            "femenino" to femenino,
            "capnometria" to capnometria,
            "causa_cardiaca" to causa_cardiaca,
            "cardio_manual" to cardio_manual,
            "rec_pulso" to recuperacionPulso,
            "fecha" to fecha,
            "valido" to "Si",
            "prediction_mode" to predictionMode,
            "momento_prediccion_legible" to modeToLabel(predictionMode),
            "modelos" to mutableListOf<String>(),
            "no_modelos" to mutableListOf("Modelo1", "Modelo2", "Modelo3", "Modelo4")
        )

        indice?.let { prediccion["indice"] = it }

        db.collection("predicciones")
            .add(prediccion)
            .addOnSuccessListener {
                Toast.makeText(this, "Predicción guardada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar en Firestore", Toast.LENGTH_SHORT).show()
            }
    }
}