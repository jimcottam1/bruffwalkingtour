package com.example.bruffwalkingtour

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.help_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
