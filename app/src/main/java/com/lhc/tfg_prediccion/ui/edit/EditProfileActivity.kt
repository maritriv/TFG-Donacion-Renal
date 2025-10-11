package com.lhc.tfg_prediccion.ui.edit

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.lhc.tfg_prediccion.databinding.ActivityEditprofileBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity
import com.lhc.tfg_prediccion.ui.main.MainAdmin

class EditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditprofileBinding

    private lateinit var nameEditText: TextInputEditText
    private lateinit var lastnameEditText: TextInputEditText
    private lateinit var birthdateEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var roleSpinner: Spinner

    private var role: String? = null
    private var userUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditprofileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializa los campos de la interfaz
        nameEditText = findViewById(R.id.name)
        lastnameEditText = findViewById(R.id.lastname)
        birthdateEditText = findViewById(R.id.birthdate)
        emailEditText = findViewById(R.id.email)
        roleSpinner = findViewById(R.id.role_spinner)

        // role spinner
        val roles = arrayOf("Medico", "Administrador")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = adapter
        roleSpinner.isEnabled = false

        // obtener el UID del usuario pasado desde la actividad anterior
        userUid = intent.getStringExtra("userUid")
        val name = intent.getStringExtra("name")

        // cargar los datos del usuario en los campos
        loadUserData()

        // boton para volver a la pagina principal
        binding.backMain.setOnClickListener{
            // distingo medico de administrador
            val selectedRole: String = roleSpinner.selectedItem.toString()
            if (selectedRole == "Medico"){
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("userName", name)
                intent.putExtra("userUid", userUid)
                startActivity(intent)
            }
            else{
                val intent = Intent(this, MainAdmin::class.java)
                intent.putExtra("userName", name)
                intent.putExtra("userUid", userUid)
                startActivity(intent)
            }

        }

        // boton para realizar los cambios
        binding.change.setOnClickListener {
            // primero compruebo que los campos esten llenos
            val name = binding.name.text.toString()
            val lastname = binding.lastname.text.toString()
            val birthdate = binding.birthdate.text.toString()
            val password = binding.password.text.toString()
            val newpass = binding.newPassword.text.toString()
            val confirmPassword = binding.confirmPassword.text.toString()

            // usuario actual
            val currentUser = FirebaseAuth.getInstance().currentUser
            if(currentUser == null){
                Toast.makeText(this, "Usuario no autenticado. Inténtelo de nuevo", Toast.LENGTH_SHORT).show()
            }

            // compruebo email
            // usuario actual
            val email = currentUser?.email
            if(email.isNullOrEmpty()){
                Toast.makeText(this, "No se puede obtener el email. Inténtelo de nuevo", Toast.LENGTH_SHORT).show()
            }

            // si algun campo de los tres primeros esta vacio mensaje de error
            if(name.isEmpty() || lastname.isEmpty() || birthdate.isEmpty()){
                Toast.makeText(this, "Los tres primeros campos son obligatorios", Toast.LENGTH_SHORT).show()
            }
            val db = FirebaseFirestore.getInstance() // accede a la coleccion 'users' de mi base de datos
            val userRef = db.collection("users").document(userUid ?: "") // para poder cambiar los datos del usuario

            // no desea cambiar la contraseña
            if(password.isEmpty() && newpass.isEmpty() && confirmPassword.isEmpty()){
                updateUser(userRef, name, lastname, birthdate)
                return@setOnClickListener
            }

            // si quiere cambiar la contraseña
            if(password.isNotEmpty() && newpass.isNotEmpty() && confirmPassword.isNotEmpty()){
                // formulario bien cumplimentado
                // compruebo que la nueva y la confirmacion sean iguales
                if(newpass == confirmPassword){
                    // uso el reauthenticate y updatePassword que Firebase Authentication tiene implementado
                    val user = EmailAuthProvider.getCredential(currentUser?.email!!, password)
                    currentUser.reauthenticate(user)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                currentUser.updatePassword(newpass)
                                    .addOnCompleteListener { updateTask ->
                                        if (updateTask.isSuccessful) {
                                            updateUser(userRef, name, lastname, birthdate)
                                        } else {
                                            Toast.makeText(this, "Error al actualizar la contraseña", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            }
                            else {
                                Toast.makeText(this, "Contraseña actual incorrecta", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                else{
                    Toast.makeText(this, "Nueva contraseña y su confirmación no coinciden", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadUserData() {
        // Obtener el correo electrónico del usuario desde Firebase Authentication
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            // Asignar el email al campo de texto y hacerlo no editable
            emailEditText.setText(it.email)
            emailEditText.isFocusable = false
            emailEditText.isClickable = false
        }

        // Obtener datos adicionales (nombre, apellidos, fecha de nacimiento, rol) de Firestore
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userUid ?: "")

        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    // Rellenar los campos con los datos de Firestore
                    nameEditText.setText(document.getString("name"))
                    lastnameEditText.setText(document.getString("lastname"))
                    birthdateEditText.setText(document.getString("birthdate"))

                    // Asignar el rol al spinner
                    role = document.getString("role")
                    setRoleInSpinner(role)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar los datos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setRoleInSpinner(role: String?) {
        val roles = arrayOf("Medico", "Administrador")
        val roleIndex = roles.indexOf(role)
        // Seleccionar el índice correspondiente al rol obtenido de Firestore
        if (roleIndex >= 0) {
            roleSpinner.setSelection(roleIndex)
        }
        else{
            roleSpinner.setSelection(0) // si no tuvise rol sale 'Medico' por defecto
        }
    }

    private fun updateUser(userRef: DocumentReference, name: String, lastname: String, birthdate: String){
        val changes = mapOf(
            "name" to name,
            "lastname" to lastname,
            "birthdate" to birthdate
        )
        userRef.update(changes)
            .addOnSuccessListener {
                Toast.makeText(this, "Cambios guardados correctamente", Toast.LENGTH_SHORT).show()
                if (role == "Médico"){
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("userName", name)
                    intent.putExtra("userUid", userUid)
                    startActivity(intent)
                }
                else{
                    val intent = Intent(this, MainAdmin::class.java)
                    intent.putExtra("userName", name)
                    intent.putExtra("userUid", userUid)
                    startActivity(intent)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al actualizar los datos. Inténtelo de nuevo", Toast.LENGTH_SHORT).show()
            }
    }
}
