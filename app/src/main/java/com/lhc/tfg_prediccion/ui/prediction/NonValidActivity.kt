package com.lhc.tfg_prediccion.ui.prediction

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityNonvalidBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity
import com.lhc.tfg_prediccion.util.PdfPrediction
import com.lhc.tfg_prediccion.util.UserUtils
import com.lhc.tfg_prediccion.util.generatePredictionPdf

class NonValidActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNonvalidBinding
    private var fullName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNonvalidBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appear = AnimationUtils.loadAnimation(this, R.anim.result_appear)
        binding.rootResult.startAnimation(appear)

        val edad              = intent.getStringExtra("edad") ?: ""
        val auxFem            = intent.getStringExtra("fem") ?: ""
        val capnometria       = intent.getStringExtra("capnometria") ?: ""
        val causa_cardiaca    = intent.getStringExtra("causa_cardiaca") ?: ""
        val cardio_manual     = intent.getStringExtra("cardio_manual") ?: ""
        val recuperacionPulso = intent.getStringExtra("rec_pulso") ?: ""
        val userUid           = intent.getStringExtra("userUid") ?: ""
        val name              = intent.getStringExtra("username") ?: "Desconocido"
        val modeCode          = intent.getStringExtra("prediction_mode") ?: MODE_AFTER
        val docId             = intent.getStringExtra("docId") ?: ""
        val indiceExtra       = intent.getDoubleExtra("indice", Double.NaN)
        val indice: Double?   = if (indiceExtra.isNaN()) null else indiceExtra

        UserUtils.cargarNombreCompleto(
            FirebaseFirestore.getInstance(),
            userUid,
            name
        ) { full -> fullName = full }

        val momentoCanonico = modeToLabel(modeCode)
        val femenino = if (auxFem == "Mujer") "Si" else "No"

        guardarPrediccion(
            edad,
            femenino,
            capnometria,
            causa_cardiaca,
            cardio_manual,
            recuperacionPulso,
            userUid,
            fullName ?: name,
            modeCode,
            indice,
            docId
        )

        binding.buttonBackToMenu.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("userUid", userUid)
                putExtra("userName", fullName ?: name)
            })
        }

        binding.downloadPDF.setOnClickListener {

            val pdfData = PdfPrediction(
                doctorName      = (fullName ?: name),
                fecha           = Timestamp.now(),
                predictionMode  = modeCode,
                momentoCanonico = momentoCanonico,
                edad            = edad,
                femenino        = femenino,
                capnometria     = capnometria,
                causaCardiaca   = causa_cardiaca,
                cardioManual    = cardio_manual,
                recPulso        = recuperacionPulso,
                valido          = false,
                indice          = indice
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
        nameOrFullName: String,
        predictionMode: String,
        indice: Double?,
        docId: String
    ) {

        if (docId.isBlank()) {
            Toast.makeText(this, "Error: docId vacío", Toast.LENGTH_SHORT).show()
            return
        }

        val fecha = Timestamp.now()
        val db = FirebaseFirestore.getInstance()

        val prediccion = hashMapOf<String, Any>(
            "nombre_medico" to nameOrFullName,
            "uid_medico" to userUid,
            "edad" to edad,
            "femenino" to femenino,
            "capnometria" to capnometria,
            "causa_cardiaca" to causa_cardiaca,
            "cardio_manual" to cardio_manual,
            "rec_pulso" to recuperacionPulso,
            "fecha" to fecha,
            "valido" to "No",
            "prediction_mode" to predictionMode,
            "momento_prediccion_legible" to modeToLabel(predictionMode),
            "modelos" to mutableListOf<String>(),
            "no_modelos" to mutableListOf("Modelo1","Modelo2","Modelo3","Modelo4")
        )

        indice?.let { prediccion["indice"] = it }

        val docRef = db.collection("predicciones").document(docId)

        db.runTransaction { tx ->
            val snap = tx.get(docRef)

            if (snap.exists()) {
                throw FirebaseFirestoreException(
                    "ALREADY_EXISTS",
                    FirebaseFirestoreException.Code.ALREADY_EXISTS
                )
            }

            tx.set(docRef, prediccion)
            null
        }
            .addOnSuccessListener {
                incrementarContadoresUsuario(userUid)
                Toast.makeText(this, "Predicción guardada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->

                val fe = e as? FirebaseFirestoreException

                if (fe?.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
                    Toast.makeText(this, "Predicción duplicada: no se guarda", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun incrementarContadoresUsuario(uid: String) {

        val db = FirebaseFirestore.getInstance()

        val inc = hashMapOf<String, Any>(
            "numeroPredicciones" to FieldValue.increment(1),
            "predicciones_no_validas" to FieldValue.increment(1)
        )

        db.collection("users").document(uid).set(inc, SetOptions.merge())
    }
}