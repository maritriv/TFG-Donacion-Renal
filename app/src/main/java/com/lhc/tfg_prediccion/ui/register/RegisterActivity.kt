package com.lhc.tfg_prediccion.ui.register

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.databinding.ActivityRegisterBinding
import com.lhc.tfg_prediccion.ui.login.LoginActivity
import com.lhc.tfg_prediccion.ui.main.MainActivity
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar base de datos y autenticacion
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Configurar el Spinner de roles
        val roles = arrayOf("Médico", "Administrador")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        binding.roleSpinner.adapter = adapter

        // Fecha de nacimiento
        binding.birthdate.setOnClickListener {
            showDatePickerDialog()
        }

        // Boton de registro
        binding.registerButton.setOnClickListener {
            val name = binding.name.text.toString()
            val lastname = binding.lastname.text.toString()
            val birthdate = binding.birthdate.text.toString()
            val email = binding.email.text.toString()
            val password = binding.password.text.toString()
            val confirmPassword = binding.confirmPassword.text.toString()
            val role = binding.roleSpinner.selectedItem.toString()

            // todos los campos deben estar llenos
            if (name.isEmpty() || lastname.isEmpty() || birthdate.isEmpty() || email.isEmpty() || password.isEmpty()
                || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // contraseña igual a confirmacion
            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // funcion para añadir usuario a la base de datos
            registerUser(name, lastname, birthdate, email, password, role)
        }

        // Botón de Volver al Inicio de sesión
        binding.loginButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun registerUser(name: String, lastname: String, birthdate:String, email:String, password:String, role:String){
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this){ task ->
            if(task.isSuccessful){
                // guardar en la base de datos
                val user = auth.currentUser
                if(user != null){
                    saveUserToFirestore(user, name, lastname, birthdate, email, password, role)
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                } else{ // mensaje de error
                    Toast.makeText(this, "Error de registro: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("RegisterError", "Error en el registro: ${task.exception?.message}")
                Toast.makeText(this, "Error de registro: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveUserToFirestore(user: FirebaseUser, name: String, lastname: String, birthdate:String, email:String,
                                    password:String, role:String){
        val userMap = hashMapOf(
            "uid" to user.uid,
            "name" to name,
            "lastname" to lastname,
            "birthdate" to birthdate,
            "email" to user.email,
            "role" to role,
            "numeroPredicciones" to 0,
            "predicciones_validas" to 0,
            "predicciones_no_validas" to 0
        )

        // Guardar la información del usuario en la colección "users" de Firestore
        db.collection("users")
            .document(user.uid)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Registro exitoso y datos guardados en la base de datos", Toast.LENGTH_SHORT).show()
                // Redirigir al login después del registro exitoso
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish() // Finalizar la actividad de registro
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar los datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
            binding.birthdate.setText(formattedDate)
        }, year, month, day)

        datePicker.show()
    }
}
