package com.lhc.tfg_prediccion.ui.login

import android.app.Activity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import android.content.Intent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.databinding.ActivityLoginBinding
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.ui.main.MainActivity
import com.lhc.tfg_prediccion.ui.main.MainAdmin
import com.lhc.tfg_prediccion.ui.register.RegisterActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this) // inicialización Firebase

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loading = binding.loading

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory())
            .get(LoginViewModel::class.java)

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            login.isEnabled = loginState.isDataValid

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            loading.visibility = View.GONE
            if (loginResult.error != null) {
                showLoginFailed()
            }
            if (loginResult.success != null) {
                updateUiWithUser(loginResult.success)
            }
            setResult(Activity.RESULT_OK)
        })

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString(),
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                val userText = username.text.toString().trim()
                val passwText = password.text.toString().trim()
                if (userText.isEmpty() || passwText.isEmpty()) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.toast_enter_credentials),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    loading.visibility = View.VISIBLE
                    loginViewModel.login(
                        username.text.toString(),
                        password.text.toString()
                    )
                }
            }
        }

        binding.registerButton?.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Comprobamos el campo "active" en Firestore
     * antes de dejar entrar al usuario.
     */
    private fun updateUiWithUser(model: LoggedInUserView) {
        val displayName = model.displayName
        val userUid = model.userUid
        val role = model.role

        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .document(userUid)
            .get()
            .addOnSuccessListener { doc ->

                // Leemos el campo "active"
                val rawActive = doc.get("active")

                val isActive = when (rawActive) {
                    is Boolean -> rawActive
                    is Number -> rawActive.toInt() != 0
                    is String -> rawActive.equals("true", true) ||
                            rawActive.equals("yes", true) ||
                            rawActive == "1"
                    null -> true   // si no existe, por compatibilidad dejamos entrar
                    else -> true
                }

                if (!isActive) {
                    // Cuenta desactivada -> no dejamos entrar
                    Toast.makeText(
                        this@LoginActivity,
                        "Your account is deactivated. Please contact the administrator.",
                        Toast.LENGTH_LONG
                    ).show()

                    FirebaseAuth.getInstance().signOut()
                    return@addOnSuccessListener
                }

                // ---- Usuario ACTIVO: flujo normal ----
                val welcome = getString(R.string.welcome_generic)
                Toast.makeText(
                    applicationContext,
                    "$welcome $displayName",
                    Toast.LENGTH_LONG
                ).show()

                // Navegación según el rol
                if (role == "Médico") {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("userName", displayName)
                    intent.putExtra("userUid", userUid)
                    startActivity(intent)
                    finish()
                } else {
                    val intentAdmin = Intent(this, MainAdmin::class.java)
                    intentAdmin.putExtra("userName", displayName)
                    intentAdmin.putExtra("userUid", userUid)
                    startActivity(intentAdmin)
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this@LoginActivity,
                    "Error checking account status.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showLoginFailed() {
        Toast.makeText(
            applicationContext,
            getString(R.string.login_incorrect),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}