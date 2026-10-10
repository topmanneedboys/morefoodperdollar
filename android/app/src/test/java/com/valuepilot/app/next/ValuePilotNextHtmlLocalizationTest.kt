package com.valuepilot.app.next

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextHtmlLocalizationTest {

    @Test
    fun `native bridge labels do not expose raw status enums or errors`() {
        val source = assetSource()

        assertTrue(source.contains("nativeMatchExactBarcode"))
        assertTrue(source.contains("nativeFreshnessLabel(offer.freshness)"))
        assertTrue(source.contains("nativeTripErrorDetail"))
        assertFalse(source.contains("safe(offer.freshness||'')"))
        assertFalse(source.contains("safe(payload.error||'')"))
    }

    @Test
    fun `trip form values survive html rerenders`() {
        val source = assetSource()

        assertTrue(source.contains("tripInputs:{distance:'',consumption:'',fuel:'',tolls:'0',parking:'0'}"))
        assertTrue(source.contains("state.tripInputs[e.target.name]=e.target.value"))
        assertTrue(source.contains("safe(state.tripInputs.distance)"))
        assertTrue(source.contains("for(const name of Object.keys(state.tripInputs))"))
    }

    @Test
    fun `html sample currency remains explicit ARS in both languages`() {
        assertTrue(assetSource().contains("const currency=(n)=>'ARS '+money(n)"))
    }

    @Test
    fun `timed out native search loading state is shown as unavailable`() {
        val source = assetSource()

        assertTrue(source.contains("case'LOADING':return copy('nativeTimeout')"))
        assertTrue(source.contains("payload.mode==='TIMEOUT'||payload.mode==='LOADING'"))
    }

    @Test
    fun `native saved state can refresh after returning from a Kotlin tool`() {
        val source = assetSource()

        assertTrue(
            source.contains(
                "refreshNativeSaved:()=>{if(!state.nativeSavedEnabled||state.nativeSavedPending)return false;requestNativeSaved();return true}"
            )
        )
    }

    @Test
    fun `stale native actions have localized notices`() {
        val source = assetSource()

        assertTrue(source.contains("'PRODUCT_DETAIL_STALE':'nativeStaleDetail'"))
        assertTrue(source.contains("'SAVED_ITEM_STALE':'nativeStaleSaved'"))
        assertTrue(source.contains("receiveNativeActionNotice:(payload)=>receiveNativeActionNotice(payload)"))
    }

    @Test
    fun `demo ribbon accessibility copy follows the selected language`() {
        val source = assetSource()

        assertTrue(source.contains("demoAria:{es:"))
        assertTrue(source.contains("document.querySelector('#demoRibbon')"))
        assertFalse(source.contains("const ariaLabel='Son datos de demostración"))
    }

    @Test
    fun `saved bridge renders localized private history and purchase boundary`() {
        val source = assetSource()

        assertTrue(source.contains("nativePrivateHistorySummary"))
        assertTrue(source.contains("nativePurchaseHistoryUnavailable"))
        assertTrue(source.contains("nativeHistoryAvailable"))
        assertTrue(source.contains("purchaseHistoryUnavailableTitle"))
        assertTrue(source.contains("\${native?nativePrivateHistorySummary(state.nativeSavedState):''}"))
        assertTrue(source.contains("\${native?nativePurchaseHistoryUnavailable():''}"))
    }

    @Test
    fun `native price cards preserve decimal evidence in each language`() {
        val source = assetSource()

        assertTrue(source.contains("const nativeAmount="))
        assertTrue(source.contains("const hasUnitPrice=offer?.unitPriceAmount!=null"))
        assertTrue(source.contains("const amount=hasUnitPrice?offer.unitPriceAmount:offer?.priceAmount"))
        assertTrue(source.contains("const decimal=state.language===LANGUAGES.EN?'.':','"))
        assertTrue(source.contains("const grouping=state.language===LANGUAGES.EN?',':'.'"))
        assertTrue(source.contains("const formattedAmount=amount==null?null:nativeAmount(amount)"))
        assertTrue(source.contains("nativeUnitPrice"))
        assertTrue(source.contains("nativePackagePrice"))
        assertTrue(source.contains("offer?.unitPriceBasis"))
    }

    @Test
    fun `unknown native enums stay behind localized evidence labels`() {
        val source = assetSource()

        assertTrue(source.contains("copyKey?copy(copyKey):copy('nativeIdentityOnly')"))
        assertTrue(source.contains("default:return copy('nativeFreshUnknown')"))
        assertFalse(source.contains("default:return String(value||copy('nativeFreshUnknown'))"))
    }

    private fun assetSource(): String {
        var directory = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate =
                File(directory, "app/src/debug/assets/valuepilot_next_html/app.js")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate valuepilot_next_html/app.js")
    }
}
