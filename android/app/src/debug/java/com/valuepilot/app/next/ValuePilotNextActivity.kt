package com.valuepilot.app.next

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.valuepilot.app.ComparisonActivity

/** Parallel Compose host used by the debug-only migration preview entry point. */
class ValuePilotNextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val reducedMotion = systemAnimationsDisabled()

        setContent {
            ValuePilotNextTheme {
                ValuePilotNextApp(
                    reducedMotion = reducedMotion,
                    onExitPreview = ::finish,
                    onOpenComparison = {
                        startActivity(Intent(this, ComparisonActivity::class.java))
                    }
                )
            }
        }
    }

    private fun systemAnimationsDisabled(): Boolean =
        runCatching {
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f || Settings.Global.getFloat(
                contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
}
