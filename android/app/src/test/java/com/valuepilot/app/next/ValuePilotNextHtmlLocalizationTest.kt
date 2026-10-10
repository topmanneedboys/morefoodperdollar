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
