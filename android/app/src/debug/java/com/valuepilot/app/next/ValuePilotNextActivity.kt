package com.valuepilot.app.next

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.valuepilot.app.AndroidUserObservedPriceProofContentPicker
import com.valuepilot.app.AndroidUserObservedPriceProofContentSource
import com.valuepilot.app.ArgentinaPrivateBasketStoreProjection
import com.valuepilot.app.ComparisonActivity
import com.valuepilot.app.DebugNextSavedToolsHandoff
import com.valuepilot.app.GoodPriceActivity
import com.valuepilot.app.MainActivity

/** Debug-only owner for the production-backed Compose shopping journey. */
class ValuePilotNextActivity : AppCompatActivity() {
    private lateinit var journey: ValuePilotNextJourneyViewModel
    private var observedPriceProofPicker: AndroidUserObservedPriceProofContentPicker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        journey = ViewModelProvider(this)[ValuePilotNextJourneyViewModel::class.java]
        journey.start(applicationContext)
        observedPriceProofPicker =
            AndroidUserObservedPriceProofContentPicker(
                activity = this,
                contentSource = AndroidUserObservedPriceProofContentSource(contentResolver),
                onReadResult = journey::onObservedPriceProofContentReadResult
            ).also { picker -> journey.attachObservedPriceProofPicker(picker::launch) }

        setContent {
            ValuePilotNextTheme {
                ValuePilotNextApp(
                    journey = journey,
                    reducedMotion = systemAnimationsDisabled(),
                    onExitPreview = ::finish,
                    onOpenComparison = {
                        startActivity(Intent(this, ComparisonActivity::class.java))
                    },
                    onOpenStoreMap = ::openStoreMap,
                    onCheckSavedPrice = ::openGoodPriceForSavedProduct,
                    onOpenSavedTools = ::openSavedTools
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::journey.isInitialized) journey.refreshPrivateIdentitiesFromStore()
    }

    override fun onDestroy() {
        if (::journey.isInitialized) journey.attachObservedPriceProofPicker(null)
        observedPriceProofPicker?.close()
        observedPriceProofPicker = null
        super.onDestroy()
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

    private fun openGoodPriceForSavedProduct(displayName: String) {
        startActivity(
            Intent(this, GoodPriceActivity::class.java).putExtra(
                GoodPriceActivity.EXTRA_PRODUCT_NAME,
                displayName
            )
        )
    }

    private fun openSavedTools() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(DebugNextSavedToolsHandoff.ACTION_OPEN_SAVED_TOOLS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
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
