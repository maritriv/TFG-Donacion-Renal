package com.lhc.tfg_prediccion.ui.main

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityMainBinding
import com.lhc.tfg_prediccion.ui.edit.EditProfileActivity
import com.lhc.tfg_prediccion.ui.login.LoginActivity
import com.lhc.tfg_prediccion.ui.prediction.PredictionModeActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.ui.historial.HistorialActivity
// grafica pie chart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.lhc.tfg_prediccion.ui.prediction.ImportActivity

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawer: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private var userUid: String? = null
    private var name: String? = null
    private lateinit var binding: ActivityMainBinding
    private val db = FirebaseFirestore.getInstance()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // menu desplegable
        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        drawer = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(
            this,
            drawer,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        // nombre que se pasa con el intent
        name = intent.getStringExtra("userName")
        userUid = intent.getStringExtra("userUid")
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val headerView = navigationView.getHeaderView(0)
        val aux = headerView.findViewById<TextView>(R.id.nav_header_textView)
        aux.text = "$name"
        val nombreBienvenida = findViewById<TextView>(R.id.title_main)
        nombreBienvenida.text = "Bienvenid@ $name"
        navigationView.setNavigationItemSelectedListener(this)

        // numero predicciones
        val num_pred = findViewById<TextView>(R.id.prediction_count)
        getNumeroPredicciones { n ->
            num_pred.text = "NÚMERO DE PREDICCIONES: $n"
        }

        // -----------   pie chart   --------------------------------------------------
        val pieChart = findViewById<PieChart>(R.id.pie_chart)
        // obtengo datos de la base de datos Firestore
        val userUidNotNull = userUid ?: "defaultUserId"
        db.collection("users").document(userUidNotNull).get()
            .addOnSuccessListener { result ->
                if (result.exists()) {
                    val prediccionesValidas = result.getLong("predicciones_validas")?.toFloat() ?: 0f
                    val prediccionesNoValidas =
                        result.getLong("predicciones_no_validas")?.toFloat() ?: 0f

                    // configurar el grafico con estos valores
                    configurarPieChart(pieChart, prediccionesValidas, prediccionesNoValidas)
                }
                else{
                    Toast.makeText(this, "Error al crear el gráfico", Toast.LENGTH_SHORT).show()
                }
            }

        // -------------------------------------------------------------------------------
        // boton prediccion
        val boton = findViewById<Button>(R.id.btn_prediction)
        boton.setOnClickListener{
            val intent2 = Intent(this, PredictionModeActivity::class.java)
            intent2.putExtra("userName", name)
            intent2.putExtra("userUid", userUid)
            startActivity(intent2)
        }

        // boton historial
        val boton_historial = findViewById<Button>(R.id.btn_historial)
        boton_historial.setOnClickListener {
            val intent3 = Intent(this, HistorialActivity::class.java)
            intent3.putExtra("userUid", userUid)
            intent3.putExtra("userName", name)
            startActivity(intent3)
        }

        // boton importar predicciones
        val boton_importar_predicciones = findViewById<Button>(R.id.btn_import)
        boton_importar_predicciones.setOnClickListener {
            val intent4 = Intent(this, ImportActivity::class.java)
            intent4.putExtra("userUid", userUid)
            intent4.putExtra("userName", name)
            startActivity(intent4)
        }

        // cruz para cerrar el menu desplegable
        val cierre_menu = headerView.findViewById<ImageView>(R.id.btn_close_nav)
        cierre_menu.isClickable = true
        cierre_menu.isFocusable = true
        cierre_menu.setOnClickListener {
            val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_item_one -> {
                Toast.makeText(this, "Editar perfil", Toast.LENGTH_SHORT).show()
                // navegar a pagina de editar perfil
                val intent = Intent(this, EditProfileActivity::class.java)
                intent.putExtra("userUid", userUid)
                intent.putExtra("name", name)
                startActivity(intent)
            }
            R.id.nav_item_two -> {
                Toast.makeText(this, "Cerrando sesión", Toast.LENGTH_SHORT).show()
                val auth = FirebaseAuth.getInstance()
                auth.signOut()
                // navegar al login
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
        }
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        toggle.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getNumeroPredicciones(callback: (Int) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userDocRef = db.collection("users").document(userUid ?: "")

        userDocRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // cojo el valor del campo "numeroPredicciones"
                val numeroPredicciones = document.getLong("numeroPredicciones")
                val n = numeroPredicciones?.toInt() ?: 0 // Usamos 0 si no se encuentra el valor
                callback(n)
            } else {
                // Si el documento no existe, devuelve 0
                callback(0)
            }
        }.addOnFailureListener { exception ->
            // En caso de error, devuelve 0
            callback(0)
            println("Error al obtener el documento: $exception")
        }
    }

    private fun configurarPieChart(pieChart: PieChart, validas: Float, noValidas: Float) {
        val entries = mutableListOf<PieEntry>()

        // si todavia no hay predicciones se muestra un grafico vacio
        if (validas == 0f && noValidas == 0f) {
            entries.add(PieEntry(1f, "")) // se añade asi porque si no no se mostraria nada y habria un vacio
        } else {
            entries.add(PieEntry(validas, "Válidas"))
            entries.add(PieEntry(noValidas, "No Válidas"))
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = if (validas == 0f && noValidas == 0f) {
            listOf(android.graphics.Color.LTGRAY) // grafico vacio en gris claro
        } else {
            listOf(
                android.graphics.Color.parseColor("#7cc873"), // verde para "Válidas"
                android.graphics.Color.parseColor("#e56f66")  // rojo para "No Válidas"
            )
        }
        dataSet.sliceSpace = 2f // Espacio entre segmentos del gráfico

        val data = PieData(dataSet)
        data.setValueTextSize(14f) // Tamaño de los valores
        data.setValueTextColor(android.graphics.Color.BLACK) // Color del texto

        if (validas == 0f && noValidas == 0f) {
            data.setDrawValues(false) // Ocultar valores si no hay datos reales
        }

        pieChart.data = data

        // Personalización del gráfico
        pieChart.description.isEnabled = false // Ocultar descripción
        pieChart.isDrawHoleEnabled = true // Habilitar agujero central
        pieChart.holeRadius = 40f // Ajustar tamaño del agujero
        pieChart.setEntryLabelTextSize(12f) // Tamaño del texto de etiquetas
        pieChart.setEntryLabelColor(android.graphics.Color.DKGRAY) // Color del texto de etiquetas
        pieChart.animateY(1000) // Animación al renderizar

        // Configuración de la leyenda
        val legend = pieChart.legend
        legend.isEnabled = false // Habilitar la leyenda
        legend.textSize = 18f // Tamaño del texto
        legend.textColor = android.graphics.Color.BLACK // Color del texto
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM // Posición en la parte inferior
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER // Centrado horizontalmente
        legend.formSize = 14f // Tamaño de los íconos
        legend.formToTextSpace = 6f // Espacio entre iconos y texto
        legend.orientation = Legend.LegendOrientation.HORIZONTAL // Leyenda horizontal
    }

}