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
