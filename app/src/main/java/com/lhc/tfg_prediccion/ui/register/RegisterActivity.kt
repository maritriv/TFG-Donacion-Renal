package com.lhc.tfg_prediccion.ui.register

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityRegisterBinding
import com.lhc.tfg_prediccion.ui.login.LoginActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.addCallback

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val datePicker by lazy {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecciona una fecha")
            .setTheme(R.style.ThemeOverlay_CustomDatePicker)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón volver + flecha
        onBackPressedDispatcher.addCallback(this) {
            goToLogin()
        }

        // Init Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Spinner de roles
        val roles = arrayOf("Médico", "Administrador")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        binding.roleSpinner.adapter = adapter

        // Fecha de nacimiento
        binding.birthdate.setOnClickListener {
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }
        datePicker.addOnPositiveButtonClickListener { selection: Long? ->
            selection?.let {
                val date = Date(it)
                val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.birthdate.setText(fmt.format(date))
            }
        }

        // Navegación: Volver (icono + texto)
        binding.backRow.setOnClickListener { goToLogin() }
        binding.btnBack.setOnClickListener { goToLogin() }

        // Registro
        binding.registerButton.setOnClickListener {
            val name = binding.name.text.toString()
            val lastname = binding.lastname.text.toString()
            val birthdate = binding.birthdate.text.toString()
            val email = binding.email.text.toString()
            val password = binding.password.text.toString()
            val confirmPassword = binding.confirmPassword.text.toString()
            val role = binding.roleSpinner.selectedItem.toString()

            if (name.isEmpty() || lastname.isEmpty() || birthdate.isEmpty() ||
                email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()
            ) {
                Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            registerUser(name, lastname, birthdate, email, password, role)
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun registerUser(
        name: String,
        lastname: String,
        birthdate: String,
        email: String,
        password: String,
        role: String
    ) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user != null) {
                    saveUserToFirestore(user, name, lastname, birthdate, email, role)
                } else {
                    Toast.makeText(this, "Error de registro: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("RegisterError", "Error en el registro: ${task.exception?.message}")
                Toast.makeText(this, "Error de registro: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveUserToFirestore(
        user: FirebaseUser,
        name: String,
        lastname: String,
        birthdate: String,
        email: String,
        role: String
    ) {
        val userMap = hashMapOf(
            "uid" to user.uid,
            "name" to name,
            "lastname" to lastname,
            "birthdate" to birthdate,
            "email" to email, // usa el email que se introdujo
            "role" to role,
            "numeroPredicciones" to 0,
            "predicciones_validas" to 0,
            "predicciones_no_validas" to 0
        )

        db.collection("users")
            .document(user.uid)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                goToLogin()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar los datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}