package com.lhc.tfg_prediccion.ui.edit

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityEditprofileBinding
import com.lhc.tfg_prediccion.ui.main.MainActivity
import com.lhc.tfg_prediccion.ui.main.MainAdmin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditprofileBinding
    private lateinit var userUid: String

    // rol actual del usuario (solo lectura, se carga de Firestore y no se edita)
    private var role: String = "Medico"

    // valores originales para detectar cambios
    private var originalName: String = ""
    private var originalLastname: String = ""
    private var originalBirthdate: String = ""
    private var originalEmail: String = ""

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val datePicker by lazy {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecciona una fecha")
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditprofileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UID recibido
        userUid = intent.getStringExtra("userUid") ?: ""

        setupBirthdatePicker()

        // cargar datos del usuario (desde Firestore, incluido email y rol)
        loadUserData()

        // navegación: atrás (fila + icono + botón físico)
        binding.backRow.setOnClickListener { handleBackPressed() }
        binding.btnBack.setOnClickListener { handleBackPressed() }
        onBackPressedDispatcher.addCallback(this) {
            handleBackPressed()
        }

        // Botón REALIZAR CAMBIOS
        binding.change.setOnClickListener {
            val name = binding.name.text.toString().trim()
            val lastname = binding.lastname.text.toString().trim()
            val birthdate = binding.birthdate.text.toString().trim()
            val email = binding.email.text.toString().trim()
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

            if (name.isEmpty() || lastname.isEmpty() || birthdate.isEmpty() || email.isEmpty()) {
                Toast.makeText(
                    this,
                    "Nombre, apellidos, fecha y email son obligatorios",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // 1) Comprobar que el email no está en uso por otro usuario (EN FIRESTORE)
            checkEmailAvailable(currentUser, email) { available ->
                if (!available) {
                    Toast.makeText(
                        this,
                        "Ese correo ya está en uso por otro usuario",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@checkEmailAvailable
                }

                // 2) Si el email es válido/libre, seguimos con la lógica de contraseña + actualización
                saveWithValidatedEmail(
                    currentUser,
                    name,
                    lastname,
                    birthdate,
                    email,
                    password,
                    newpass,
                    confirmPassword
                )
            }
        }
    }

    // ---------- Comprobación de email único (EN FIRESTORE) ----------

    private fun checkEmailAvailable(
        currentUser: FirebaseUser,
        email: String,
        onResult: (Boolean) -> Unit
    ) {
        // No ha cambiado el correo -> lo damos por bueno
        if (email == currentUser.email) {
            onResult(true)
            return
        }

        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { query ->
                // ¿Hay algún otro documento con ese email?
                val takenByOtherUser = query.documents.any { it.id != userUid }
                onResult(!takenByOtherUser)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Error al comprobar el correo. Inténtelo de nuevo",
                    Toast.LENGTH_SHORT
                ).show()
                onResult(false)
            }
    }

    // ---------- Lógica de guardado una vez validado el email ----------

    private fun saveWithValidatedEmail(
        currentUser: FirebaseUser,
        name: String,
        lastname: String,
        birthdate: String,
        email: String,
        password: String,
        newpass: String,
        confirmPassword: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userUid)

        // No quiere cambiar contraseña → solo datos de perfil
        if (password.isEmpty() && newpass.isEmpty() && confirmPassword.isEmpty()) {
            updateUser(userRef, name, lastname, birthdate, email, currentUser)
            return
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
                                        updateUser(
                                            userRef,
                                            name,
                                            lastname,
                                            birthdate,
                                            email,
                                            currentUser
                                        )
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

    // ---------- Navegación y descarte de cambios ----------

    private fun handleBackPressed() {
        if (hasChanges()) {
            confirmDiscardChanges()
        } else {
            goToMain()
        }
    }

    private fun goToMain() {
        val currentName = binding.name.text.toString()

        // Normalizamos el rol para evitar problemas de tilde / mayúsculas
        val normalizedRole = role.lowercase(Locale.ROOT)
        val isMedico = normalizedRole == "medico" || normalizedRole == "médico"

        val intent = if (isMedico) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, MainAdmin::class.java)
        }

        intent.putExtra("userName", currentName)
        intent.putExtra("userUid", userUid)
        startActivity(intent)
        finish()
    }

    private fun confirmDiscardChanges() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Descartar cambios")
            .setMessage("¿Estás seguro de que quieres descartar los cambios realizados?")
            .setPositiveButton("Descartar") { _, _ ->
                goToMain()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                ContextCompat.getDrawable(this, R.drawable.bg_panel_full_rounded)
            )
        }

        dialog.show()
    }

    private fun hasChanges(): Boolean {
        val currentName = binding.name.text.toString().trim()
        val currentLastname = binding.lastname.text.toString().trim()
        val currentBirthdate = binding.birthdate.text.toString().trim()
        val currentEmail = binding.email.text.toString().trim()

        val pass = binding.password.text.toString()
        val newPass = binding.newPassword.text.toString()
        val confirmPass = binding.confirmPassword.text.toString()

        return currentName != originalName ||
                currentLastname != originalLastname ||
                currentBirthdate != originalBirthdate ||
                currentEmail != originalEmail ||
                pass.isNotEmpty() ||
                newPass.isNotEmpty() ||
                confirmPass.isNotEmpty()
    }

    // ---------- DatePicker para la fecha ----------

    private fun setupBirthdatePicker() {
        binding.birthdate.setOnClickListener {
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }

        datePicker.addOnPositiveButtonClickListener { millis ->
            val date = Date(millis)
            binding.birthdate.setText(dateFormat.format(date))
        }
    }

    // ---------- Carga de datos desde Firestore ----------

    private fun loadUserData() {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userUid)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val name = document.getString("name") ?: ""
                    val lastname = document.getString("lastname") ?: ""
                    val birthdate = document.getString("birthdate") ?: ""
                    val email = document.getString("email") ?: ""
                    role = document.getString("role") ?: "Medico"

                    binding.name.setText(name)
                    binding.lastname.setText(lastname)
                    binding.birthdate.setText(birthdate)
                    binding.email.setText(email)

                    // guardamos originales
                    originalName = name
                    originalLastname = lastname
                    originalBirthdate = birthdate
                    originalEmail = email
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar los datos", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- Actualización en Firestore + (opcional) Auth ----------

    private fun updateUser(
        userRef: DocumentReference,
        name: String,
        lastname: String,
        birthdate: String,
        email: String,
        currentUser: FirebaseUser
    ) {
        val changes = mapOf(
            "name" to name,
            "lastname" to lastname,
            "birthdate" to birthdate,
            "email" to email
            // el campo "role" NO se toca
        )

        userRef.update(changes)
            .addOnSuccessListener {
                // Actualizar email también en Auth si ha cambiado
                if (currentUser.email != email) {
                    currentUser.updateEmail(email).addOnCompleteListener {
                        // si falla, al menos Firestore ya está actualizado
                    }
                }

                Toast.makeText(
                    this,
                    "Cambios guardados correctamente",
                    Toast.LENGTH_SHORT
                ).show()

                // Actualizamos originales
                originalName = name
                originalLastname = lastname
                originalBirthdate = birthdate
                originalEmail = email

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