package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceUnitValueSurfaceViewTest {

    @Test
    fun `physical renderer starts inactive and mechanically binds every immutable presentation field`() {
        val source = source("UserObservedPriceUnitValueSurfaceView.kt").readText()

        listOf(
            ") : LinearLayout(context, attrs, defStyleAttr), UserObservedPriceUnitValueSurfaceRenderer",
            "visibility = View.GONE",
            "isSaveEnabled = false",
            "headlineView.text = state.headline",
            "evidenceLabelView.text = state.evidenceLabel",
            "statusTitleView.text = state.statusTitle",
            "guidanceView.text = state.guidance",
            "val unitRateText = state.unitRateText",
            "unitRateView.text = unitRateText.orEmpty()",
            "unitRateView.visibility = if (unitRateText == null) View.GONE else View.VISIBLE",
            "noticeView.text = state.notice",
            "visibility = View.VISIBLE"
        ).forEach { required ->
            assertTrue("Expected mechanical surface binding $required", source.contains(required))
        }
    }

    @Test
    fun `physical renderer owns no observed price semantics or eligibility decision`() {
        val source = source("UserObservedPriceUnitValueSurfaceView.kt").readText()

        listOf(
            "state.status",
            "state.valueComparisonEligible",
            "when (state",
            "UserObservedPriceUnitValueUiProjector",
            "UserProofBackedObservedPriceUnitValueEligibilityEvaluator",
            "UserProofBackedObservedPriceUsePolicy(",
            "EvidenceFreshnessEvaluator",
            "ProductPackageQuantityFactResolver",
            "EvidenceFactResolver",
            "EvidenceBackedUnitValuePolicy",
            "DeterministicValueMath",
            "ProductionCurrentPriceEligibilityEvaluator",
            "ProductionDatasetLifecycleRegistry",
            "ProviderProductionAuthorization",
            "OpenFoodFactsPackageQuantityEvidenceAdapter",
            "AvailabilityEvidence",
            "PromotionEvidence"
        ).forEach { forbidden ->
            assertFalse("Surface view must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `physical renderer has no action navigation storage network or clock authority`() {
        val source = source("UserObservedPriceUnitValueSurfaceView.kt").readText()

        listOf(
            "android.widget.Button",
            "setOnClickListener",
            "android.content.Intent",
            "startActivity(",
            "android.app.Activity",
            "SharedPreferences",
            "UserProvidedPriceProofArtifactLocalStore(",
            "WorkManager",
            "System.currentTimeMillis",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Surface view must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}
