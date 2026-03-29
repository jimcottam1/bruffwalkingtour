package com.example.bruffwalkingtour

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.method.ScrollingMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class LogViewerActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display and hide system navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupSystemUI()
        
        setContentView(R.layout.activity_log_viewer)
        
        setupViews()
        loadLogs()
    }
    
    private fun setupSystemUI() {
        // Hide system navigation bar for full screen experience
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Handle window insets to avoid overlap with action bar
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val actionBarHeight = (56 * resources.displayMetrics.density).toInt()
            val scrollView = findViewById<android.widget.ScrollView>(R.id.log_scroll_view)
            scrollView?.setPadding(0, systemBars.top + actionBarHeight, 0, 0)
            insets
        }
    }
    
    private fun setupViews() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Application Logs"
        
        // Set up clickable links
        setupClickableLinks()
    }
    
    private fun setupClickableLinks() {
        // Copy logs link
        val copyLogsTextView = findViewById<TextView>(R.id.copy_logs_text)
        copyLogsTextView.movementMethod = LinkMovementMethod.getInstance()
        
        val copyText = "📋 Copy All Logs"
        val copySpannable = SpannableString(copyText)
        val copySpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                copyLogsToClipboard()
            }
        }
        copySpannable.setSpan(copySpan, 0, copyText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        copyLogsTextView.text = copySpannable
        
        // Clear logs link
        val clearLogsTextView = findViewById<TextView>(R.id.clear_logs_text)
        clearLogsTextView.movementMethod = LinkMovementMethod.getInstance()
        
        val clearText = "🗑️ Clear Logs"
        val clearSpannable = SpannableString(clearText)
        val clearSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                clearLogs()
            }
        }
        clearSpannable.setSpan(clearSpan, 0, clearText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        clearLogsTextView.text = clearSpannable
        
        // Refresh logs link
        val refreshLogsTextView = findViewById<TextView>(R.id.refresh_logs_text)
        refreshLogsTextView.movementMethod = LinkMovementMethod.getInstance()
        
        val refreshText = "🔄 Refresh Logs"
        val refreshSpannable = SpannableString(refreshText)
        val refreshSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                loadLogs()
            }
        }
        refreshSpannable.setSpan(refreshSpan, 0, refreshText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        refreshLogsTextView.text = refreshSpannable
    }
    
    private fun loadLogs() {
        val logTextView = findViewById<TextView>(R.id.log_content)
        logTextView.movementMethod = ScrollingMovementMethod()
        
        // Get logs from LogUtils
        val logs = LogUtils.getAllLogs()
        
        if (logs.isNotEmpty()) {
            logTextView.text = logs.joinToString("\n")
        } else {
            logTextView.text = "No logs available.\n\nLogs will appear here as you use the app.\nTry navigating around the tour to generate some log entries."
        }
        
        // Auto-scroll to bottom to show latest logs
        logTextView.post {
            val scrollView = findViewById<android.widget.ScrollView>(R.id.log_scroll_view)
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    private fun copyLogsToClipboard() {
        val logs = LogUtils.getAllLogs()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Bruff Tour Logs", logs.joinToString("\n"))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun clearLogs() {
        LogUtils.clearLogs()
        loadLogs() // Refresh the display
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}