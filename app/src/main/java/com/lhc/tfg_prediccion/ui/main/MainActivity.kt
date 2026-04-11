package com.lhc.tfg_prediccion.ui.main

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.ui.edit.EditProfileActivity
import com.lhc.tfg_prediccion.ui.historial.HistorialActivity
import com.lhc.tfg_prediccion.ui.login.LoginActivity
import com.lhc.tfg_prediccion.ui.prediction.ImportActivity
import com.lhc.tfg_prediccion.ui.prediction.LBL_AFTER
import com.lhc.tfg_prediccion.ui.prediction.LBL_MID
import com.lhc.tfg_prediccion.ui.prediction.MODE_AFTER
import com.lhc.tfg_prediccion.ui.prediction.MODE_MID
import com.lhc.tfg_prediccion.ui.prediction.PredictionModeActivity
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawer: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private var userUid: String? = null
    private var name: String? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        drawer = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(
            this,
            drawer,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = ContextCompat.getColor(this, R.color.white)

        name = intent.getStringExtra("userName")
        userUid = intent.getStringExtra("userUid")

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val headerView = navigationView.getHeaderView(0)
        val aux = headerView.findViewById<TextView>(R.id.nav_header_textView)
        aux.text = "$name"
        navigationView.setNavigationItemSelectedListener(this)

        val centerValue = findViewById<TextView>(R.id.center_value)
        val centerLabel = findViewById<TextView>(R.id.center_label)
        val pieChart = findViewById<PieChart>(R.id.pie_chart)
        val spinnerMode = findViewById<Spinner>(R.id.spinner_mode)

        val opciones = arrayOf(
            "Todos los momentos",
            LBL_MID,
            LBL_AFTER
        )

        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_mode_item,
            opciones
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.isSingleLine = true
                view.ellipsize = TextUtils.TruncateAt.END
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setBackgroundResource(android.R.color.transparent)
                return view
            }
        }

        adapter.setDropDownViewResource(R.layout.spinner_mode_dropdown_item)
        spinnerMode.adapter = adapter

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val seleccion = opciones[position]
                val mode: String? = when (seleccion) {
                    LBL_MID -> MODE_MID
                    LBL_AFTER -> MODE_AFTER
                    else -> null
                }

                cargarEstadisticasPorModo(mode, pieChart, centerValue, centerLabel)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val boton = findViewById<Button>(R.id.btn_prediction)
        boton.setOnClickListener {
            val intent2 = Intent(this, PredictionModeActivity::class.java)
            intent2.putExtra("userName", name)
            intent2.putExtra("userUid", userUid)
            startActivity(intent2)
        }

        val botonHistorial = findViewById<Button>(R.id.btn_historial)
        botonHistorial.setOnClickListener {
            val intent3 = Intent(this, HistorialActivity::class.java)
            intent3.putExtra("userUid", userUid)
            intent3.putExtra("userName", name)
            startActivity(intent3)
        }

        val botonImportarPredicciones = findViewById<Button>(R.id.btn_import)
        botonImportarPredicciones.setOnClickListener {
            val intent4 = Intent(this, ImportActivity::class.java)
            intent4.putExtra("userUid", userUid)
            intent4.putExtra("userName", name)
            startActivity(intent4)
        }

        val cierreMenu = headerView.findViewById<ImageView>(R.id.btn_close_nav)
        cierreMenu.isClickable = true
        cierreMenu.isFocusable = true
        cierreMenu.setOnClickListener {
            val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_item_one -> {
                Toast.makeText(this, "Editar perfil", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, EditProfileActivity::class.java)
                intent.putExtra("userUid", userUid)
                intent.putExtra("name", name)
                startActivity(intent)
            }

            R.id.nav_item_two -> {
                Toast.makeText(this, "Cerrando sesión", Toast.LENGTH_SHORT).show()
                val auth = FirebaseAuth.getInstance()
                auth.signOut()
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

    private fun cargarEstadisticasPorModo(
        mode: String?,
        pieChart: PieChart,
        centerValue: TextView,
        centerLabel: TextView
    ) {
        val uid = userUid ?: return

        var query: Query = db.collection("predicciones")
            .whereEqualTo("uid_medico", uid)

        if (mode != null) {
            query = query.whereEqualTo("prediction_mode", mode)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                var validas = 0f
                var noValidas = 0f

                for (doc in snapshot) {
                    val rawVal = doc.get("valido")

                    val esValida = when (rawVal) {
                        is Boolean -> rawVal
                        is String -> rawVal.equals("si", true) ||
                                rawVal.equals("sí", true) ||
                                rawVal.equals("true", true) ||
                                rawVal == "1"
                        is Number -> rawVal.toInt() != 0
                        else -> false
                    }

                    if (esValida) validas++ else noValidas++
                }

                val total = (validas + noValidas).toInt()
                centerValue.text = total.toString()
                centerLabel.text = "PREDICCIONES"

                configurarPieChart(pieChart, validas, noValidas)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar estadísticas", Toast.LENGTH_SHORT).show()
            }
    }

    private fun configurarPieChart(pieChart: PieChart, validas: Float, noValidas: Float) {
        val entries = mutableListOf<PieEntry>()

        val total = validas + noValidas
        val txtValid = findViewById<TextView>(R.id.txt_valid_percent)
        val txtInvalid = findViewById<TextView>(R.id.txt_invalid_percent)

        if (total == 0f) {
            txtValid.text = "0% Válidas"
            txtInvalid.text = "0% No válidas"
        } else {
            val validPercent = (validas / total * 100).roundToInt()
            val invalidPercent = (noValidas / total * 100).roundToInt()

            txtValid.text = "$validPercent% Válidas"
            txtInvalid.text = "$invalidPercent% No válidas"
        }

        if (validas == 0f && noValidas == 0f) {
            entries.add(PieEntry(1f, ""))
        } else {
            entries.add(PieEntry(validas, "Válidas"))
            entries.add(PieEntry(noValidas, "No válidas"))
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = if (validas == 0f && noValidas == 0f) {
            listOf(android.graphics.Color.LTGRAY)
        } else {
            listOf(
                android.graphics.Color.parseColor("#7cc873"),
                android.graphics.Color.parseColor("#e56f66")
            )
        }
        dataSet.sliceSpace = 2f

        val data = PieData(dataSet)
        data.setDrawValues(false)

        pieChart.legend.isEnabled = false
        pieChart.setDrawEntryLabels(false)
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.holeRadius = 70f
        pieChart.transparentCircleRadius = 75f
        pieChart.animateY(1000)
        pieChart.invalidate()
    }
}