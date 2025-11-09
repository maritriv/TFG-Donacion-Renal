package com.lhc.tfg_prediccion.ui.main

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.data.model.Medico
import com.lhc.tfg_prediccion.databinding.ActivityMainadminBinding
import com.lhc.tfg_prediccion.ui.edit.EditProfileActivity
import com.lhc.tfg_prediccion.ui.login.LoginActivity
import com.lhc.tfg_prediccion.ui.prediction.ShowpredActivity
import java.text.Normalizer

class MainAdmin : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainadminBinding
    private lateinit var drawer: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private var userUid: String? = null
    private var name: String? = null
    private val db = FirebaseFirestore.getInstance()

    // lista completa en memoria + renderizado
    private val allDoctors = mutableListOf<Medico>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainadminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        drawer = binding.drawerLayout
        toggle = ActionBarDrawerToggle(
            this, drawer, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        userUid = intent.getStringExtra("userUid")
        name = intent.getStringExtra("userName")

        findViewById<TextView>(R.id.title_main).text =
            getString(R.string.welcome_prefix, name ?: "")

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        val headerView = navigationView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.nav_header_textView).text = name ?: ""
        headerView.findViewById<ImageView>(R.id.btn_close_nav).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Buscar a medida que escribe
        binding.root.findViewById<TextView>(R.id.search_input)?.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filterAndRender(s?.toString().orEmpty())
                }
            }
        )

        cargarMedicos()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_item_one -> {
                Toast.makeText(this, getString(R.string.toast_edit_profile), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, EditProfileActivity::class.java).apply {
                    putExtra("userUid", userUid)
                    putExtra("name", name)
                })
            }
            R.id.nav_item_two -> {
                Toast.makeText(this, getString(R.string.toast_logging_out), Toast.LENGTH_SHORT).show()
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    // ----- Carga + render -----
    private fun cargarMedicos() {
        db.collection("users").whereEqualTo("role", "Médico")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    allDoctors.clear()
                    task.result?.forEach { doc ->
                        allDoctors += doc.toObject(Medico::class.java)
                    }
                    renderDoctors(allDoctors)
                } else {
                    Toast.makeText(this, getString(R.string.toast_table_error), Toast.LENGTH_SHORT).show()
                }
            }
    }

    /** Filtra por apellidos, nombre o email (ignorando tildes y mayúsculas) */
    private fun filterAndRender(query: String) {
        if (query.isBlank()) {
            renderDoctors(allDoctors)
            return
        }
        val q = norm(query)
        val filtered = allDoctors.filter { m ->
            val full = "${m.lastname.orEmpty()} ${m.name.orEmpty()}"
            norm(full).contains(q) || norm(m.email.orEmpty()).contains(q)
        }
        renderDoctors(filtered)
    }

    private fun renderDoctors(list: List<Medico>) {
        val tabla = findViewById<TableLayout>(R.id.tabla_medicos)
        tabla.removeAllViews()
        list.forEach { nuevaFila(tabla, it) }
    }

    private fun norm(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        return n.replace("\\p{Mn}+".toRegex(), "").lowercase()
    }

    // ----- Fila -----
    private fun nuevaFila(tabla: TableLayout, medico: Medico) {
        val fila = TableRow(this)

        // "Apellidos,\nNombre"
        val ap = medico.lastname?.trim().orEmpty()
        val nom = medico.name?.trim().orEmpty()
        val displayName = if (ap.isNotEmpty() && nom.isNotEmpty()) "$ap,\n$nom" else ap + nom

        TextView(this).apply {
            text = displayName
            setTextColor(ContextCompat.getColor(this@MainAdmin, android.R.color.black))
            textSize = 18f
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            isSingleLine = false
            maxLines = 2
            setLineSpacing(0f, 1.05f)
            fila.addView(this)
        }

        // Espacio flexible
        View(this).apply {
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT).also {
                it.weight = 1f
            }
            fila.addView(this)
        }

        // Email
        TextView(this).apply {
            text = medico.email
            setTextColor(ContextCompat.getColor(this@MainAdmin, android.R.color.black))
            textSize = 18f
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            fila.addView(this)
        }

        // Espacio fijo antes del icono
        View(this).apply {
            layoutParams = TableRow.LayoutParams(30, TableRow.LayoutParams.MATCH_PARENT)
            fila.addView(this)
        }

        // Icono "ver predicciones"
        ImageView(this).apply {
            isClickable = true
            isFocusable = true
            setImageResource(R.drawable.pred)
            layoutParams = TableRow.LayoutParams(55, 55)
            setOnClickListener {
                startActivity(Intent(this@MainAdmin, ShowpredActivity::class.java).apply {
                    putExtra("userUid", userUid)
                    putExtra("name", name)
                    putExtra("email", medico.email)
                })
            }
            fila.addView(this)
        }

        tabla.addView(fila)

        // Separador
        View(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                30
            )
            tabla.addView(this)
        }
    }

    private fun eliminarMedico(medico: Medico) {
        db.collection("users").document(medico.uid)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.toast_doctor_deleted), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.toast_doctor_delete_error, e.message ?: "-"),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}