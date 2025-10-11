package com.lhc.tfg_prediccion.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.lhc.tfg_prediccion.data.model.LoggedInUser
import java.io.IOException

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun login(username: String, password: String, callback: (Result<LoggedInUser>) -> Unit) {
        auth.signInWithEmailAndPassword(username, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        val userId = user.uid
                        // consulta a la base de datos para obtener campo 'name'
                        db.collection("users").document(userId).get().
                            addOnCompleteListener() { firestoreTask ->
                                if (firestoreTask.isSuccessful){
                                    val document: DocumentSnapshot = firestoreTask.result
                                    val name = document.getString("name")?:"Anonimo"
                                    val userUid = document.getString("uid")?: "Anonimo"
                                    val role = document.getString("role")?: "Médico"
                                    val loggedInUser = LoggedInUser(userUid, name, role)
                                    callback(Result.Success(loggedInUser))
                                } else{
                                    // error al obtener 'name' de la base de datos
                                    callback(Result.Error(Exception("Error al obtener los datos del usuario desde Firestore")))
                                }
                            }
                    } else {
                        callback(Result.Error(Exception("Autenticación incorrecta")))
                    }
                } else {
                    val exception = task.exception
                    val errorResult = when (exception) {
                        is FirebaseAuthInvalidCredentialsException -> Result.Error(Exception("Correo o contraseña incorrectos"))
                        is FirebaseAuthInvalidUserException -> Result.Error(Exception("Este usuario no existe"))
                        else -> Result.Error(Exception("Error de autenticación: ${exception?.message}"))
                    }
                    callback(errorResult)
                }
            }
    }


    fun logout() {
        auth.signOut()
    }
}