package com.lhc.tfg_prediccion.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.lhc.tfg_prediccion.R
import com.lhc.tfg_prediccion.ui.login.LoginActivity

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // layout icono + texto RENAL-uDCD
        setContentView(R.layout.activity_splash)

        // Navega a Login tras una pequeña espera
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // evita volver al splash con el botón atrás
        }, 600)
    }
}