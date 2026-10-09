package com.valuepilot.app.next

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.valuepilot.app.ArgentinaPrivateBasketStoreProjection
import com.valuepilot.app.ComparisonActivity

/** Debug-only owner for the production-backed Compose shopping journey. */
class ValuePilotNextActivity : ComponentActivity() {
    private lateinit var journey: ValuePilotNextJourneyViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        journey = ViewModelProvider(this)[ValuePilotNextJourneyViewModel::class.java]
        journey.start(applicationContext)

        setContent {
            ValuePilotNextTheme {
                ValuePilotNextApp(
                    journey = journey,
                    reducedMotion = systemAnimationsDisabled(),
                    onExitPreview = ::finish,
                    onOpenComparison = {
                        startActivity(Intent(this, ComparisonActivity::class.java))
                    },
                    onOpenStoreMap = ::openStoreMap
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::journey.isInitialized) journey.refreshPrivateIdentitiesFromStore()
    }

    private fun openStoreMap(store: ArgentinaPrivateBasketStoreProjection): Boolean {
        val uri = (
            "geo:${store.latitude},${store.longitude}?q=${store.latitude},${store.longitude}(${Uri.encode(store.storeName)})"
        ).toUri()
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            false
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
