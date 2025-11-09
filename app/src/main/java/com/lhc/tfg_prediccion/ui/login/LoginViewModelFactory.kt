package com.lhc.tfg_prediccion.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lhc.tfg_prediccion.data.LoginDataSource
import com.lhc.tfg_prediccion.data.LoginRepository

/**
 * Factory para instanciar LoginViewModel.
 * Necesaria porque LoginViewModel tiene un constructor con parámetros.
 */
class LoginViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(
                    loginRepository = LoginRepository(
                        dataSource = LoginDataSource()
                    )
                ) as T
            }
            else -> {
                throw IllegalArgumentException("Clase ViewModel desconocida: ${modelClass.name}")
            }
        }
    }
}
