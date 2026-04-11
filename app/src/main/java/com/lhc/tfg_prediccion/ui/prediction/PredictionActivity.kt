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

        mode = intent.getStringExtra(PredictionModeActivity.EXTRA_MODE) ?: MODE_AFTER
        Log.d("PRED", "mode recibido = $mode")

        val textoModo = when (mode) {
            MODE_MID -> "Predicción a los 20 minutos de RCP"
            else -> "Predicción en transferencia"
        }

        Toast.makeText(this, textoModo, Toast.LENGTH_SHORT).show()

        binding.tilCapnometria.hint = when (mode) {
            MODE_MID -> "Capnometría (mejor valor a los 20 min)"
            else -> "Capnometría (transferencia)"
        }

        val opcionesSiNo = arrayOf("Si", "No")
        val adapterSiNo = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSiNo)

        binding.spCausaCardiaca.adapter = adapterSiNo
        binding.spRecuperacion.adapter = adapterSiNo

        val opcionesCardio = arrayOf("Mecánica", "Manual")
        val adapterCardio = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesCardio)
        binding.spCardioManual.adapter = adapterCardio

        val opcionesSexo = arrayOf("Hombre", "Mujer")
        val adapterSexo = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSexo)
        binding.spSexoFemenino.adapter = adapterSexo

        name = intent.getStringExtra("userName")
        userUid = intent.getStringExtra("userUid")

        configurarVista()

        binding.btnBack.setOnClickListener {
            val i = Intent(this, PredictionModeActivity::class.java)
            i.putExtra("userName", name)
            i.putExtra("userUid", userUid)
            startActivity(i)
            finish()
        }

        binding.btnPredic.setOnClickListener { onPredictClicked() }
    }

    private fun configurarVista() {

        binding.tilEdad.visibility = View.VISIBLE

        binding.tvSexo.visibility = View.VISIBLE
        binding.containerSexo.visibility = View.VISIBLE

        binding.tilCapnometria.visibility = View.VISIBLE

        binding.tvCausaCardiaca.visibility = View.VISIBLE
        binding.containerCausa.visibility = View.VISIBLE

        binding.tvCardioManual.visibility = View.VISIBLE
        binding.containerCardio.visibility = View.VISIBLE

        binding.tvRecuperacion.visibility = View.VISIBLE
        binding.containerRecuperacion.visibility = View.VISIBLE

        // campos eliminados del modelo
        binding.tvColesterol.visibility = View.GONE
        binding.containerColesterol.visibility = View.GONE
        binding.tilAdrenalina.visibility = View.GONE
        binding.tilImc.visibility = View.GONE
    }

    private fun onPredictClicked() {

        val edad = binding.etEdad.text.toString().toIntOrNull()
        if (edad == null) {
            toast("Edad obligatoria")
            return
        }

        val capnometria = binding.etCapnometria.text.toString().toIntOrNull()
        if (capnometria == null) {
            toast("Capnometría obligatoria")
            return
        }

        val causaStr = binding.spCausaCardiaca.selectedItem?.toString() ?: "No"
        val cardioStr = binding.spCardioManual.selectedItem?.toString() ?: "Mecánica"
        val recStr = binding.spRecuperacion.selectedItem?.toString() ?: "No"
        val sexoStr = binding.spSexoFemenino.selectedItem?.toString() ?: "Hombre"

        val causaBin = if (causaStr == "Si") 1 else 0
        val cardioBin = if (cardioStr == "Manual") 1 else 0
        val recBin = if (recStr == "Si") 1 else 0
        val femBin = if (sexoStr == "Mujer") 1 else 0

        val (esValido, indice) = when (mode) {

            MODE_MID -> calcularIndiceYValidez(
                mode = MODE_MID,
                mitad = InputsMitad(
                    edad = edad,
                    femenino = femBin,
                    capnometria = capnometria,
                    causaCardiaca = causaBin,
                    cardioManual = cardioBin,
                    recuperacion = recBin
                )
            )

            else -> calcularIndiceYValidez(
                mode = MODE_AFTER,
                after = InputsAfter(
                    edad = edad,
                    femenino = femBin,
                    capnometria = capnometria,
                    causaCardiaca = causaBin,
                    cardioManual = cardioBin,
                    recuperacion = recBin
                )
            )
        }

        val docId = PrediccionDedup.docId(
            predictionMode = mode,
            edad = edad.toString(),
            femenino = sexoStr,
            capnometria = capnometria.toString(),
            causaCardiaca = causaStr,
            cardioManual = cardioStr,
            recPulso = recStr,
            valido = if (esValido) "Si" else "No"
        )

        abrirPantallaResultado(
            esValido,
            indice,
            edad.toString(),
            sexoStr,
            capnometria.toString(),
            causaStr,
            cardioStr,
            recStr,
            docId
        )
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
        docId: String
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

            putExtra("username", name)
            putExtra("userUid", userUid)
            putExtra("docId", docId)
        }

        startActivity(i)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}