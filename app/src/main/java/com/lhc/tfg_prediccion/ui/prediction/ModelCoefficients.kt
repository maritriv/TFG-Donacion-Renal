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

// Modos estándar
const val MODE_BEFORE = "BEFORE_RCP"
const val MODE_MID    = "MID_RCP"
const val MODE_AFTER  = "AFTER_RCP"

// Coeficientes por modo
val COEFS_BEFORE = ModelCoefficients(
    intercepto = 7.5,
    edad = -0.1058,
    sexoFemenino = -0.6187,
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

// ===================== Etiquetas canónicas para "Momento" =====================
const val LBL_BEFORE = "Antes del procedimiento de RCP"
const val LBL_MID    = "A los 20 minutos de iniciada la RCP"
const val LBL_AFTER  = "Después del procedimiento de RCP"

/** CÓDIGO -> frase canónica */
fun modeToLabel(mode: String?): String = when (mode) {
    MODE_BEFORE -> LBL_BEFORE
    MODE_MID    -> LBL_MID
    MODE_AFTER  -> LBL_AFTER
    else        -> LBL_AFTER // compatibilidad
}

/** Texto libre (CSV/antiguo) -> CÓDIGO (parser tolerante) */
fun modeFromLabelLoose(label: String?): String {
    val s = (label ?: "").trim().lowercase()
    return when {
        // ANTES
        "Antes" in s -> MODE_BEFORE

        // 20 MIN / MEDIO
        "20" in s || "min" in s || "medio" in s || "mitad" in s || "punto medio" in s -> MODE_MID

        // DESPUÉS
        "Después" in s -> MODE_AFTER

        else -> MODE_AFTER
    }
}

/** Cualquier texto viejo -> frase canónica */
fun normalizeLabel(anyText: String?): String = modeToLabel(modeFromLabelLoose(anyText))