package com.lhc.tfg_prediccion.ui.prediction

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.data.model.Prediccion
import com.lhc.tfg_prediccion.databinding.ActivityShowpredBinding
import com.lhc.tfg_prediccion.ui.main.MainAdmin

class ShowpredActivity: AppCompatActivity() {
    private lateinit var binding: ActivityShowpredBinding
    private var userUid: String? = null
    private var name: String? = null
    private var email: String? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShowpredBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // cojo valores intent
        name = intent.getStringExtra("name")
        email = intent.getStringExtra("email")
        userUid = intent.getStringExtra("userUid")

        // boton volver
        binding.buttonBackToMenu.setOnClickListener {
            val intent = Intent(this, MainAdmin::class.java)
            intent.putExtra("userUid", userUid)
            intent.putExtra("userName", name)
            startActivity(intent)
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

    private fun cargarHistorial(orden: String) {
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
        val encabezados = listOf("N", "Edad", "F", "Cap", "CauCard", "Cardiocomp", "RecP", "Res")
        for (titulo in encabezados) {
            val celda = TextView(this)
            celda.text = titulo
            celda.textSize = 16f
            celda.setPadding(16, 24, 16, 24)
            celda.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            celda.layoutParams = params
            filaEncabezado.addView(celda)
        }

        // Añadir encabezado a la tabla
        tabla.addView(filaEncabezado)

        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    for (document in documents) {
                        val uid = document.getString("uid")  // obtener uid medico para cargar las predicciones
                        db.collection("predicciones")
                            .whereEqualTo("uid_medico", uid)  // cojo solo las predicciones del medico
                            .get()
                            .addOnCompleteListener { firestoreTask ->
                                if (firestoreTask.isSuccessful) {
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
                }
            }
    }


    private fun nuevaFila(tabla: TableLayout, contador: Int, pred: Prediccion) {
        val fila = TableRow(this)

        // formato celdas
        val params = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(8, 16, 8, 16) // Espaciado entre celdas

        // fondo celdas
        if (contador%2 == 0){
            fila.setBackgroundColor(ContextCompat.getColor(this, R.color.blue1celdas))
        }
        else{
            fila.setBackgroundColor(ContextCompat.getColor(this, R.color.blue2celdas))
        }

        // n
        val n = TextView(this)
        n.text = contador.toString()
        n.textSize = 14f
        n.setPadding(16, 24, 16, 24) // Ajuste de altura
        n.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        n.layoutParams = params
        fila.addView(n)

        // edad
        val edad = TextView(this)
        edad.text = pred.edad
        edad.textSize = 14f
        edad.setPadding(16, 24, 16, 24)
        edad.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        edad.layoutParams = params
        fila.addView(edad)

        // femenino
        val femenino = TextView(this)
        femenino.text = pred.femenino
        femenino.textSize = 14f
        femenino.setPadding(16, 24, 16, 24)
        femenino.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        femenino.layoutParams = params
        fila.addView(femenino)

        // capnometria
        val capnometria = TextView(this)
        capnometria.text = pred.capnometria
        capnometria.textSize = 14f
        capnometria.setPadding(16, 24, 16, 24)
        capnometria.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        capnometria.layoutParams = params
        fila.addView(capnometria)

        // causa cardiaca
        val causa = TextView(this)
        causa.text = pred.causa_cardiaca
        causa.textSize = 14f
        causa.setPadding(16, 24, 16, 24)
        causa.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        causa.layoutParams = params
        fila.addView(causa)

        // cardiocompresión
        val cardiocomp = TextView(this)
        cardiocomp.text = pred.cardio_manual
        cardiocomp.textSize = 14f
        cardiocomp.setPadding(16, 24, 16, 24)
        cardiocomp.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        cardiocomp.layoutParams = params
        fila.addView(cardiocomp)

        // recuperación pulso
        val rec = TextView(this)
        rec.text = pred.rec_pulso
        rec.textSize = 14f
        rec.setPadding(16, 24, 16, 24)
        rec.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        rec.layoutParams = params
        fila.addView(rec)

        // resultado
        val resultado = TextView(this)
        resultado.text = pred.valido
        resultado.textSize = 14f
        resultado.setPadding(16, 24, 16, 24)
        resultado.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        resultado.layoutParams = params
        fila.addView(resultado)

        // Añadir fila completa a la tabla
        tabla.addView(fila)
    }
}