package com.valuepilot.app.next

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.valuepilot.app.AndroidUserObservedPriceProofContentPicker
import com.valuepilot.app.AndroidUserObservedPriceProofContentSource
import com.valuepilot.app.ArgentinaPrivateBasketStoreProjection
import com.valuepilot.app.BarcodeCaptureActivity
import com.valuepilot.app.ComparisonActivity
import com.valuepilot.app.DebugNextSavedToolsHandoff
import com.valuepilot.app.GoodPriceShareCard
import com.valuepilot.app.MainActivity

/** Debug-only owner for the production-backed Compose shopping journey. */
class ValuePilotNextActivity : AppCompatActivity() {
    private lateinit var journey: ValuePilotNextJourneyViewModel
    private var observedPriceProofPicker: AndroidUserObservedPriceProofContentPicker? = null
    private val goodPriceBarcodeCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (!::journey.isInitialized) return@registerForActivityResult
            val gtin = result.data?.getStringExtra(BarcodeCaptureActivity.EXTRA_GTIN)
            when {
                result.resultCode != RESULT_OK -> journey.onGoodPriceBarcodeScanCancelled()
                gtin.isNullOrBlank() -> journey.onGoodPriceBarcodeScanUnavailable()
                else -> journey.lookupGoodPriceBarcode(gtin)
            }
        }

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
        val uiPreferencesStore = ValuePilotNextUiPreferencesStore(applicationContext)

        setContent {
            var presentationPreferences by remember {
                mutableStateOf(uiPreferencesStore.load())
            }
            val systemReducedMotion = remember { systemAnimationsDisabled() }
            ValuePilotNextTheme(
                darkTheme = ValuePilotNextUiPreferencesCodec.resolvesDarkTheme(
                    appearance = presentationPreferences.appearance,
                    systemDarkTheme = isSystemInDarkTheme()
                )
            ) {
                ValuePilotNextApp(
                    journey = journey,
                    reducedMotion = ValuePilotNextUiPreferencesCodec.resolvesReducedMotion(
                        preference = presentationPreferences.motion,
                        systemReducedMotion = systemReducedMotion
                    ),
                    presentationPreferences = presentationPreferences,
                    onPresentationPreferencesChanged = { updated ->
                        uiPreferencesStore.save(updated)
                        presentationPreferences = updated
                    },
                    onExitPreview = ::finish,
                    onOpenLegacyComparisonTools = {
                        startActivity(Intent(this, ComparisonActivity::class.java))
                    },
                    onShareCompareHereResult = ::shareCompareHereResult,
                    onOpenStoreMap = ::openStoreMap,
                    onStartGoodPriceBarcodeScan = ::startGoodPriceBarcodeScan,
                    onOpenLegacyStapleWatch = ::openSavedTools,
                    onOpenSavedTools = ::openSavedTools,
                    onSharePrivatePriceHistory = ::sharePrivatePriceHistory,
                    onShareGoodPriceResult = ::shareGoodPriceResult
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::journey.isInitialized) {
            journey.refreshPrivateIdentitiesFromStore()
            journey.refreshPrivatePriceHistory()
            journey.refreshGoodPricePrivateMemory()
        }
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

    private fun startGoodPriceBarcodeScan() {
        journey.onGoodPriceBarcodeScanRequested()
        try {
            goodPriceBarcodeCaptureLauncher.launch(
                Intent(this, BarcodeCaptureActivity::class.java)
            )
        } catch (_: Exception) {
            journey.onGoodPriceBarcodeScanUnavailable()
        }
    }

    private fun openSavedTools() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(DebugNextSavedToolsHandoff.ACTION_OPEN_SAVED_TOOLS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun sharePrivatePriceHistory(text: String): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                getString(com.valuepilot.app.R.string.home_private_memory_export_subject)
            )
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            startActivity(
                Intent.createChooser(
                    sendIntent,
                    getString(com.valuepilot.app.R.string.home_private_memory_export_send)
                )
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun shareGoodPriceResult(card: GoodPriceShareCard): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, card.title)
            putExtra(Intent.EXTRA_TEXT, card.text)
        }
        return try {
            startActivity(
                Intent.createChooser(
                    sendIntent,
                    "Share price calculation"
                )
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun shareCompareHereResult(card: com.valuepilot.app.CompareHereShareCard): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, card.title)
            putExtra(Intent.EXTRA_TEXT, card.text)
        }
        return try {
            startActivity(
                Intent.createChooser(
                    sendIntent,
                    "Share comparison result"
                )
            )
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
