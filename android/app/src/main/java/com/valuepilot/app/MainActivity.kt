package com.valuepilot.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.serviceStatus)
        findViewById<Button>(R.id.enableButton).setOnClickListener { showAccessibilityDisclosure() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener { openAccessibilitySettings() }
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        status.text = if (enabled) "ValuePilot accessibility scanner: ON" else "ValuePilot accessibility scanner: OFF"
        status.setTextColor(if (enabled) 0xff0f7b4d.toInt() else 0xffb42318.toInt())
    }

    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Before enabling ValuePilot")
            .setMessage(
                "ValuePilot uses Android Accessibility access to read product/menu text in the app currently on screen and to display its floating comparison panel. " +
                    "It automatically processes newly visible products and scrolls a list only after you choose the advanced off-screen collection control. If you choose OCR, it captures the visible app window for on-device text recognition. " +
                    "Its compact food classifier and OCR both run locally. Exact measurements always override AI estimates. " +
                    "This build has no INTERNET permission and does not upload screen contents, account credentials, or shopping data."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ -> openAccessibilitySettings() }
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isServiceEnabled(): Boolean {
        val expected = "$packageName/${ValueAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        return splitter.any { it.equals(expected, true) }
    }
}
