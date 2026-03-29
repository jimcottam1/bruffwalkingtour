package com.example.bruffwalkingtour

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class TourCompletionActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display and hide system navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupSystemUI()
        
        setContentView(R.layout.activity_tour_completion)
        
        setupViews()
    }
    
    private fun setupSystemUI() {
        // Hide system navigation bar for full screen experience
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun setupViews() {
        supportActionBar?.hide()
        
        val actionsTextView = findViewById<TextView>(R.id.tour_completion_actions)
        actionsTextView.movementMethod = LinkMovementMethod.getInstance()
        
        val fullText = "🔄 Take Tour Again  •  🚪 Exit App"
        val spannableString = SpannableString(fullText)
        
        // Make "Take Tour Again" clickable
        val restartIndex = fullText.indexOf("Take Tour Again")
        if (restartIndex >= 0) {
            val restartSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(this@TourCompletionActivity, IntroActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            }
            spannableString.setSpan(restartSpan, restartIndex, restartIndex + "Take Tour Again".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // Make "Exit App" clickable
        val exitIndex = fullText.indexOf("Exit App")
        if (exitIndex >= 0) {
            val exitSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    finishAffinity()
                }
            }
            spannableString.setSpan(exitSpan, exitIndex, exitIndex + "Exit App".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        actionsTextView.text = spannableString
    }
}