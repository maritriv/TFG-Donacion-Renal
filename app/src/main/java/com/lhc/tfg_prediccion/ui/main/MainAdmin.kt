package com.lhc.tfg_prediccion.ui.main

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
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

class MainAdmin: AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainadminBinding
    private lateinit var drawer: DrawerLayout // controla la apertura y cierre del menu lateral
    private lateinit var toggle: ActionBarDrawerToggle //maneja los eventos de apertura y cierre del menu cuando el usuario toca ese icono
    private var userUid: String? = null
    private var name: String? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainadminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // menu desplegable
        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        drawer = binding.drawerLayout
        toggle = ActionBarDrawerToggle(
            this,
            drawer,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        // nombre que se pasa con el intent para la bienvenida
        name = intent.getStringExtra("userName")
        val nombreBienvenida = findViewById<TextView>(R.id.title_main)
        nombreBienvenida.text = "Bienvenid@ $name"

        // nombre menu
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        val headerView = navigationView.getHeaderView(0)
        val aux = headerView.findViewById<TextView>(R.id.nav_header_textView)
        aux.text = "$name"

        // cruz para cerrar el menu desplegable
        val cierre_menu = headerView.findViewById<ImageView>(R.id.btn_close_nav)
        cierre_menu.isClickable = true
        cierre_menu.isFocusable = true
        cierre_menu.setOnClickListener {
            val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // cargar medicos
        cargarMedicos()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_item_one -> {
                userUid = intent.getStringExtra("userUid")
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

    private fun cargarMedicos(){
        val tabla = findViewById<TableLayout>(R.id.tabla_medicos)
        // coger medicos segun rol
        db.collection("users").whereEqualTo("role", "Médico")
            .get()
            .addOnCompleteListener() { firestoreTask ->
                if (firestoreTask.isSuccessful){
                    val medicos = firestoreTask.result
                    for (aux in medicos) {
                        val medico = aux.toObject(Medico::class.java)
                        nuevaFila(tabla, medico)
                    }
                } else{
                    Toast.makeText(this, "Error al cargar la tabla", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun nuevaFila(tabla: TableLayout, medico: Medico){
        val fila = TableRow(this)

        // apellidos
        val apellidos = TextView(this)
        apellidos.text = medico.lastname
        apellidos.setTextColor(resources.getColor(android.R.color.black))
        apellidos.textSize = 18f
        apellidos.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        fila.addView(apellidos)

        // espacio entre apellidos y email
        val espacioApellidosEmail = View(this)
        val paramsEspacio = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT)
        paramsEspacio.weight = 1f  // Añadir espacio proporcional
        espacioApellidosEmail.layoutParams = paramsEspacio
        fila.addView(espacioApellidosEmail)

        // email
        val email = TextView(this)
        email.text = medico.email
        email.setTextColor(resources.getColor(android.R.color.black))
        email.textSize = 18f
        email.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        fila.addView(email)

        // espacio entre email e icono de eliminar
        val espacioEmailEliminar = View(this)
        val paramsEspacioEliminar = TableRow.LayoutParams(30, TableRow.LayoutParams.MATCH_PARENT)
        espacioEmailEliminar.layoutParams = paramsEspacioEliminar
        fila.addView(espacioEmailEliminar)

        // icono predicciones
        val mostrar_pred = ImageView(this)
        mostrar_pred.isClickable = true
        mostrar_pred.isFocusable = true
        mostrar_pred.setImageResource(R.drawable.pred)
        val params = TableRow.LayoutParams(55, 55) // altura y anchura icono
        mostrar_pred.layoutParams = params

        mostrar_pred.setOnClickListener{
            val intent = Intent(this, ShowpredActivity::class.java)
            intent.putExtra("userUid", userUid)
            intent.putExtra("name", name)
            intent.putExtra("email", medico.email) // para identificar al medico
            startActivity(intent)
        }
        fila.addView(mostrar_pred)

        // añadir fila
        tabla.addView(fila)

        // espacio entre filas
        val espacio = View(this)
        espacio.layoutParams = TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            30 // espacio entre filas
        )
        tabla.addView(espacio)
    }

    private fun eliminarMedico(medico: Medico){
        // eliminamos de Firestore Database
        val usersCollection = db.collection("users")
        usersCollection.document(medico.uid)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Médico eliminado correctamente", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al eliminar médico: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}