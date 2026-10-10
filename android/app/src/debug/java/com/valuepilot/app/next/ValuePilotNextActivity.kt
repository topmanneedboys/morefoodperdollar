package com.valuepilot.app.next

import android.content.ActivityNotFoundException
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
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
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation

/** Debug-only owner for the production-backed Compose shopping journey. */
class ValuePilotNextActivity : AppCompatActivity() {
    private lateinit var journey: ValuePilotNextJourneyViewModel
    private var observedPriceProofPicker: AndroidUserObservedPriceProofContentPicker? = null
    private var googleImagesLaunchInFlight = false
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
    private val googleImagesCustomTabLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            googleImagesLaunchInFlight = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        journey = ViewModelProvider(this)[ValuePilotNextJourneyViewModel::class.java]
        journey.start(applicationContext)
        val startDestination = when (intent.getStringExtra(EXTRA_START_DESTINATION)) {
            "SAVED" -> NextDestination.SAVED
            "OBSERVED_PRICE_SELECTION" -> NextDestination.OBSERVED_PRICE_SELECTION
            "STAPLE_WATCH_SETUP" -> NextDestination.STAPLE_WATCH_SETUP
            "PRIVATE_PRICE_HISTORY" -> NextDestination.PRIVATE_PRICE_HISTORY
            else -> NextDestination.HOME
        }
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
            var systemReducedMotion by remember { mutableStateOf(systemAnimationsDisabled()) }
            DisposableEffect(contentResolver) {
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        systemReducedMotion = systemAnimationsDisabled()
                    }

                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        systemReducedMotion = systemAnimationsDisabled()
                    }
                }
                contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                    false,
                    observer
                )
                contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
                    false,
                    observer
                )
                systemReducedMotion = systemAnimationsDisabled()
                onDispose { contentResolver.unregisterContentObserver(observer) }
            }
            val darkTheme = ValuePilotNextUiPreferencesCodec.resolvesDarkTheme(
                appearance = presentationPreferences.appearance,
                systemDarkTheme = isSystemInDarkTheme()
            )
            DisposableEffect(darkTheme) {
                val systemBars = WindowCompat.getInsetsController(window, window.decorView)
                systemBars.isAppearanceLightStatusBars = !darkTheme
                systemBars.isAppearanceLightNavigationBars = !darkTheme
                onDispose { }
            }
            ValuePilotNextTheme(
                darkTheme = darkTheme,
                density = presentationPreferences.density
            ) {
                ValuePilotNextApp(
                    journey = journey,
                    startDestination = startDestination,
                    reducedMotion = ValuePilotNextUiPreferencesCodec.resolvesReducedMotion(
                        preference = presentationPreferences.motion,
                        systemReducedMotion = systemReducedMotion
                    ),
                    presentationPreferences = presentationPreferences,
                    language = ValuePilotNextUiPreferencesCodec.resolvesLanguage(
                        presentationPreferences.language
                    ),
                    onPresentationPreferencesChanged = { updated ->
                        uiPreferencesStore.save(updated)
                        presentationPreferences = updated
                    },
                    onExitPreview = ::finish,
                    onOpenLegacyComparisonTools = {
                        startActivity(Intent(this, ComparisonActivity::class.java))
                    },
                    onOpenGoogleImages = ::openGoogleImages,
                    onShareCompareHereResult = ::shareCompareHereResult,
                    onOpenStoreMap = ::openStoreMap,
                    onStartGoodPriceBarcodeScan = ::startGoodPriceBarcodeScan,
                    onOpenLegacyStapleWatch = ::openSavedTools,
                    onSharePrivatePriceHistory = ::sharePrivatePriceHistory,
                    onShareGoodPriceResult = ::shareGoodPriceResult
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        googleImagesLaunchInFlight = false
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

    private fun openGoogleImages(
        match: PracticalShoppingSearchIdentityPresentation.Match,
        areaLabel: String
    ) {
        if (googleImagesLaunchInFlight) return
        val uri = GoogleImagesHandoff.urlFor(match, areaLabel)?.toUri() ?: run {
            Toast.makeText(this, "No se pudo preparar la búsqueda de imágenes.", Toast.LENGTH_SHORT).show()
            return
        }
        googleImagesLaunchInFlight = true
        val partialTab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setInitialActivityHeightPx(
                googleImagesInitialHeightPx(),
                CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE
            )
            .build()
        partialTab.intent.data = uri
        CustomTabsClient.getPackageName(this, null)?.let(partialTab.intent::setPackage)
        try {
            // Chrome only applies the partial-tab height when the Custom Tab is
            // launched for a result (or through an explicit Custom Tabs session).
            // The result callback leaves this Activity's product and Search state
            // alive when the browser closes.
            googleImagesCustomTabLauncher.launch(partialTab.intent)
        } catch (_: ActivityNotFoundException) {
            googleImagesLaunchInFlight = false
            showGoogleImagesFallback(uri)
        } catch (_: SecurityException) {
            googleImagesLaunchInFlight = false
            showGoogleImagesFallback(uri)
        }
    }

    private fun showGoogleImagesFallback(uri: Uri) {
        googleImagesLaunchInFlight = false
        AlertDialog.Builder(this)
            .setTitle("Google Images")
            .setMessage("El navegador no pudo abrir la pestaña parcial. ¿Querés abrir la página en pantalla completa?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Abrir navegador") { _, _ -> launchGoogleImagesFullScreen(uri) }
            .show()
    }

    private fun launchGoogleImagesFullScreen(uri: Uri) {
        googleImagesLaunchInFlight = true
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            googleImagesLaunchInFlight = false
            Toast.makeText(this, "No hay un navegador disponible.", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            googleImagesLaunchInFlight = false
            Toast.makeText(this, "No se pudo abrir Google Images.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun googleImagesInitialHeightPx(): Int = maxOf(
        resources.displayMetrics.heightPixels * GOOGLE_IMAGES_INITIAL_HEIGHT_PERCENT / 100,
        (GOOGLE_IMAGES_MIN_INITIAL_HEIGHT_DP * resources.displayMetrics.density).toInt()
    )

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

    companion object {
        internal const val EXTRA_START_DESTINATION = "valuepilot.next.start_destination"
        const val GOOGLE_IMAGES_INITIAL_HEIGHT_PERCENT = 65
        const val GOOGLE_IMAGES_MIN_INITIAL_HEIGHT_DP = 320
    }
}
