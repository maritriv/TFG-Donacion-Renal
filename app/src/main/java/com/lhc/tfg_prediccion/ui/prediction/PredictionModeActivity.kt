package com.lhc.tfg_prediccion.ui.prediction

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lhc.tfg_prediccion.databinding.ActivityPredictionModeBinding

class PredictionModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPredictionModeBinding
    private var name: String? = null
    private var userUid: String? = null

    companion object {
        const val EXTRA_MODE = "prediction_mode"
        const val MODE_BEFORE = "BEFORE_RCP"
        const val MODE_MID    = "MID_RCP"
        const val MODE_AFTER  = "AFTER_RCP"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPredictionModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Recuperamos los extras que ya pasabas entre pantallas
        name = intent.getStringExtra("userName")
        userUid = intent.getStringExtra("userUid")

        fun goToPrediction(mode: String) {
            val i = Intent(this, PredictionActivity::class.java)
            i.putExtra("userName", name)
            i.putExtra("userUid", userUid)
            i.putExtra(EXTRA_MODE, mode)
            startActivity(i)
        }

        binding.btnBefore.setOnClickListener { goToPrediction(MODE_BEFORE) }
        binding.btnMid.setOnClickListener    { goToPrediction(MODE_MID) }
        binding.btnAfter.setOnClickListener  { goToPrediction(MODE_AFTER) }
    }
}


