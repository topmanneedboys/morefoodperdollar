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
    fun `language changes restore scroll on every screen`() {
        val source = assetSource()

        assertTrue(source.contains("const scroll=window.scrollY||document.documentElement.scrollTop"))
        assertTrue(source.contains("requestAnimationFrame(()=>window.scrollTo({top:scroll,behavior:'auto'}))"))
        assertFalse(source.contains("if(page==='search')requestAnimationFrame"))
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
        assertTrue(source.contains("function nativeHistoryRows(history)"))
        assertTrue(source.contains("historyLatestPrice"))
        assertTrue(source.contains("nativeHistorySource(value)"))
        assertTrue(source.contains("purchaseHistoryUnavailableTitle"))
        assertTrue(source.contains("\${native?nativePrivateHistorySummary(state.nativeSavedState):''}"))
        assertTrue(source.contains("\${native?nativePurchaseHistoryUnavailable():''}"))
    }

    @Test
    fun `native price cards preserve decimal evidence in each language`() {
        val source = assetSource()

        assertTrue(source.contains("const nativeAmount="))
        assertTrue(source.contains("unitValue:offer?.unitPriceAmount!=null?Number(offer.unitPriceAmount):null"))
        assertTrue(source.contains("packValue:offer?.priceAmount!=null?Number(offer.priceAmount):null"))
        assertTrue(source.contains("const decimal=state.language===LANGUAGES.EN?'.':','"))
        assertTrue(source.contains("const grouping=state.language===LANGUAGES.EN?',':'.'"))
        assertTrue(source.contains("const nativeCurrency="))
        assertTrue(source.contains("unitCurrency:offer?.unitPriceCurrency||offer?.currency||'ARS'"))
        assertTrue(source.contains("nativeUnitPrice"))
        assertTrue(source.contains("nativePackagePrice"))
        assertTrue(source.contains("'provider identity match':'nativeMatchProvider'"))
        assertTrue(source.contains("offer?.unitPriceBasis"))
    }

    @Test
    fun `html journey defaults to Kotlin-owned surfaces with fixture fallback`() {
        val source = assetSource()

        assertTrue(source.contains("nativeSearchEnabled:true"))
        assertTrue(source.contains("nativeSavedEnabled:true"))
        assertTrue(source.contains("nativeTripEnabled:true"))
        assertTrue(source.contains("function nativeDataMode(){return state.nativeSearchEnabled&&state.nativeSavedEnabled}"))
        assertTrue(source.contains("function basket(){return nativeDataMode()?nativeBasket():demoBasket()}"))
        assertTrue(source.contains("function trip(){const native=nativeDataMode()&&state.nativeTripEnabled"))
        assertTrue(source.contains("if(nativeDataMode()&&state.nativeTripEnabled){requestNativeTrip"))
        assertTrue(source.contains("function receiveNativeProductDetail"))
        assertTrue(source.contains("function nativeDetail()"))
        assertTrue(source.contains("function nativeBasket()"))
        assertTrue(source.contains("case'native-detail-save':requestNativeDetailSave()"))
        assertTrue(source.contains("receiveNativeProductDetail:(payload)=>{receiveNativeProductDetail(payload);return true}"))
    }

    @Test
    fun `saved mode can re-enter Kotlin surfaces after a search fixture fallback`() {
        val source = assetSource()

        assertTrue(
            source.contains(
                "function enableNativeSaved(){state.nativeSavedEnabled=true;state.nativeSearchEnabled=true;state.nativeSavedState=null;requestNativeSaved()}"
            )
        )
    }

    @Test
    fun `native mode keeps the home shell evidence backed and preserves route context`() {
        val source = assetSource()

        assertTrue(source.contains("const native=Boolean(state.nativeSavedState&&nativeDataMode())"))
        assertTrue(source.contains("native?copy('nativeBasketTitle'):copy('demoBasket')"))
        assertTrue(source.contains("native?copy('nativeEvidence'):(basketCount()?copy('samplePrices'):copy('basketEmptyCopy'))"))
        assertTrue(source.contains("const parentNav=['home','search','basket','saved'].includes(state.from)?state.from:'search'"))
        assertTrue(source.contains("navigate(el.dataset.nav,{from:state.page})"))
        assertTrue(source.contains("navigate('search',{from:'home'})"))
    }

    @Test
    fun `maximum density matrix keeps identity pinned and supports evidence actions`() {
        val source = assetSource()

        assertTrue(source.contains("const MATRIX_DEMO_ROWS=Object.freeze(Array.from({length:1000"))
        assertTrue(source.contains("function matrixRowsForNative(payload)"))
        assertTrue(source.contains("class=\"matrix-scroll\""))
        assertTrue(source.contains("data-action=\"matrix-sort\""))
        assertTrue(source.contains("id=\"matrixStore\""))
        assertTrue(source.contains("id=\"matrixSort\""))
        assertTrue(source.contains("case'native-map':requestNativeMap"))
        assertTrue(source.contains("case'native-save':requestNativeSave"))
        assertTrue(source.contains("state.matrixScrollTop"))
        assertTrue(source.contains("matrixRowsSorted"))
    }

    @Test
    fun `matrix styles define sticky identity and unit columns with compact rows`() {
        val source = styleSource()

        assertTrue(source.contains(".price-matrix .matrix-product{width:164px"))
        assertTrue(source.contains("position:sticky;left:0"))
        assertTrue(source.contains(".price-matrix .matrix-unit{width:96px"))
        assertTrue(source.contains("height:48px"))
        assertTrue(source.contains("overflow:auto"))
    }

    @Test
    fun `observed price route is native backed and bilingual`() {
        val source = assetSource()

        assertTrue(source.contains("observedPriceTitle:{es:"))
        assertTrue(source.contains("observedConfirmTitle:{es:"))
        assertTrue(source.contains("function nativeObservedSelection(payload)"))
        assertTrue(source.contains("function nativeObservedConfirmation(payload)"))
        assertTrue(source.contains("'observed-price':observedPrice"))
        assertTrue(source.contains("'observed-confirm':observedConfirm"))
        assertTrue(source.contains("READ_NATIVE_OBSERVED_PRICE"))
        assertTrue(source.contains("SET_NATIVE_OBSERVED_PROOF"))
        assertTrue(source.contains("SUBMIT_NATIVE_OBSERVED_PRICE"))
        assertTrue(source.contains("case'observed-proof-picker':requestNativeObservedProofPicker()"))
        assertTrue(source.contains("state.observedPriceForm.amount=e.target.value"))
        assertTrue(source.contains("receiveNativeObservedPrice:(payload)=>{receiveNativeObservedPrice(payload);return true}"))
    }

    @Test
    fun `staple watch and private history routes stay native backed and bilingual`() {
        val source = assetSource()

        assertTrue(source.contains("stapleWatchTitle:{es:"))
        assertTrue(source.contains("function nativeStapleWatch(payload)"))
        assertTrue(source.contains("'staple-watch':stapleWatch"))
        assertTrue(source.contains("'private-history':privateHistory"))
        assertTrue(source.contains("READ_NATIVE_STAPLE_WATCH"))
        assertTrue(source.contains("SET_NATIVE_STAPLE_PRODUCT"))
        assertTrue(source.contains("SELECT_NATIVE_STAPLE_STORE"))
        assertTrue(source.contains("receiveNativeStapleWatch:(payload)=>{receiveNativeStapleWatch(payload);return true}"))
        assertTrue(source.contains("case'open-staple-watch':navigate('staple-watch')"))
        assertTrue(source.contains("case'open-private-history':navigate('private-history')"))
        assertFalse(source.contains("safe(setup.status||'')"))
    }

    @Test
    fun `missing photo panel stays compact while retaining an accessible action`() {
        val source = styleSource()

        assertTrue(source.contains(".detail-image-placeholder{padding:16px 14px;gap:5px}"))
        assertTrue(source.contains(".detail-image-placeholder .icon-circle{width:42px;height:42px}"))
        assertTrue(source.contains(".detail-image-placeholder .btn{min-height:39px"))
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

    private fun styleSource(): String {
        var directory = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate =
                File(directory, "app/src/debug/assets/valuepilot_next_html/styles.css")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate valuepilot_next_html/styles.css")
    }
}
