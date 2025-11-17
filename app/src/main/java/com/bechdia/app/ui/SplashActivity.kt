package com.bechdia.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bechdia.app.R

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    // Splash screen duration in milliseconds
    private val SPLASH_DURATION = 2000L // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide action bar
        supportActionBar?.hide()

        // Get views
        val heartIcon = findViewById<ImageView>(R.id.splashHeart)
        val splashText = findViewById<TextView>(R.id.splashText)
        val splashTagline = findViewById<TextView>(R.id.splashTagline)

        // Load animations
        val heartAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_heart_animation)
        val textAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_text_animation)

        // Start animations
        heartIcon.startAnimation(heartAnimation)
        splashText.startAnimation(textAnimation)

        // Animate tagline with delay
        Handler(Looper.getMainLooper()).postDelayed({
            splashTagline.animate()
                .alpha(1f)
                .setDuration(400)
                .start()
        }, 600)

        // Navigate to MainActivity after splash duration
        Handler(Looper.getMainLooper()).postDelayed({
            startMainActivity()
        }, SPLASH_DURATION)
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)

        // Smooth transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        // Finish splash activity so user can't go back to it
        finish()
    }

    // Disable back button on splash screen
    override fun onBackPressed() {
        // Do nothing - prevent going back during splash
    }
}