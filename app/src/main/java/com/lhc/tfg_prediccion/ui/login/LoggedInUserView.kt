package com.lhc.tfg_prediccion.ui.login

/**
 * User details post authentication that is exposed to the UI
 */
data class LoggedInUserView(
    val displayName: String,
    val userUid: String,
    val role: String
)