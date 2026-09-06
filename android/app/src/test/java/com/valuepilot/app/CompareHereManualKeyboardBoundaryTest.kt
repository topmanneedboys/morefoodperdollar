package com.valuepilot.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompareHereManualKeyboardBoundaryTest {

    @Test
    fun addingAProductFocusesTheNewEditableBlockAfterTheDraftIsUpdated() {
        val source = source().readText()
        val addBlock =
            source
                .substringAfter("addProductButton.setOnClickListener")
                .substringBefore("compareButton.setOnClickListener")

        assertTrue(addBlock.contains("addProductInput(\"\")"))
        assertTrue(addBlock.contains("onProductsChanged()"))
        assertTrue(addBlock.contains("updateAddProductButton()"))
        assertTrue(addBlock.contains("focusProductInput(productInputs.lastIndex)"))
        assertTrue(
            addBlock.indexOf("focusProductInput(productInputs.lastIndex)") >
                addBlock.indexOf("onProductsChanged()")
        )
    }

    @Test
    fun addedProductFocusRemainsAnEditableNavigationOnlyAffordance() {
        val source = source().readText()
        val focus =
            source
                .substringAfter("private fun focusProductInput(index: Int?)")
                .substringBefore("private fun finishBarcodeRequest")

        assertTrue(focus.contains("val input = index?.let(productInputs::getOrNull) ?: return"))
        assertTrue(focus.contains("input.requestFocus()"))
        assertTrue(focus.contains("input.setSelection(input.text?.length ?: 0)"))
        assertTrue(focus.contains("manager?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)"))
        assertTrue(!focus.contains("CompareHereManualRouteCoordinator"))
        assertTrue(!focus.contains("Money.parse"))
    }

    @Test
    fun keyboardDoneAdvancesEntriesAndUsesOnlyTheVisibleCompareGate() {
        val source = source().readText()
        val editorAction =
            source
                .substringAfter("input.imeOptions = EditorInfo.IME_ACTION_DONE")
                .substringBefore("label.labelFor = input.id")

        assertTrue(source.contains("import android.view.inputmethod.EditorInfo"))
        assertTrue(editorAction.contains("input.setOnEditorActionListener"))
        assertTrue(editorAction.contains("EditorInfo.IME_ACTION_DONE"))
        assertTrue(editorAction.contains("EditorInfo.IME_ACTION_NEXT"))
        assertTrue(editorAction.contains("val currentIndex = productInputs.indexOf(input)"))
        assertTrue(editorAction.contains("val nextIndex = currentIndex + 1"))
        assertTrue(editorAction.contains("focusProductInput(nextIndex)"))
        assertTrue(editorAction.contains("else if (compareButton.isEnabled)"))
        assertTrue(editorAction.contains("runComparison("))
        assertTrue(editorAction.contains("persist = true"))
        assertTrue(
            editorAction.indexOf("focusProductInput(nextIndex)") <
                editorAction.indexOf("else if (compareButton.isEnabled)")
        )
        assertTrue(
            editorAction.indexOf("compareButton.isEnabled") <
                editorAction.indexOf("runComparison(")
        )
        assertTrue(!editorAction.contains("Money.parse"))
        assertTrue(!editorAction.contains("CompareHereManualInputAdapter"))
    }

    @Test
    fun keyboardActionLabelTracksTheFinalProductEditorAfterAddAndRemove() {
        val source = source().readText()

        listOf(
            "syncProductEditorImeActions()",
            "val finalIndex = productInputs.lastIndex",
            "if (index == finalIndex)",
            "EditorInfo.IME_ACTION_DONE",
            "EditorInfo.IME_ACTION_NEXT",
            "refreshProductInputRows()",
            "productInputs += input"
        ).forEach { required ->
            assertTrue("Expected dynamic keyboard action binding $required", source.contains(required))
        }

        val syncStart = source.indexOf("private fun syncProductEditorImeActions()")
        assertTrue("Expected keyboard action synchronizer", syncStart >= 0)
        val syncEnd = source.indexOf("private fun updateRemoveProductButtons", syncStart)
        assertTrue("Expected bounded keyboard action synchronizer", syncEnd > syncStart)
        val sync = source.substring(syncStart, syncEnd)
        assertTrue(sync.indexOf("EditorInfo.IME_ACTION_DONE") < sync.indexOf("EditorInfo.IME_ACTION_NEXT"))
        assertTrue(!sync.contains("CompareHereManualRouteCoordinator"))
        assertTrue(!sync.contains("Money.parse"))
        assertTrue(!sync.contains("PracticalShoppingPlanner"))
    }

    @Test
    fun eachDynamicProductEditorIsAssociatedWithItsVisibleProductLabel() {
        val source = source().readText()
        val inputBlock =
            source
                .substringAfter("val input = EditText(this).apply")
                .substringBefore("val removeButton = Button(this).apply")

        assertTrue(inputBlock.contains("id = View.generateViewId()"))
        assertTrue(source.contains("label.labelFor = input.id"))
        assertTrue(
            source.indexOf("label.labelFor = input.id") >
                source.indexOf("val input = EditText(this).apply")
        )
        assertTrue(!inputBlock.contains("Money.parse"))
        assertTrue(!inputBlock.contains("PracticalShoppingPlanner"))
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/ComparisonActivity.kt"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}
