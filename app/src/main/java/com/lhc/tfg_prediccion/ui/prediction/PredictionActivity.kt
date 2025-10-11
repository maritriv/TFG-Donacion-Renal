package com.lhc.tfg_prediccion.ui.prediction

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.lhc.tfg_prediccion.databinding.ActivityPredictionBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lhc.tfg_prediccion.ui.main.MainActivity

const val VALOR_FIJO = 7.5
const val COEFICIENTE_EDAD = -0.096
const val COEFICIENTE_SEXO_FEMENINO = -1.013
const val COEFICIENTE_CAPNOMETRIA = 0.08
const val COEFICIENTE_CAUSA_CARDIACA = -0.517
const val COEFICIENTE_CARDIOCOMPRESION = -2.616
const val COEFICIENTE_RECUPERACION_CIRCULACION = 2.943
const val CORTE = 4.366

class PredictionActivity: AppCompatActivity() {

    private lateinit var binding: ActivityPredictionBinding
    private var name: String? = null
    private var userUid: String?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // spinner de si o no
        val opcionesSiNo = arrayOf("Si", "No")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSiNo)
        binding.spCausaCardiaca.adapter = adapter
        binding.spCardioManual.adapter = adapter
        binding.spRecuperacion.adapter = adapter

        // spinner Mujer u Hombre
        val opcionesSexo = arrayOf("Hombre", "Mujer")
        val adapterSexo = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSexo)
        binding.spSexoFemenino.adapter = adapterSexo


        // cojo datos medico
        name = intent.getStringExtra("userName")
        userUid = intent.getStringExtra("userUid")

        // boton volver
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("userName", name)
            intent.putExtra("userUid", userUid)
            startActivity(intent)
        }

        // boton realizar prediccion
        binding.btnPredic.setOnClickListener {
            // comprobar que todos los campos estan llenos
            val edad = binding.etEdad.text.toString().toIntOrNull()
            val femenino = binding.spSexoFemenino.selectedItem.toString()
            val capnometria = binding.etCapnometria.text.toString().toIntOrNull()
            val causacardiaca = binding.spCausaCardiaca.selectedItem.toString()
            val cardioManual = binding.spCardioManual.selectedItem.toString()
            val recuperacionPulso = binding.spRecuperacion.selectedItem.toString()

            if (edad == null || capnometria == null){
                Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
            }
            else{
                calcularResultado(edad, femenino, capnometria, causacardiaca, cardioManual, recuperacionPulso)
            }
        }
        Log.d("PRED", "extras -> name=${intent.getStringExtra("userName")} uid=${intent.getStringExtra("userUid")}")
        Log.d("PRED", "asignado -> name=$name userUid=$userUid")
    }

    private fun calcularResultado(edad: Int, femenino: String, capnometria: Int, causacardiaca: String, cardiomanual: String,
                                  recuperacionPulso: String) {
        // paso strings a numero
        val fem = if (femenino == "Mujer") 1 else 0
        val causa_cardiaca = if (causacardiaca == "Si") 1 else 0
        val cardio_man = if (cardiomanual == "Si") 1 else 0  // cardiocompresion
        val rec = if(recuperacionPulso == "Si") 1 else 0

        // aumento contador numero predicciones del medico
        val resultado = VALOR_FIJO +
                (edad * COEFICIENTE_EDAD) +
                (fem * COEFICIENTE_SEXO_FEMENINO) +
                (capnometria * COEFICIENTE_CAPNOMETRIA) +
                (causa_cardiaca * COEFICIENTE_CAUSA_CARDIACA) +
                (cardio_man * COEFICIENTE_CARDIOCOMPRESION) +
                (rec * COEFICIENTE_RECUPERACION_CIRCULACION)

        val esValido = resultado >= CORTE
        Log.d("PRED", "calc -> resultado=$resultado corte=$CORTE esValido=$esValido")
        if(esValido) {
            actualizarNumeroPredicciones(esValido) {
                Toast.makeText(this, "DONANTE VALIDO", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ValidActivity::class.java)
                intent.putExtra("edad", edad.toString())
                intent.putExtra("fem", femenino)
                intent.putExtra("capnometria", capnometria.toString())
                intent.putExtra("causa_cardiaca", causacardiaca)
                intent.putExtra("cardio_manual", cardiomanual)
                intent.putExtra("rec_pulso", recuperacionPulso)
                intent.putExtra("username", name)
                intent.putExtra("userUid", userUid) // para identificar al medico que hace la prediccion
                startActivity(intent)
            }
        }
        else{
            actualizarNumeroPredicciones(esValido) {
                Toast.makeText(this, "DONANTE NO VALIDO", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, NonValidActivity::class.java)
                intent.putExtra("edad", edad.toString())
                intent.putExtra("fem", femenino)
                intent.putExtra("capnometria", capnometria.toString())
                intent.putExtra("causa_cardiaca", causacardiaca)
                intent.putExtra("cardio_manual", cardiomanual)
                intent.putExtra("rec_pulso", recuperacionPulso)
                intent.putExtra("username", name)
                intent.putExtra("userUid", userUid) // para identificar al medico que hace la prediccion
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
                    // Incrementar en 1 el campo 'numeroPredicciones'
                    userRef.update("numeroPredicciones", (document.getLong("numeroPredicciones") ?: 0) + 1)
                    // Actualizar los contadores de predicciones válidas o no válidas
                    if (esValido) {
                        // Incrementar predicciones válidas
                        userRef.update("predicciones_validas", (document.getLong("predicciones_validas") ?: 0) + 1)
                    } else {
                        // Incrementar predicciones no válidas
                        userRef.update("predicciones_no_validas", (document.getLong("predicciones_no_validas") ?: 0) + 1)
                    }
                        .addOnSuccessListener {
                            // Llamamos al callback después de actualizar el número de predicciones
                            callback()
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Error al actualizar predicciones: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // Si el usuario no tiene esos campos, lo creamos con valores predeterminados
                    val initialData = mapOf(
                        "numeroPredicciones" to 1,
                        "predicciones_validas" to (if (esValido) 1 else 0),
                        "predicciones_no_validas" to (if (esValido) 0 else 1)
                    )

                    userRef.set(initialData, SetOptions.merge())
                        .addOnSuccessListener {
                            callback() // Llamar al callback para continuar
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Error al crear los campos: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener el documento: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}