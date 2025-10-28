package com.lhc.tfg_prediccion.ui.prediction

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityImportBinding
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lhc.tfg_prediccion.ui.main.MainActivity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import com.lhc.tfg_prediccion.ui.prediction.MODE_AFTER
import com.lhc.tfg_prediccion.ui.prediction.calcularIndiceYValidez


class ImportActivity: AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding
    private var userUid: String? = null
    private var name: String? = null
    private var fecha = Timestamp.now()
    private var esValido: Boolean? = null
    private val importMode: String = MODE_AFTER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // cojo valores intent
        userUid = intent.getStringExtra("userUid")
        name = intent.getStringExtra("userName")

        // boton importar csv
        val import_button = findViewById<Button>(R.id.btn_importar_csv)
        import_button.setOnClickListener {
            seleccionCSVUsuario()
        }

        // boton volver
        val back_button = findViewById<Button>(R.id.btn_volver)
        back_button.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("userUid", userUid)
            intent.putExtra("userName", name)
            startActivity(intent)
        }
    }

    private fun seleccionCSVUsuario(){
        // intent de tipo GET CONTENT
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*" // cualquier archivo valido
        }
        selectorArchivos.launch(intent)
    }

    // propiedad que almacena
    // registerForActivityResult es una API de AndroidX
    private val selectorArchivos = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult() // lanza una actividad que espera un resultado
    ){ result -> // funcion lambda que se ejecuta cuando se obtiene un resultado
        if (result.resultCode == Activity.RESULT_OK){
            // toast para informar de que es correcto
            Toast.makeText(this, "CSV importado", Toast.LENGTH_SHORT).show()
            val data: Intent? = result.data
            // uri es la direccion que identifica el excel en el sistema
            val uri = data?.data
            if (uri != null){
                leerCSV(uri, this)
            }
        }
    }

    private fun leerCSV(uri: Uri, context: Context) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lista = mutableListOf<List<String>>() // lista para almacenar las filas del excel
            val barraProgreso = findViewById<ProgressBar>(R.id.barraProgreso)

            // numero de lineas para configurar la barra de progreso
            val lineas = reader.readLines()
            barraProgreso.max = lineas.size
            inputStream?.close()

            // lee cada linea y la añade a la lista
            lineas.forEach { linea ->
                val fila = linea.split(";") // divide cada fila por comas
                lista.add(fila)
            }

            // variables para gestionar el numero de predicciones y que la primera linea no se procese
            var primera = true
            var contadorPredicciones = 0
            var contadorValidas = 0
            var contadorNoValidas = 0

            // importo en la base de datos cada prediccion
            // importo en la base de datos cada prediccion
            lista.forEachIndexed { index, fila ->
                if (fila.size == 6) { // 6 valores iguales que los del formulario
                    if (!primera) { // la primera linea (cabecera) no se procesa

                        val edad            = fila[0].toIntOrNull()
                        val capno           = fila[1].toIntOrNull()
                        val fem             = fila[2].toIntOrNull()   // 0/1 en CSV
                        val cardio          = fila[3].toIntOrNull()   // 0/1
                        val rec             = fila[4].toIntOrNull()   // 0/1
                        val causa           = fila[5].toIntOrNull()   // 0/1

                        // Validación básica de nulos
                        if (edad == null || capno == null || fem == null || cardio == null || rec == null || causa == null) {
                            Toast.makeText(this, "Fila ${index + 1}: valores inválidos", Toast.LENGTH_SHORT).show()
                        } else {
                            // 1) Calcular índice y validez con el "motor" común
                            val (valido, indice) = calcularIndiceYValidez(
                                mode = importMode,     // AFTER_RCP fijo en importación
                                edad = edad,
                                femenino = fem,        // 0/1
                                capnometria = capno,
                                causacardiaca = causa, // 0/1
                                cardiomanual = cardio, // 0/1
                                recuperacion = rec,    // 0/1
                                tiempoMin = 0
                            )
                            esValido = valido

                            // 2) Actualizar contadores
                            contadorPredicciones++
                            if (valido) contadorValidas++ else contadorNoValidas++

                            // 3) Convertir a "Si"/"No" para guardarlo como antes
                            val femTxt   = if (fem == 1) "Si" else "No"
                            val cardioTxt= if (cardio == 1) "Si" else "No"
                            val recTxt   = if (rec == 1) "Si" else "No"
                            val causaTxt = if (causa == 1) "Si" else "No"

                            // 4) Guardar en Firestore con indice + prediction_mode
                            guardarPrediccion(
                                edad = edad.toString(),
                                femenino = femTxt,
                                capnometria = capno.toString(),
                                causa_cardiaca = causaTxt,
                                cardio_manual = cardioTxt,
                                recuperacionPulso = recTxt,
                                indice = indice,
                                mode = importMode
                            )
                        }

                        // Barra de progreso
                        runOnUiThread {
                            barraProgreso.progress = index + 1
                            binding.barraProgreso.progress = contadorPredicciones
                        }
                    }
                } else {
                    Toast.makeText(this, "Formato incorrecto. Se necesitan 6 columnas", Toast.LENGTH_SHORT).show()
                }
                primera = false
            }

            actualizarNumeroPredicciones(contadorPredicciones, contadorValidas, contadorNoValidas){
                Toast.makeText(this, "Actualizando número de predicciones...", Toast.LENGTH_SHORT).show()
            }

            runOnUiThread {
                binding.barraProgreso.visibility = View.GONE
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al leer el archivo CSV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actualizarNumeroPredicciones(numeroTotal: Int, numeroValidas: Int, numeroNoValidas: Int, callback: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        userUid?.let { uid ->
            val userRef = db.collection("users").document(uid)
            val updates = mapOf(
                    "numeroPredicciones" to FieldValue.increment(numeroTotal.toLong()),
                    "predicciones_validas" to FieldValue.increment(numeroValidas.toLong()),
                    "predicciones_no_validas" to FieldValue.increment(numeroNoValidas.toLong())
            )
            userRef.update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Contadores actualizados", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al actualizar los contadores", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun guardarPrediccion(
        edad: String, femenino: String, capnometria: String,
        causa_cardiaca: String, cardio_manual: String, recuperacionPulso: String,
        indice: Double, mode: String
    ){
        var valido: String
        if (esValido!!) valido = "Si"
        else valido = "No"

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
            "valido" to valido,
            "indice" to indice,
            "prediction_mode" to mode,
            "modelos" to mutableListOf<String>(), // lista vacia para añadir los modelos de IA
            "no_modelos" to mutableListOf("Modelo1", "Modelo2", "Modelo3", "Modelo4") // lista de los modelos en los que NO esta la prediccion
        )

        db.collection("predicciones")
            .add(prediccion)
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar en Firestore", Toast.LENGTH_SHORT).show()
            }
    }

}