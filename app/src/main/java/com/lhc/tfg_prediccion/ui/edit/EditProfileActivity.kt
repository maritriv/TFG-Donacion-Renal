package com.lhc.tfg_prediccion.ui.edit

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.databinding.ActivityEditprofileBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity
import com.lhc.tfg_prediccion.ui.main.MainAdmin

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditprofileBinding
    private lateinit var roleSpinner: Spinner
    private lateinit var userUid: String
    private lateinit var name: String
    private var role: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditprofileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Spinner de rol
        roleSpinner = binding.roleSpinner
        val roles = arrayOf("Medico", "Administrador")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = adapter
        roleSpinner.isEnabled = false

        // obtener UID y nombre del intent
        userUid = intent.getStringExtra("userUid") ?: ""
        name = intent.getStringExtra("name") ?: ""

        // cargar datos del usuario
        loadUserData()

        // Navegación: volver (fila completa + icono)
        binding.backRow.setOnClickListener { goToMain() }
        binding.btnBack.setOnClickListener { goToMain() }

        // Botón REALIZAR CAMBIOS
        binding.change.setOnClickListener {
            val name = binding.name.text.toString()
            val lastname = binding.lastname.text.toString()
            val birthdate = binding.birthdate.text.toString()
            val password = binding.password.text.toString()
            val newpass = binding.newPassword.text.toString()
            val confirmPassword = binding.confirmPassword.text.toString()

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(
                    this,
                    "Usuario no autentificado. Inténtelo de nuevo",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val email = currentUser.email
            if (email.isNullOrEmpty()) {
                Toast.makeText(
                    this,
                    "No se puede obtener el email. Inténtelo de nuevo",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (name.isEmpty() || lastname.isEmpty() || birthdate.isEmpty()) {
                Toast.makeText(
                    this,
                    "Los tres primeros campos son obligatorios",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(userUid)

            // No quiere cambiar contraseña
            if (password.isEmpty() && newpass.isEmpty() && confirmPassword.isEmpty()) {
                updateUser(userRef, name, lastname, birthdate)
                return@setOnClickListener
            }

            // Quiere cambiar contraseña
            if (password.isNotEmpty() && newpass.isNotEmpty() && confirmPassword.isNotEmpty()) {
                if (newpass == confirmPassword) {
                    val credential =
                        EmailAuthProvider.getCredential(currentUser.email!!, password)
                    currentUser.reauthenticate(credential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                currentUser.updatePassword(newpass)
                                    .addOnCompleteListener { updateTask ->
                                        if (updateTask.isSuccessful) {
                                            updateUser(userRef, name, lastname, birthdate)
                                        } else {
                                            Toast.makeText(
                                                this,
                                                "Error al actualizar la contraseña",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                            } else {
                                Toast.makeText(
                                    this,
                                    "Contraseña actual incorrecta",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                } else {
                    Toast.makeText(
                        this,
                        "Nueva contraseña y su confirmación no coinciden",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Si quieres cambiar la contraseña, rellena los tres campos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun goToMain() {
        val selectedRole: String = roleSpinner.selectedItem.toString()
        val intent = if (selectedRole == "Medico") {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, MainAdmin::class.java)
        }
        intent.putExtra("userName", name)
        intent.putExtra("userUid", userUid)
        startActivity(intent)
    }

    private fun loadUserData() {
        // Email desde Authentication
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            binding.email.setText(it.email)
            binding.email.isFocusable = false
            binding.email.isClickable = false
        }

        // Datos extra desde Firestore
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userUid)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    binding.name.setText(document.getString("name") ?: "")
                    binding.lastname.setText(document.getString("lastname") ?: "")
                    binding.birthdate.setText(document.getString("birthdate") ?: "")

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
        if (roleIndex >= 0) {
            roleSpinner.setSelection(roleIndex)
        } else {
            roleSpinner.setSelection(0)
        }
    }

    private fun updateUser(
        userRef: DocumentReference,
        name: String,
        lastname: String,
        birthdate: String
    ) {
        val changes = mapOf(
            "name" to name,
            "lastname" to lastname,
            "birthdate" to birthdate
        )

        userRef.update(changes)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Cambios guardados correctamente",
                    Toast.LENGTH_SHORT
                ).show()

                goToMain()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Error al actualizar los datos. Inténtelo de nuevo",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}