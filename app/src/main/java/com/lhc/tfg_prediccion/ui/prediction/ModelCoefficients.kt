package com.lhc.tfg_prediccion.ui.prediction

// ===================== MODOS =====================
const val MODE_BEFORE = "BEFORE_RCP"
const val MODE_MID    = "MID_RCP"
const val MODE_AFTER  = "AFTER_RCP"

// ===================== AFTER=====================
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

// ===================== BEFORE - MIDDLE =====================

/** Inputs del INICIO */
data class InputsInicio(
    val edad: Int,
    val capnoInicio: Int,
    val colesterol: Int // 0/1
)

/** Inputs de la MITAD (20 min) */
data class InputsMitad(
    val capnoMedio: Int,
    val colesterol: Int,        // 0/1
    val adrenalinaN: Int,       // 0..n
    val causaCardiaca: Int,     // 0/1
    val imc: Double,            // ej 24.7
    val cardioExtraManual: Int  // 0/1 (Manual=1)
)

/** Inputs del AFTER (modelo antiguo) */
data class InputsAfter(
    val edad: Int,
    val femenino: Int,       // 0/1
    val capnometria: Int,
    val causaCardiaca: Int,  // 0/1
    val cardioManual: Int,   // 0/1
    val recuperacion: Int,   // 0/1
    val tiempoMin: Int = 0
)

// ===================== COEFICIENTES NUEVOS =====================

data class CoefsInicio(
    val intercepto: Double,
    val capnoInicio: Double,
    val edad: Double,
    val colesterol: Double,
    val corte: Double
)

data class CoefsMitad(
    val intercepto: Double,
    val cardioExtraManual: Double,
    val colesterol: Double,
    val adrenalinaN: Double,
    val causaCardiaca: Double,
    val imc: Double,
    val capnoMedio: Double,
    val corte: Double
)

val COEFS_INICIO = CoefsInicio(
    intercepto = 7.5,
    capnoInicio = 0.04906541,
    edad = -0.06570387,
    colesterol = -1.24382604,
    corte = 5.2609689
)

val COEFS_MITAD = CoefsMitad(
    intercepto = 7.5,
    cardioExtraManual = -1.83461963,
    colesterol = -2.02736788,
    adrenalinaN = -1.09360374,
    causaCardiaca = -0.12585945,
    imc = -0.20158305,
    capnoMedio = 0.03149219,
    corte = 3.0003088
)

// ===================== CÁLCULO POR MOMENTO =====================

fun calcularInicio(i: InputsInicio): Pair<Boolean, Double> {
    val c = COEFS_INICIO
    val indice = c.intercepto +
            (i.capnoInicio * c.capnoInicio) +
            (i.edad * c.edad) +
            (i.colesterol * c.colesterol)

    return (indice >= c.corte) to indice
}

fun calcularMitad(i: InputsMitad): Pair<Boolean, Double> {
    val c = COEFS_MITAD
    val indice = c.intercepto +
            (i.cardioExtraManual * c.cardioExtraManual) +
            (i.colesterol * c.colesterol) +
            (i.adrenalinaN * c.adrenalinaN) +
            (i.causaCardiaca * c.causaCardiaca) +
            (i.imc * c.imc) +
            (i.capnoMedio * c.capnoMedio)

    return (indice >= c.corte) to indice
}

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

/**
 * API común por si te viene bien mantener una sola llamada desde el resto de la app.
 * Llamas según el modo con el data class correspondiente.
 */
fun calcularIndiceYValidez(
    mode: String,
    inicio: InputsInicio? = null,
    mitad: InputsMitad? = null,
    after: InputsAfter? = null
): Pair<Boolean, Double> {
    return when (mode) {
        MODE_BEFORE -> calcularInicio(requireNotNull(inicio) { "Faltan valores de entrada" })
        MODE_MID    -> calcularMitad(requireNotNull(mitad) { "Faltan valores de entrada" })
        else        -> calcularAfter(requireNotNull(after) { "Faltan valores de entrada" })
    }
}

// ===================== Etiquetas canónicas para "Momento" =====================
const val LBL_BEFORE = "Inicio del procedimiento de RCP"
const val LBL_MID    = "Mitad del procedimiento de RCP (20 min)"
const val LBL_AFTER  = "Después del procedimiento de RCP"

fun modeToLabel(mode: String?): String = when (mode) {
    MODE_BEFORE -> LBL_BEFORE
    MODE_MID    -> LBL_MID
    MODE_AFTER  -> LBL_AFTER
    else        -> LBL_AFTER
}

fun modeFromLabelLoose(label: String?): String {
    val s = (label ?: "").trim().lowercase()
    return when {
        "antes" in s || "inicio" in s -> MODE_BEFORE
        "20" in s || "min" in s || "medio" in s || "mitad" in s || "punto medio" in s -> MODE_MID
        "después" in s || "despues" in s -> MODE_AFTER
        else -> MODE_AFTER
    }
}