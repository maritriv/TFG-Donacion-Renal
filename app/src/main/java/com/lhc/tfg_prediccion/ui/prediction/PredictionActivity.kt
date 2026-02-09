package com.lhc.tfg_prediccion.ui.prediction

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lhc.tfg_prediccion.databinding.ActivityPredictionBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity


class PredictionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictionBinding
    private var name: String? = null
    private var userUid: String? = null
    private var mode: String = MODE_AFTER   // por defecto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Modo que llega desde la pantalla de selección
        mode = intent.getStringExtra(PredictionModeActivity.EXTRA_MODE) ?: MODE_AFTER
        Log.d("PRED", "mode recibido = $mode")
        val textoModo = when (mode) {
            MODE_BEFORE -> "Antes de la RCP"
            MODE_MID -> "Durante la RCP (20 min)"
            else -> "Después de la RCP"
        }
        Toast.makeText(this, "Modo seleccionado: $textoModo", Toast.LENGTH_SHORT).show()

        // Ajustar el hint de capnografía según modo
        val capnoLabel = when (mode) {
            MODE_BEFORE -> "Capnometría (inicio)"
            MODE_MID    -> "Capnometría (punto medio)"
            else        -> "Capnometría (transferencia)"
        }
        binding.tilCapnometria.hint = capnoLabel

        // Spinners
        val opcionesSiNo = arrayOf("Si", "No")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSiNo)
        binding.spCausaCardiaca.adapter = adapter
        binding.spRecuperacion.adapter = adapter

        // Spinner de tipo de cardiocompresión: Mecánica / Manual
        val opcionesCardio = arrayOf("Mecánica", "Manual")
        val adapterCardio = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesCardio)
        binding.spCardioManual.adapter = adapterCardio

        val opcionesSexo = arrayOf("Hombre", "Mujer")
        val adapterSexo = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSexo)
        binding.spSexoFemenino.adapter = adapterSexo

        // Datos del médico
        name = intent.getStringExtra("userName")
        userUid = intent.getStringExtra("userUid")

        // Botón volver
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, PredictionModeActivity::class.java)
            intent.putExtra("userName", name)
            intent.putExtra("userUid", userUid)
            startActivity(intent)
            finish() // opcional: evita que al pulsar atrás vuelva al formulario
        }

        // Botón realizar predicción
        binding.btnPredic.setOnClickListener {
            val edad = binding.etEdad.text.toString().toIntOrNull()
            val femenino = binding.spSexoFemenino.selectedItem.toString()
            val capnometria = binding.etCapnometria.text.toString().toIntOrNull()
            val causacardiaca = binding.spCausaCardiaca.selectedItem.toString()
            val cardioManual = binding.spCardioManual.selectedItem.toString()
            val recuperacionPulso = binding.spRecuperacion.selectedItem.toString()

            if (edad == null || capnometria == null) {
                Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
            } else {
                calcularResultado(
                    mode = mode,
                    edad = edad,
                    femenino = femenino,
                    capnometria = capnometria,
                    causacardiaca = causacardiaca,
                    cardiomanual = cardioManual,
                    recuperacionPulso = recuperacionPulso,
                    tiempoMin = 0 // si luego añades este campo, pásalo aquí
                )
            }
        }

        Log.d("PRED", "extras -> name=${intent.getStringExtra("userName")} uid=${intent.getStringExtra("userUid")}")
        Log.d("PRED", "asignado -> name=$name userUid=$userUid")
    }

    private fun calcularResultado(
        mode: String,
        edad: Int,
        femenino: String,
        capnometria: Int,
        causacardiaca: String,
        cardiomanual: String,
        recuperacionPulso: String,
        tiempoMin: Int = 0
    ) {
        // 1) Pasamos los campos de la UI a binarios 0/1
        val femBin    = if (femenino == "Mujer") 1 else 0
        val causaBin  = if (causacardiaca == "Si") 1 else 0
        val cardioBin = if (cardiomanual == "Manual") 1 else 0
        val recBin    = if (recuperacionPulso == "Si") 1 else 0

        // 2) Llamamos al motor común (coefs + cálculo + corte)
        val (esValido, indice) = calcularIndiceYValidez(
            mode = mode,
            edad = edad,
            femenino = femBin,
            capnometria = capnometria,
            causacardiaca = causaBin,
            cardiomanual = cardioBin,
            recuperacion = recBin,
            tiempoMin = tiempoMin
        )

        Log.d("PRED", "calc[$mode] -> idx=$indice esValido=$esValido")

        // 3) Flujo igual que tenías, pero pasando también 'indice' y 'prediction_mode'
        if (esValido) {
            actualizarNumeroPredicciones(true) {
                Toast.makeText(this, "DONANTE VALIDO", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ValidActivity::class.java).apply {
                    putExtra("prediction_mode", mode)
                    putExtra("indice", indice)
                    putExtra("edad", edad.toString())
                    putExtra("fem", femenino)
                    putExtra("capnometria", capnometria.toString())
                    putExtra("causa_cardiaca", causacardiaca)
                    putExtra("cardio_manual", cardiomanual)
                    putExtra("rec_pulso", recuperacionPulso)
                    putExtra("username", name)
                    putExtra("userUid", userUid)
                }
                startActivity(intent)
            }
        } else {
            actualizarNumeroPredicciones(false) {
                Toast.makeText(this, "DONANTE NO VALIDO", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, NonValidActivity::class.java).apply {
                    putExtra("prediction_mode", mode)
                    putExtra("indice", indice)
                    putExtra("edad", edad.toString())
                    putExtra("fem", femenino)
                    putExtra("capnometria", capnometria.toString())
                    putExtra("causa_cardiaca", causacardiaca)
                    putExtra("cardio_manual", cardiomanual)
                    putExtra("rec_pulso", recuperacionPulso)
                    putExtra("username", name)
                    putExtra("userUid", userUid)
                }
                startActivity(intent)
            }
        }
    }

    private fun actualizarNumeroPredicciones(esValido: Boolean, callback: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        userUid?.let { uid ->
            val userRef = db.collection("users").document(uid)
            userRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    // Incrementar contadores
                    userRef.update("numeroPredicciones", (document.getLong("numeroPredicciones") ?: 0) + 1)
                    if (esValido) {
                        userRef.update("predicciones_validas", (document.getLong("predicciones_validas") ?: 0) + 1)
                    } else {
                        userRef.update("predicciones_no_validas", (document.getLong("predicciones_no_validas") ?: 0) + 1)
                    }.addOnSuccessListener { callback() }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al actualizar predicciones: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // Crear campos si no existen
                    val initialData = mapOf(
                        "numeroPredicciones" to 1,
                        "predicciones_validas" to (if (esValido) 1 else 0),
                        "predicciones_no_validas" to (if (esValido) 0 else 1)
                    )
                    userRef.set(initialData, SetOptions.merge())
                        .addOnSuccessListener { callback() }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al crear los campos: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener el documento: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
