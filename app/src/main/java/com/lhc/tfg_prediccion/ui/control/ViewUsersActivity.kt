package com.lhc.tfg_prediccion.ui.control

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.databinding.ActivityAdminUsersBinding
import java.text.Normalizer

class ViewUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminUsersBinding
    private lateinit var usersAdapter: UserListAdapter
    private val db = FirebaseFirestore.getInstance()

    // Tipos de usuario
    private enum class UserType { MEDICO, ADMIN }

    private var currentType: UserType = UserType.MEDICO

    // Lista completa cargada de Firestore para el tipo actual
    private var fullUserList: List<UserItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ---------- Navegación: volver ----------
        binding.backRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }

        // ---------- RecyclerView ----------
        usersAdapter = UserListAdapter { user -> onStatusToggle(user) }
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(this@ViewUsersActivity)
            adapter = usersAdapter
        }

        // ---------- Switch Médicos / Administradores ----------
        setupUserTypeSwitch()

        // ---------- Buscador ----------
        setupSearch()
    }

    // ---------------------------------------------------------
    // Switch de tipo de usuario
    // ---------------------------------------------------------
    private fun setupUserTypeSwitch() {

        fun applyType(type: UserType) {
            currentType = type

            // Actualizar selección visual del switch
            binding.tvSwitchMedicos.isSelected = (type == UserType.MEDICO)
            binding.tvSwitchAdmins.isSelected = (type == UserType.ADMIN)

            // Cambiar título grande
            binding.tvUsersTitle.text = when (type) {
                UserType.MEDICO -> getString(R.string.medicos_mayus)
                UserType.ADMIN  -> getString(R.string.administradores_mayus)
            }

            // Cargar usuarios reales desde Firestore
            loadUsersForType(type)
        }

        // Listeners del switch
        binding.tvSwitchMedicos.setOnClickListener { applyType(UserType.MEDICO) }
        binding.tvSwitchAdmins.setOnClickListener { applyType(UserType.ADMIN) }

        // Estado inicial
        applyType(UserType.MEDICO)
    }

    // ---------------------------------------------------------
    // Carga de usuarios reales desde Firestore
    // ---------------------------------------------------------
    private fun loadUsersForType(type: UserType) {
        val roleLabel = when (type) {
            UserType.MEDICO -> "Médico"
            UserType.ADMIN  -> "Administrador"
        }

        db.collection("users")
            .whereEqualTo("role", roleLabel)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.map { doc ->
                    val id = doc.id

                    val name = doc.getString("name").orEmpty().trim()
                    val lastname = doc.getString("lastname").orEmpty().trim()
                    val fullName = listOf(name, lastname)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")

                    val email = doc.getString("email").orEmpty()

                    // Campo "active" en inglés (con compatibilidad con "activo")
                    val rawActive = doc.get("active") ?: doc.get("activo")
                    val isActive = when (rawActive) {
                        is Boolean -> rawActive
                        is Number  -> rawActive.toInt() != 0
                        is String  -> rawActive.equals("true", true) ||
                                rawActive.equals("yes", true) ||
                                rawActive.equals("si", true) ||
                                rawActive.equals("sí", true) ||
                                rawActive == "1"
                        else       -> true   // si no existe, lo consideramos activo
                    }

                    UserItem(
                        id = id,
                        fullName = fullName.ifBlank { email },
                        email = email,
                        isActive = isActive
                    )
                }

                fullUserList = list
                applySearch(binding.searchInput.text?.toString().orEmpty())
            }
            .addOnFailureListener {
                fullUserList = emptyList()
                usersAdapter.submitList(fullUserList)
            }
    }

    // ---------------------------------------------------------
    // Buscador (nombre, apellidos o email)
    // ---------------------------------------------------------
    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applySearch(s?.toString().orEmpty())
            }
        })
    }

    private fun applySearch(query: String) {
        if (query.isBlank()) {
            usersAdapter.submitList(fullUserList)
            return
        }

        val q = norm(query)
        val filtered = fullUserList.filter { user ->
            val nameNorm = norm(user.fullName)
            val emailNorm = norm(user.email)
            nameNorm.contains(q) || emailNorm.contains(q)
        }

        usersAdapter.submitList(filtered)
    }

    private fun norm(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        return n.replace("\\p{Mn}+".toRegex(), "").lowercase()
    }

    // ---------------------------------------------------------
    // Cambiar estado Activo / Inactivo (toggle)
    // ---------------------------------------------------------
    private fun onStatusToggle(user: UserItem) {
        val newState = !user.isActive

        db.collection("users")
            .document(user.id)
            .update("active", newState)
            .addOnSuccessListener {
                // Actualizamos la lista en memoria
                fullUserList = fullUserList.map {
                    if (it.id == user.id) it.copy(isActive = newState) else it
                }
                // Volvemos a aplicar el filtro del buscador
                applySearch(binding.searchInput.text?.toString().orEmpty())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cambiar estado", Toast.LENGTH_SHORT).show()
            }
    }
}