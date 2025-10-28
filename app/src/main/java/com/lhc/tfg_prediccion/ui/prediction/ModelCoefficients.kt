package com.lhc.tfg_prediccion.ui.prediction

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

// Modos estándar (en inglés para evitar acentos/espacios en claves técnicas)
const val MODE_BEFORE = "BEFORE_RCP"
const val MODE_MID    = "MID_RCP"
const val MODE_AFTER  = "AFTER_RCP"

// Coeficientes por modo (según tu Excel)
val COEFS_BEFORE = ModelCoefficients(
    intercepto = 7.5,
    edad = -0.1058,
    sexoFemenino = -0.61867,
    capnometria = 0.0663,
    causaCardiaca = -0.2247,
    cardioManual = -2.8363,
    recuperacion = 1.9646,
    tiempoLlegadaInicioPcrMin = 0.0013,
    corte = 3.701
)

val COEFS_MID = ModelCoefficients(
    intercepto = 7.5,
    edad = -0.0465,
    sexoFemenino = -0.9870,
    capnometria = 0.0334,
    causaCardiaca = -0.5847,
    cardioManual = -2.0706,
    recuperacion = 1.2459,
    tiempoLlegadaInicioPcrMin = -0.0045,
    corte = 4.827
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

fun coefsFor(mode: String): ModelCoefficients = when (mode) {
    MODE_BEFORE -> COEFS_BEFORE
    MODE_MID    -> COEFS_MID
    MODE_AFTER  -> COEFS_AFTER
    else        -> COEFS_AFTER
}

/** Devuelve Pair(esValido, indice) */
fun calcularIndiceYValidez(
    mode: String,
    edad: Int,
    femenino: Int,       // 0/1
    capnometria: Int,
    causacardiaca: Int,  // 0/1
    cardiomanual: Int,   // 0/1
    recuperacion: Int,   // 0/1
    tiempoMin: Int = 0
): Pair<Boolean, Double> {
    val c = coefsFor(mode)
    val indice =
        c.intercepto +
                (edad * c.edad) +
                (femenino * c.sexoFemenino) +
                (capnometria * c.capnometria) +
                (causacardiaca * c.causaCardiaca) +
                (cardiomanual * c.cardioManual) +
                (recuperacion * c.recuperacion) +
                (tiempoMin * c.tiempoLlegadaInicioPcrMin)

    val esValido = indice >= c.corte
    return esValido to indice
}
