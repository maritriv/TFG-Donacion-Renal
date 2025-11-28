package com.lhc.tfg_prediccion.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityMedicalProfileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicalProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicalProfileBinding
    private val db = FirebaseFirestore.getInstance()

    private var isEditMode = false
    private var userId: String? = null
    private var originalActive: Boolean = true
    private var currentActive: Boolean = true
    private var originalRole: String = "Médico"

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val datePicker by lazy {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecciona una fecha")
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicalProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navegación
        binding.backRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }

        // Id del usuario recibido desde ViewUsersActivity
        userId = intent.getStringExtra("userId")
        if (userId == null) {
            Toast.makeText(this, "Usuario no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRoleSpinner()
        setupBirthdatePicker()

        // Aseguramos que arrancamos en modo SOLO LECTURA
        toggleEditMode(false)

        loadUserData()

        binding.tvUserStatus.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            currentActive = !currentActive
            updateStatusBadge()
        }

        // Editar
        binding.btnEditUser.setOnClickListener {
            toggleEditMode(!isEditMode)
        }

        // Borrar
        binding.btnDeleteUser.setOnClickListener {
            confirmDeleteUser()
        }

        // Guardar cambios
        binding.btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        binding.btnCancelChanges.setOnClickListener {
            // Volvemos a cargar desde Firestore y salimos del modo edición
            loadUserData()
            toggleEditMode(false)
        }

        // Cambiar estado Activo / Inactivo solo en modo edición
        binding.tvUserStatus.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            currentActive = !currentActive
            updateStatusBadge()
        }
    }

    // -------------------------------------------------------------
    // Cargar datos del usuario desde Firestore
    // -------------------------------------------------------------
    private fun loadUserData() {
        val id = userId ?: return

        db.collection("users").document(id).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val name = doc.getString("name").orEmpty()
                val lastname = doc.getString("lastname").orEmpty()
                val fullName = listOf(name, lastname)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")

                val email = doc.getString("email").orEmpty()
                val birthdate = doc.getString("birthdate").orEmpty()
                val role = doc.getString("role") ?: "Médico"
                originalRole = role

                val active = doc.getBoolean("active") ?: true
                originalActive = active
                currentActive = active

                // Rellenamos vistas
                binding.etFullName.setText(fullName)
                binding.etEmail.setText(email)
                binding.etBirthdate.setText(
                    if (birthdate.isBlank()) getString(R.string.sin_fecha)
                    else birthdate
                )

                binding.tvUserRole.text = role
                binding.spinnerRole.setSelection(
                    if (role == "Administrador") 1 else 0
                )

                updateStatusBadge()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar el usuario", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    // -------------------------------------------------------------
    // Modo edición ON/OFF
    // -------------------------------------------------------------
    private fun toggleEditMode(enable: Boolean) {
        isEditMode = enable

        // Iconos
        binding.btnEditUser.visibility = if (enable) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnDeleteUser.visibility = if (enable) android.view.View.VISIBLE else android.view.View.GONE

        // Botones inferior
        binding.btnSaveChanges.visibility = if (enable) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnCancelChanges.visibility = if (enable) android.view.View.VISIBLE else android.view.View.GONE

        // Rol: TextView vs Spinner
        binding.tvUserRole.visibility = if (enable) android.view.View.GONE else android.view.View.VISIBLE
        binding.spinnerRole.visibility = if (enable) android.view.View.VISIBLE else android.view.View.GONE

        // Campos editables
        setEditable(binding.etFullName, enable)
        setEditable(binding.etEmail, enable)
        setEditable(binding.etBirthdate, enable, isDateField = true)

        // El badge de estado solo se puede tocar en modo edición
        binding.tvUserStatus.isClickable = enable
    }

    private fun setEditable(
        editText: android.widget.EditText,
        editable: Boolean,
        isDateField: Boolean = false
    ) {
        editText.isEnabled = editable
        editText.isFocusable = editable && !isDateField
        editText.isFocusableInTouchMode = editable && !isDateField
        editText.isClickable = editable && isDateField

        if (editable) {
            editText.setBackgroundResource(R.drawable.bg_input)
            editText.setTextColor(getColor(R.color.dark_blue))
        } else {
            editText.setBackgroundResource(android.R.color.transparent)
            editText.setTextColor(getColor(R.color.dark_blue))
        }
    }

    // -------------------------------------------------------------
    // Spinner de rol
    // -------------------------------------------------------------
    private fun setupRoleSpinner() {
        val roles = arrayOf("Médico", "Administrador")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        binding.spinnerRole.adapter = adapter
    }

    // -------------------------------------------------------------
    // DatePicker para fecha de nacimiento
    // -------------------------------------------------------------
    private fun setupBirthdatePicker() {
        binding.etBirthdate.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }

        datePicker.addOnPositiveButtonClickListener { millis ->
            val date = Date(millis)
            binding.etBirthdate.setText(dateFormat.format(date))
        }
    }

    // -------------------------------------------------------------
    // Guardar cambios en Firestore
    // -------------------------------------------------------------
    private fun saveChanges() {
        val id = userId ?: return

        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val birthdate = binding.etBirthdate.text.toString().trim()
        val roleSelected = binding.spinnerRole.selectedItem.toString()

        val nameParts = fullName.split(" ")
        val name = nameParts.firstOrNull().orEmpty()
        val lastname = nameParts.drop(1).joinToString(" ")

        val updates = mapOf(
            "name" to name,
            "lastname" to lastname,
            "email" to email,
            "birthdate" to birthdate,
            "role" to roleSelected,
            "active" to currentActive
        )

        db.collection("users").document(id)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
                binding.tvUserRole.text = roleSelected
                originalRole = roleSelected
                originalActive = currentActive
                toggleEditMode(false)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar cambios", Toast.LENGTH_SHORT).show()
            }
    }

    // -------------------------------------------------------------
    // Borrar usuario con confirmación
    // -------------------------------------------------------------
    private fun confirmDeleteUser() {
        val id = userId ?: return

        AlertDialog.Builder(this)
            .setTitle("Eliminar usuario")
            .setMessage("¿Estás seguro de que quieres eliminar a este usuario?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("users").document(id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Usuario eliminado", Toast.LENGTH_SHORT).show()
                        finish()  // Volver a la lista
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al eliminar usuario", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // -------------------------------------------------------------
    // Badge Activo / Inactivo
    // -------------------------------------------------------------
    private fun updateStatusBadge() {
        if (currentActive) {
            binding.tvUserStatus.text = getString(R.string.estado_activo)
            binding.tvUserStatus.setBackgroundResource(R.drawable.bg_status_active)
        } else {
            binding.tvUserStatus.text = getString(R.string.estado_inactivo)
            binding.tvUserStatus.setBackgroundResource(R.drawable.bg_status_inactive)
        }
    }
}