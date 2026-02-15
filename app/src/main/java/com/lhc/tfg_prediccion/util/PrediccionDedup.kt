package com.lhc.tfg_prediccion.util

import java.security.MessageDigest

object PrediccionDedup {

    fun docId(
        predictionMode: String,
        edad: String,
        femenino: String,
        capnometria: String,
        causaCardiaca: String,
        cardioManual: String,
        recPulso: String,
        valido: String
    ): String {

        // Clave canónica EXACTA (sin normalización)
        val canonical = listOf(
            predictionMode,
            edad,
            femenino,
            capnometria,
            causaCardiaca,
            cardioManual,
            recPulso,
            valido
        ).joinToString("|")

        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))

        val hex = bytes.joinToString("") { "%02x".format(it) }

        return "pred_v2_$hex"
    }
}