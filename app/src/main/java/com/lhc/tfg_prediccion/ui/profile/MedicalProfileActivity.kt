package com.lhc.tfg_prediccion.ui.profile

import android.os.Bundle
import android.view.View
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
import com.lhc.tfg_prediccion.ui.prediction.modeFromLabelLoose
import com.lhc.tfg_prediccion.ui.prediction.modeToLabel
import com.lhc.tfg_prediccion.ui.util.HistorialFilter
import com.lhc.tfg_prediccion.ui.util.mapResultado
import com.lhc.tfg_prediccion.ui.util.mapSexo
import com.lhc.tfg_prediccion.ui.util.matchesFilter
import com.lhc.tfg_prediccion.ui.util.showFilterDialog
import com.lhc.tfg_prediccion.ui.util.showSortDialog
import com.lhc.tfg_prediccion.util.PdfPrediction
import com.lhc.tfg_prediccion.util.PredictionCsvExporter
import com.lhc.tfg_prediccion.util.generatePredictionPdf
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

    private var originalName: String = ""
    private var originalLastname: String = ""
    private var originalEmail: String = ""
    private var originalBirthdate: String = ""

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val datePicker by lazy {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecciona una fecha")
            .build()
    }

    private val predictions = mutableListOf<Prediccion>()
    private var currentFilter: HistorialFilter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicalProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }

        userId = intent.getStringExtra("userId")
        if (userId == null) {
            Toast.makeText(this, "Usuario no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRoleSpinner()
        setupBirthdatePicker()
        toggleEditMode(false)

        loadUserData()
        loadPredictions()

        binding.tvUserStatus.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            currentActive = !currentActive
            updateStatusBadge()
        }

        binding.btnEditUser.setOnClickListener {
            toggleEditMode(!isEditMode)
        }

        binding.btnDeleteUser.setOnClickListener {
            if (hasChanges()) {
                confirmDiscardChanges()
            } else {
                loadUserData()
                toggleEditMode(false)
            }
        }

        binding.btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        binding.btnCancelChanges.setOnClickListener {
            confirmDeleteUser()
        }

        binding.btnSort.setOnClickListener {
            showSortDialog(
                activity = this,
                baseListProvider = { getFilteredList() }
            ) { listaOrdenada ->
                renderPredictions(listaOrdenada)
            }
        }

        binding.btnFilter.setOnClickListener {
            showFilterDialog(
                activity = this,
                currentFilter = currentFilter
            ) { nuevoFiltro ->
                currentFilter = nuevoFiltro
                renderPredictions(getFilteredList())
            }
        }

        binding.btnExport.setOnClickListener {
            val doctorName = "${binding.etName.text} ${binding.etLastname.text}".trim()
            PredictionCsvExporter.exportCsv(
                activity = this,
                predictions = getFilteredList(),
                doctorName = doctorName,
                userUid = userId
            )
        }
    }

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
                val email = doc.getString("email").orEmpty()
                val birthdate = doc.getString("birthdate").orEmpty()
                val role = doc.getString("role") ?: "Médico"
                originalRole = role

                val active = doc.getBoolean("active") ?: true
                originalActive = active
                currentActive = active

                val birthdateDisplay =
                    if (birthdate.isBlank()) getString(R.string.sin_fecha) else birthdate

                binding.etName.setText(name)
                binding.etLastname.setText(lastname)
                binding.etEmail.setText(email)
                binding.etBirthdate.setText(birthdateDisplay)

                binding.tvUserRole.text = role
                binding.spinnerRole.setSelection(
                    if (role == "Administrador") 1 else 0
                )

                originalName = name
                originalLastname = lastname
                originalEmail = email
                originalBirthdate = birthdateDisplay

                updateStatusBadge()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar el usuario", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadPredictions() {
        val id = userId ?: return

        db.collection("predicciones")
            .whereEqualTo("uid_medico", id)
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents
                predictions.clear()
                predictions.addAll(docs.mapNotNull { it.toObject(Prediccion::class.java) })
                renderPredictions(getFilteredList())
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Error al cargar las predicciones", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFilteredList(): List<Prediccion> {
        val filter = currentFilter ?: return predictions.toList()
        return predictions.filter { it.matchesFilter(filter) }
    }

    private fun dashIfBlank(s: String?): String =
        if (s.isNullOrBlank()) "—" else s

    private fun formatIndice(d: Double?): String =
        if (d == null) "—" else String.format(Locale.US, "%.3f", d)

    private fun renderPredictions(list: List<Prediccion>) {
        val table = binding.tablePredictions

        if (table.childCount > 1) {
            table.removeViews(1, table.childCount - 1)
        }

        binding.tvShowingRows.text = "Mostrando ${list.size} filas"

        if (list.isEmpty()) {
            binding.tvNoPredictions.visibility = View.VISIBLE
            binding.scrollPredictions.visibility = View.GONE
            return
        } else {
            binding.tvNoPredictions.visibility = View.GONE
            binding.scrollPredictions.visibility = View.VISIBLE
        }

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

        list.forEach { pred ->
            val fila = TableRow(this)

            val mode = pred.prediction_mode
                ?: modeFromLabelLoose(pred.momento_prediccion_legible ?: "")
            val canonicalMoment = modeToLabel(mode)
            val indiceStr = formatIndice(pred.indice)

            fila.addCell(contador.toString())
            fila.addCell(dashIfBlank(pred.edad))
            fila.addCell(mapSexo(pred.femenino))
            fila.addCell(dashIfBlank(pred.capnometria))
            fila.addCell(dashIfBlank(pred.causa_cardiaca))
            fila.addCell(dashIfBlank(pred.cardio_manual))
            fila.addCell(dashIfBlank(pred.rec_pulso))
            fila.addCell(canonicalMoment)
            fila.addCell(mapResultado(pred.valido))
            fila.addCell(indiceStr)

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

                setOnClickListener {
                    val doctorName = "${binding.etName.text} ${binding.etLastname.text}"
                        .trim()
                        .ifBlank { "Desconocido" }

                    generatePredictionPdf(
                        this@MedicalProfileActivity,
                        PdfPrediction(
                            doctorName = doctorName,
                            fecha = pred.fecha,
                            predictionMode = pred.prediction_mode,
                            momentoCanonico = canonicalMoment,
                            edad = pred.edad ?: "",
                            femenino = pred.femenino ?: "",
                            capnometria = pred.capnometria ?: "",
                            causaCardiaca = pred.causa_cardiaca ?: "",
                            cardioManual = pred.cardio_manual ?: "",
                            recPulso = pred.rec_pulso ?: "",
                            valido = pred.valido.equals("Si", ignoreCase = true),
                            indice = pred.indice
                        )
                    )
                }

                fila.addView(this)
            }

            table.addView(fila)
            contador++
        }

        adjustPredictionsHeight(list.size)
    }

    private fun toggleEditMode(enable: Boolean) {
        isEditMode = enable

        if (enable) {
            binding.spinnerRole.visibility = View.VISIBLE
            binding.tvUserRole.visibility = View.GONE
        } else {
            binding.tvUserRole.visibility = View.VISIBLE
            binding.spinnerRole.visibility = View.GONE
        }

        binding.btnEditUser.visibility = if (enable) View.GONE else View.VISIBLE
        binding.btnDeleteUser.visibility = if (enable) View.VISIBLE else View.GONE

        binding.btnSaveChanges.visibility = if (enable) View.VISIBLE else View.GONE
        binding.btnCancelChanges.visibility = if (enable) View.VISIBLE else View.GONE

        setEditable(binding.etName, enable)
        setEditable(binding.etLastname, enable)
        setEditable(binding.etEmail, enable, isSecondary = true)
        setEditable(binding.etBirthdate, enable, isDateField = true, isSecondary = true)

        binding.tvUserStatus.isClickable = enable
    }

    private fun setEditable(
        editText: android.widget.EditText,
        editable: Boolean,
        isDateField: Boolean = false,
        isSecondary: Boolean = false
    ) {
        editText.isEnabled = editable
        editText.isFocusable = editable && !isDateField
        editText.isFocusableInTouchMode = editable && !isDateField
        editText.isClickable = editable && isDateField

        val colorRes = if (isSecondary) R.color.grey else R.color.dark_blue

        val horizontalPadding = (16 * resources.displayMetrics.density).toInt()
        val verticalPadding = (8 * resources.displayMetrics.density).toInt()

        if (editable) {
            editText.setBackgroundResource(R.drawable.bg_profile_input)
            editText.setTextColor(getColor(colorRes))
            editText.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
            )
        } else {
            editText.setBackgroundResource(android.R.color.transparent)
            editText.setTextColor(getColor(colorRes))
            editText.setPadding(0, 0, 0, 0)
        }
    }

    private fun setupRoleSpinner() {
        val roles = arrayOf("Médico", "Administrador")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        binding.spinnerRole.adapter = adapter
    }

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

    private fun hasChanges(): Boolean {
        val name = binding.etName.text.toString().trim()
        val lastname = binding.etLastname.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val birthdateDisplay = binding.etBirthdate.text.toString().trim()
        val role = binding.spinnerRole.selectedItem.toString()

        return name != originalName ||
                lastname != originalLastname ||
                email != originalEmail ||
                birthdateDisplay != originalBirthdate ||
                role != originalRole ||
                currentActive != originalActive
    }

    private fun saveChanges() {
        val id = userId ?: return

        val name = binding.etName.text.toString().trim()
        val lastname = binding.etLastname.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val birthdate = binding.etBirthdate.text.toString().trim()
        val roleSelected = binding.spinnerRole.selectedItem.toString()

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
                originalName = name
                originalLastname = lastname
                originalEmail = email
                originalBirthdate = birthdate

                toggleEditMode(false)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar cambios", Toast.LENGTH_SHORT).show()
            }
    }

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
                        finish()
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

        val rowHeightDp = 48f
        val rowHeightPx = (rowHeightDp * metrics.density).toInt()

        val desiredHeightPx = rowCount * rowHeightPx
        val maxHeightPx = (metrics.heightPixels * 0.5f).toInt()
        val minHeightPx = (metrics.heightPixels * 0.25f).toInt()

        val lp = scroll.layoutParams
        lp.height = if (rowCount == 0) {
            minHeightPx
        } else {
            desiredHeightPx.coerceIn(minHeightPx, maxHeightPx)
        }

        scroll.layoutParams = lp
    }
}