package com.lhc.tfg_prediccion.ui.prediction

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lhc.tfg_prediccion.databinding.ActivityPredictionBinding
import com.lhc.tfg_prediccion.util.PrediccionDedup

class PredictionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictionBinding
    private var name: String? = null
    private var userUid: String? = null
    private var mode: String = MODE_AFTER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Modo que llega desde la pantalla de selección
        mode = intent.getStringExtra(PredictionModeActivity.EXTRA_MODE) ?: MODE_AFTER
        Log.d("PRED", "mode recibido = $mode")

        val textoModo = when (mode) {
            MODE_BEFORE -> "Inicio de la RCP"
            MODE_MID    -> "Mitad de la RCP (20 min)"
            else        -> "Después de la RCP"
        }
        Toast.makeText(this, "Modo seleccionado: $textoModo", Toast.LENGTH_SHORT).show()

        // Hint capnografía según modo
        binding.tilCapnometria.hint = when (mode) {
            MODE_BEFORE -> "Capnometría (inicio)"
            MODE_MID    -> "Capnometría (punto medio)"
            else        -> "Capnometría (transferencia)"
        }

        // Spinners Si/No
        val opcionesSiNo = arrayOf("Si", "No")
        val adapterSiNo = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSiNo)
        binding.spCausaCardiaca.adapter = adapterSiNo
        binding.spRecuperacion.adapter = adapterSiNo
        binding.spColesterol.adapter = adapterSiNo

        // Spinner cardiocompresión: Mecánica / Manual
        val opcionesCardio = arrayOf("Mecánica", "Manual")
        val adapterCardio = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesCardio)
        binding.spCardioManual.adapter = adapterCardio

        // Sexo
        val opcionesSexo = arrayOf("Hombre", "Mujer")
        val adapterSexo = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSexo)
        binding.spSexoFemenino.adapter = adapterSexo

        // Datos del médico
        name = intent.getStringExtra("userName")
        userUid = intent.getStringExtra("userUid")

        // Mostrar/ocultar según modo
        configurarVistaPorModo()

        // Botón volver
        binding.btnBack.setOnClickListener {
            val i = Intent(this, PredictionModeActivity::class.java)
            i.putExtra("userName", name)
            i.putExtra("userUid", userUid)
            startActivity(i)
            finish()
        }

        // Botón realizar predicción
        binding.btnPredic.setOnClickListener { onPredictClicked() }
    }

    private fun configurarVistaPorModo() {

        val isBefore = (mode == MODE_BEFORE)
        val isMid    = (mode == MODE_MID)
        val isAfter  = (mode == MODE_AFTER)

        // -------------------------
        // EDAD
        // BEFORE + AFTER (MID no)
        // -------------------------
        binding.tilEdad.visibility = if (isMid) View.GONE else View.VISIBLE

        // -------------------------
        // SEXO
        // SOLO AFTER (formato antiguo)
        // -------------------------
        binding.tvSexo.visibility = if (isAfter) View.VISIBLE else View.GONE
        binding.containerSexo.visibility = if (isAfter) View.VISIBLE else View.GONE

        // -------------------------
        // CAPNOMETRÍA
        // siempre visible (solo cambia hint arriba)
        // -------------------------
        binding.tilCapnometria.visibility = View.VISIBLE

        // -------------------------
        // COLESTEROL
        // BEFORE + MID, AFTER NO
        // -------------------------
        binding.tvColesterol.visibility = if (isAfter) View.GONE else View.VISIBLE
        binding.containerColesterol.visibility = if (isAfter) View.GONE else View.VISIBLE

        // -------------------------
        // ADRENALINA + IMC
        // SOLO MID
        // -------------------------
        binding.tilAdrenalina.visibility = if (isMid) View.VISIBLE else View.GONE
        binding.tilImc.visibility = if (isMid) View.VISIBLE else View.GONE

        // -------------------------
        // CAUSA CARDIACA
        // MID + AFTER (BEFORE NO)
        // -------------------------
        binding.tvCausaCardiaca.visibility = if (isBefore) View.GONE else View.VISIBLE
        binding.containerCausa.visibility = if (isBefore) View.GONE else View.VISIBLE

        // -------------------------
        // CARDIOCOMPRESIÓN
        // MID + AFTER (BEFORE NO)
        // -------------------------
        binding.tvCardioManual.visibility = if (isBefore) View.GONE else View.VISIBLE
        binding.containerCardio.visibility = if (isBefore) View.GONE else View.VISIBLE

        // -------------------------
        // RECUPERACIÓN
        // SOLO AFTER (modelo antiguo)
        // -------------------------
        binding.tvRecuperacion.visibility = if (isAfter) View.VISIBLE else View.GONE
        binding.containerRecuperacion.visibility = if (isAfter) View.VISIBLE else View.GONE
    }


    private fun onPredictClicked() {
        val capnometria = binding.etCapnometria.text.toString().toIntOrNull()
        if (capnometria == null) {
            toast("Capnometría obligatoria")
            return
        }

        // Strings de spinners (si algo está oculto, igual tiene valor por defecto)
        val causaStr = binding.spCausaCardiaca.selectedItem?.toString() ?: "No"
        val cardioStr = binding.spCardioManual.selectedItem?.toString() ?: "Mecánica"
        val recStr = binding.spRecuperacion.selectedItem?.toString() ?: "No"
        val sexoStr = binding.spSexoFemenino.selectedItem?.toString() ?: "Hombre"

        // Binarios
        val causaBin = if (causaStr == "Si") 1 else 0
        val cardioBin = if (cardioStr == "Manual") 1 else 0
        val recBin = if (recStr == "Si") 1 else 0
        val femBin = if (sexoStr == "Mujer") 1 else 0

        // Colesterol (Si/No) -> 0/1 (solo BEFORE/MID)
        val colesterolBin = if (binding.spColesterol.selectedItem?.toString() == "Si") 1 else 0

        when (mode) {

            MODE_BEFORE -> {
                val edad = binding.etEdad.text.toString().toIntOrNull()
                if (edad == null) {
                    toast("Edad obligatoria en INICIO")
                    return
                }

                val (esValido, indice) = calcularIndiceYValidez(
                    mode = MODE_BEFORE,
                    inicio = InputsInicio(
                        edad = edad,
                        capnoInicio = capnometria,
                        colesterol = colesterolBin
                    )
                )

                val docId = PrediccionDedup.docId(
                    predictionMode = MODE_BEFORE,
                    edad = edad.toString(),
                    femenino = "", // no aplica
                    capnometria = capnometria.toString(),
                    causaCardiaca = "", // no aplica
                    cardioManual = "",  // no aplica
                    recPulso = "",      // no aplica
                    valido = if (esValido) "Si" else "No",
                    colesterol = colesterolBin.toString()
                )

                abrirPantallaResultado(
                    esValido = esValido,
                    indice = indice,
                    edadStr = edad.toString(),
                    sexoStr = sexoStr, // lo pasamos por si lo quieres mostrar, aunque no lo use el modelo
                    capnometriaStr = capnometria.toString(),
                    causaStr = causaStr,
                    cardioStr = cardioStr,
                    recStr = recStr,
                    docId = docId,
                    colesterolBin = colesterolBin,
                    adrenalinaN = null,
                    imc = null
                )
            }

            MODE_MID -> {
                val adrenalinaN = binding.etAdrenalinaN.text.toString().toIntOrNull()
                val imc = binding.etImc.text.toString().toDoubleOrNull()

                if (adrenalinaN == null || imc == null) {
                    toast("Adrenalina e IMC son obligatorios en MITAD")
                    return
                }

                val (esValido, indice) = calcularIndiceYValidez(
                    mode = MODE_MID,
                    mitad = InputsMitad(
                        capnoMedio = capnometria,
                        colesterol = colesterolBin,
                        adrenalinaN = adrenalinaN,
                        causaCardiaca = causaBin,
                        imc = imc,
                        cardioExtraManual = cardioBin
                    )
                )

                val docId = PrediccionDedup.docId(
                    predictionMode = MODE_MID,
                    edad = "",       // no aplica
                    femenino = "",   // no aplica
                    capnometria = capnometria.toString(),
                    causaCardiaca = causaStr,
                    cardioManual = cardioStr,
                    recPulso = "",   // no aplica
                    valido = if (esValido) "Si" else "No",
                    colesterol = colesterolBin.toString(),
                    adrenalinaN = adrenalinaN.toString(),
                    imc = imc.toString()
                )

                abrirPantallaResultado(
                    esValido = esValido,
                    indice = indice,
                    edadStr = "", // no aplica
                    sexoStr = sexoStr,
                    capnometriaStr = capnometria.toString(),
                    causaStr = causaStr,
                    cardioStr = cardioStr,
                    recStr = recStr,
                    docId = docId,
                    colesterolBin = colesterolBin,
                    adrenalinaN = adrenalinaN,
                    imc = imc
                )
            }

            else -> {
                // AFTER (modelo antiguo)
                val edad = binding.etEdad.text.toString().toIntOrNull()
                if (edad == null) {
                    toast("Edad obligatoria")
                    return
                }

                val (esValido, indice) = calcularIndiceYValidez(
                    mode = MODE_AFTER,
                    after = InputsAfter(
                        edad = edad,
                        femenino = femBin,
                        capnometria = capnometria,
                        causaCardiaca = causaBin,
                        cardioManual = cardioBin,
                        recuperacion = recBin,
                        tiempoMin = 0
                    )
                )

                val docId = PrediccionDedup.docId(
                    predictionMode = MODE_AFTER,
                    edad = edad.toString(),
                    femenino = sexoStr,
                    capnometria = capnometria.toString(),
                    causaCardiaca = causaStr,
                    cardioManual = cardioStr,
                    recPulso = recStr,
                    valido = if (esValido) "Si" else "No"
                    // no pasamos colesterol/adrenalina/imc porque no aplica
                )

                abrirPantallaResultado(
                    esValido = esValido,
                    indice = indice,
                    edadStr = edad.toString(),
                    sexoStr = sexoStr,
                    capnometriaStr = capnometria.toString(),
                    causaStr = causaStr,
                    cardioStr = cardioStr,
                    recStr = recStr,
                    docId = docId,
                    colesterolBin = 0,
                    adrenalinaN = null,
                    imc = null
                )
            }
        }
    }

    private fun abrirPantallaResultado(
        esValido: Boolean,
        indice: Double,
        edadStr: String,
        sexoStr: String,
        capnometriaStr: String,
        causaStr: String,
        cardioStr: String,
        recStr: String,
        docId: String,
        colesterolBin: Int,
        adrenalinaN: Int?,
        imc: Double?
    ) {
        val target = if (esValido) ValidActivity::class.java else NonValidActivity::class.java

        val i = Intent(this, target).apply {
            putExtra("prediction_mode", mode)
            putExtra("indice", indice)

            putExtra("edad", edadStr)
            putExtra("fem", sexoStr)
            putExtra("capnometria", capnometriaStr)
            putExtra("causa_cardiaca", causaStr)
            putExtra("cardio_manual", cardioStr)
            putExtra("rec_pulso", recStr)

            // nuevos
            putExtra("colesterol", colesterolBin.toString())          // "0" o "1"
            putExtra("adrenalina_n", adrenalinaN?.toString() ?: "")   // "" si no aplica
            putExtra("imc", imc?.toString() ?: "")                    // "" si no aplica

            putExtra("username", name)
            putExtra("userUid", userUid)
            putExtra("docId", docId)
        }
        startActivity(i)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}