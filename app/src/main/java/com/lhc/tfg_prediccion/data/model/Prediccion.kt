package com.lhc.tfg_prediccion.data.model

import com.google.firebase.Timestamp

data class Prediccion(
    val edad: String = "",
    val femenino: String = "",
    val capnometria: String = "",
    val causa_cardiaca: String = "",
    val cardio_manual: String = "",
    val rec_pulso: String= "",
    val valido: String = "",
    val uid_medico: String = "",
    val fecha: Timestamp = Timestamp.now()
)
