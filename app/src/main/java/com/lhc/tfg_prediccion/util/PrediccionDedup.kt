package com.lhc.tfg_prediccion.util

import com.lhc.tfg_prediccion.ui.prediction.MODE_AFTER
import com.lhc.tfg_prediccion.ui.prediction.MODE_BEFORE
import com.lhc.tfg_prediccion.ui.prediction.MODE_MID
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
        valido: String,
        colesterol: String? = null,
        adrenalinaN: String? = null,
        imc: String? = null
    ): String {

        val canonical = when (predictionMode) {

            // =========================
            // INICIO (BEFORE)
            // =========================
            MODE_BEFORE -> listOf(
                predictionMode,
                edad,
                capnometria,
                colesterol ?: "",
                valido
            )

            // =========================
            // MITAD (MID)
            // =========================
            MODE_MID -> listOf(
                predictionMode,
                capnometria,
                colesterol ?: "",
                adrenalinaN ?: "",
                imc ?: "",
                causaCardiaca,
                cardioManual,
                valido
            )

            // =========================
            // AFTER (modelo antiguo)
            // =========================
            else -> listOf(
                predictionMode,
                edad,
                femenino,
                capnometria,
                causaCardiaca,
                cardioManual,
                recPulso,
                valido
            )
        }.joinToString("|")

        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))

        val hex = bytes.joinToString("") { "%02x".format(it) }

        return "pred_v3_$hex"
    }
}