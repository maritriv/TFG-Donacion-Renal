package com.lhc.tfg_prediccion.ui.prediction

// ===================== MODOS =====================
const val MODE_MID    = "MID_RCP"
const val MODE_AFTER  = "AFTER_RCP"

// ===================== MODELO AFTER =========================

data class ModelCoefficients(
    val intercepto: Double,
    val edad: Double,
    val sexoFemenino: Double,
    val capnometria: Double,
    val causaCardiaca: Double,
    val cardioManual: Double,
    val recuperacion: Double,
    val tiempoLlegadaInicioPcrMin: Double,
    val corte: Double
)

val COEFS_AFTER = ModelCoefficients(
    intercepto = 7.5,
    edad = -0.0959,
    sexoFemenino = -1.1558,
    capnometria = 0.0807,
    causaCardiaca = -0.5245,
    cardioManual = -2.6349,
    recuperacion = 2.8971,
    tiempoLlegadaInicioPcrMin = -0.0003,
    corte = 4.625
)

// ===================== MODELO MITAD (20 MIN) =====================

data class InputsMitad(
    val edad: Int,
    val femenino: Int,
    val capnometria: Int,
    val causaCardiaca: Int,
    val cardioManual: Int,
    val recuperacion: Int
)

data class CoefsMitad(
    val intercepto: Double,
    val capnometria: Double,
    val edad: Double,
    val sexoFemenino: Double,
    val causaCardiaca: Double,
    val cardioManual: Double,
    val recuperacion: Double,
    val corte: Double
)

val COEFS_MITAD = CoefsMitad(
    intercepto = 7.5,
    capnometria = 0.03701583,
    edad = -0.0510799,
    sexoFemenino = -0.82780951,
    causaCardiaca = -0.50187099,
    cardioManual = -2.0621372,
    recuperacion = 1.29618296,
    corte = 5.6071538
)

fun calcularMitad(i: InputsMitad): Pair<Boolean, Double> {
    val c = COEFS_MITAD

    val indice =
        c.intercepto +
                (i.capnometria * c.capnometria) +
                (i.edad * c.edad) +
                (i.femenino * c.sexoFemenino) +
                (i.causaCardiaca * c.causaCardiaca) +
                (i.cardioManual * c.cardioManual) +
                (i.recuperacion * c.recuperacion)

    return (indice >= c.corte) to indice
}

// ===================== AFTER =====================

data class InputsAfter(
    val edad: Int,
    val femenino: Int,
    val capnometria: Int,
    val causaCardiaca: Int,
    val cardioManual: Int,
    val recuperacion: Int,
    val tiempoMin: Int = 0
)

fun calcularAfter(i: InputsAfter): Pair<Boolean, Double> {
    val c = COEFS_AFTER

    val indice =
        c.intercepto +
                (i.edad * c.edad) +
                (i.femenino * c.sexoFemenino) +
                (i.capnometria * c.capnometria) +
                (i.causaCardiaca * c.causaCardiaca) +
                (i.cardioManual * c.cardioManual) +
                (i.recuperacion * c.recuperacion) +
                (i.tiempoMin * c.tiempoLlegadaInicioPcrMin)

    return (indice >= c.corte) to indice
}

// ===================== API GENERAL =====================

fun calcularIndiceYValidez(
    mode: String,
    mitad: InputsMitad? = null,
    after: InputsAfter? = null
): Pair<Boolean, Double> {

    return when (mode) {
        MODE_MID -> calcularMitad(requireNotNull(mitad))
        else -> calcularAfter(requireNotNull(after))
    }
}

// ===================== ETIQUETAS =====================

const val LBL_MID = "Mitad del procedimiento de RCP (20 min)"
const val LBL_AFTER = "Después del procedimiento de RCP"

fun modeToLabel(mode: String?): String = when (mode) {
    MODE_MID -> LBL_MID
    else -> LBL_AFTER
}

fun modeFromLabelLoose(label: String?): String {
    val s = (label ?: "").lowercase()

    return when {
        "20" in s || "medio" in s || "mitad" in s -> MODE_MID
        else -> MODE_AFTER
    }
}