package com.lhc.tfg_prediccion.ui.profile

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.data.model.Prediccion
import com.lhc.tfg_prediccion.databinding.ActivityMedicalProfileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.ViewGroup
import kotlin.math.min


class MedicalProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicalProfileBinding
    private val db = FirebaseFirestore.getInstance()

    private var isEditMode = false
    private var userId: String? = null
    private var originalActive: Boolean = true
    private var currentActive: Boolean = true
    private var originalRole: String = "Médico"

    // Valores originales para detectar cambios
    private var originalFullName: String = ""
    private var originalEmail: String = ""
    private var originalBirthdate: String = ""

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

        // Arrancamos en modo SOLO LECTURA
        toggleEditMode(false)

        // Datos del usuario + predicciones
        loadUserData()
        loadPredictions()

        // Cambiar estado Activo / Inactivo solo en modo edición
        binding.tvUserStatus.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            currentActive = !currentActive
            updateStatusBadge()
        }

        // Lápiz: entrar / salir de modo edición
        binding.btnEditUser.setOnClickListener {
            toggleEditMode(!isEditMode)
        }

        // X arriba a la derecha:
        //   - Si hay cambios → preguntar si quiere descartarlos
        //   - Si no hay cambios → salir directamente del modo edición
        binding.btnDeleteUser.setOnClickListener {
            if (hasChanges()) {
                confirmDiscardChanges()
            } else {
                loadUserData()
                toggleEditMode(false)
            }
        }

        // Guardar cambios
        binding.btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        // Botón inferior: eliminar usuario
        binding.btnCancelChanges.setOnClickListener {
            confirmDeleteUser()
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

                val birthdateDisplay =
                    if (birthdate.isBlank()) getString(R.string.sin_fecha) else birthdate

                // Rellenamos vistas
                binding.etFullName.setText(fullName)
                binding.etEmail.setText(email)
                binding.etBirthdate.setText(birthdateDisplay)

                binding.tvUserRole.text = role
                binding.spinnerRole.setSelection(
                    if (role == "Administrador") 1 else 0
                )

                // Guardamos originales para detectar cambios
                originalFullName = fullName
                originalEmail = email
                originalBirthdate = birthdateDisplay

                updateStatusBadge()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar el usuario", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    // -------------------------------------------------------------
    // Cargar predicciones del médico y rellenar tabla
    // -------------------------------------------------------------
    private fun loadPredictions() {
        val id = userId ?: return

        val table = binding.tablePredictions

        // Eliminar filas de datos, mantener la cabecera (fila 0)
        if (table.childCount > 1) {
            table.removeViews(1, table.childCount - 1)
        }

        db.collection("predicciones")
            .whereEqualTo("uid_medico", id)
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents
                binding.tvShowingRows.text = "Mostrando ${docs.size} filas"

                var contador = 1
                val params = TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 16, 8, 16)
                }

                fun TableRow.addCell(text: String, bold: Boolean = false) {
                    TextView(this@MedicalProfileActivity).apply {
                        this.text = text
                        textSize = 12f
                        setPadding(16, 24, 16, 24)
                        typeface = android.graphics.Typeface.create(
                            "sans-serif-condensed",
                            if (bold) android.graphics.Typeface.BOLD
                            else android.graphics.Typeface.NORMAL
                        )
                        layoutParams = params
                        this@addCell.addView(this)
                    }
                }

                docs.forEach { doc ->
                    val pred = doc.toObject(Prediccion::class.java) ?: return@forEach

                    val fila = TableRow(this)

                    // #
                    fila.addCell(contador.toString())

                    // Edad
                    fila.addCell(pred.edad)

                    // Sexo: mapeamos femenino ("Si"/"No") a M / H
                    fila.addCell(mapSexo(pred.femenino))

                    // Capnografía
                    fila.addCell(pred.capnometria)

                    // Causa cardiaca
                    fila.addCell(pred.causa_cardiaca)

                    // Cardiocompresión
                    fila.addCell(pred.cardio_manual)

                    // Rec. del pulso
                    fila.addCell(pred.rec_pulso)

                    // Momento
                    val momento = pred.momento_prediccion_legible ?: ""
                    fila.addCell(momento)

                    // Resultado: "Si"/"No" -> "Válido"/"No válido"
                    fila.addCell(mapResultado(pred.valido))

                    // Informe (PDF) – de momento solo texto “PDF”
                    TextView(this).apply {
                        text = "PDF"
                        textSize = 12f
                        setPadding(16, 24, 16, 24)
                        layoutParams = params
                        setTextColor(
                            ContextCompat.getColor(
                                this@MedicalProfileActivity,
                                R.color.dark_blue
                            )
                        )
                        // TODO: aquí podrías poner un setOnClickListener para generar/abrir el PDF
                        fila.addView(this)
                    }

                    table.addView(fila)
                    contador++
                }
                adjustPredictionsHeight(docs.size)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Error al cargar las predicciones", Toast.LENGTH_SHORT).show()
            }
    }

    // Mapea el campo "femenino" almacenado ("Si"/"No", 1/0, true/false, etc.) a "M" / "H"
    private fun mapSexo(femenino: String?): String {
        val value = femenino?.trim()?.lowercase(Locale.getDefault()) ?: return ""
        return when (value) {
            "si", "1", "true", "f", "femenino", "mujer" -> "M" // Mujer
            "no", "0", "false", "masculino", "hombre" -> "H"  // Hombre
            else -> femenino ?: ""
        }
    }

    // Mapea "Si"/"No", 1/0... a "Válido" / "No válido"
    private fun mapResultado(valido: String?): String {
        val value = valido?.trim()?.lowercase(Locale.getDefault()) ?: return ""
        return when (value) {
            "si", "1", "true" -> "Válido"
            "no", "0", "false" -> "No válido"
            else -> valido ?: ""
        }
    }

    // -------------------------------------------------------------
    // Modo edición ON/OFF
    // -------------------------------------------------------------
    private fun toggleEditMode(enable: Boolean) {
        isEditMode = enable

        // Iconos
        binding.btnEditUser.visibility =
            if (enable) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnDeleteUser.visibility =
            if (enable) android.view.View.VISIBLE else android.view.View.GONE

        // Botones inferior
        binding.btnSaveChanges.visibility =
            if (enable) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnCancelChanges.visibility =
            if (enable) android.view.View.VISIBLE else android.view.View.GONE

        // Rol: TextView vs Spinner
        binding.tvUserRole.visibility =
            if (enable) android.view.View.GONE else android.view.View.VISIBLE
        binding.spinnerRole.visibility =
            if (enable) android.view.View.VISIBLE else android.view.View.GONE

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
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
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
    // Detectar si hay cambios sin guardar
    // -------------------------------------------------------------
    private fun hasChanges(): Boolean {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val birthdateDisplay = binding.etBirthdate.text.toString().trim()
        val role = binding.spinnerRole.selectedItem.toString()

        return fullName != originalFullName ||
                email != originalEmail ||
                birthdateDisplay != originalBirthdate ||
                role != originalRole ||
                currentActive != originalActive
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

                // Actualizamos originales
                originalRole = roleSelected
                originalActive = currentActive
                originalFullName = fullName
                originalEmail = email
                originalBirthdate = birthdate

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

        val dialog = AlertDialog.Builder(this)
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
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                ContextCompat.getDrawable(this, R.drawable.bg_panel_full_rounded)
            )
        }

        dialog.show()
    }

    // -------------------------------------------------------------
    // Confirmar descarte de cambios
    // -------------------------------------------------------------
    private fun confirmDiscardChanges() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Descartar cambios")
            .setMessage("¿Estás seguro de que quieres descartar los cambios realizados?")
            .setPositiveButton("Descartar") { _, _ ->
                loadUserData()
                toggleEditMode(false)
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

    private fun adjustPredictionsHeight(rowCount: Int) {
        val scroll = binding.scrollPredictions

        val metrics = resources.displayMetrics

        // Altura aproximada de cada fila en dp (ajústalo si hace falta)
        val rowHeightDp = 48f
        val rowHeightPx = (rowHeightDp * metrics.density).toInt()

        // Alto deseado en función del nº de filas
        val desiredHeightPx = rowCount * rowHeightPx

        // Alto máximo: por ejemplo el 40% de la pantalla
        val maxHeightPx = (metrics.heightPixels * 0.4f).toInt()

        val lp = scroll.layoutParams

        if (rowCount == 0) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            lp.height = min(desiredHeightPx, maxHeightPx)
        }

        scroll.layoutParams = lp
    }
}