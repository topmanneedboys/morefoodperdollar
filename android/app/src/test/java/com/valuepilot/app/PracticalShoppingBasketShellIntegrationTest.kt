package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingBasketShellIntegrationTest {

    @Test
    fun shellBindsBasketToTheExistingHomeProjectionAndExactBasketRoute() {
        val source = activitySource().readText()

        assertTrue(
            source.contains(
                "private lateinit var basketExperience: PracticalShoppingBasketSurfaceView"
            )
        )
        assertTrue(source.contains("basketExperience = findViewById(R.id.basketExperience)"))
        assertTrue(
            source.contains(
                "basketExperience.render(PracticalShoppingBasketRenderer.render(homeState))"
            )
        )
        assertTrue(
            source.contains(
                "val basketVisible = state.selectedPrimaryTab == AppPrimaryTab.BASKET"
            )
        )
        assertTrue(
            source.contains(
                "basketExperience.visibility = if (basketVisible) View.VISIBLE else View.GONE"
            )
        )
        assertTrue(
            source.contains(
                "dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.HOME))"
            )
        )
        assertFalse(source.contains("PracticalShoppingPlanner("))
        assertFalse(source.contains("Money.parse"))
    }

    @Test
    fun basketPhysicalSurfaceStartsHiddenAndReplacesPlaceholderCopy() {
        val layout = layoutSource().readText()
        val strings = stringsSource().readText()

        val basketStart = layout.indexOf("android:id=\"@+id/basketExperience\"")
        assertTrue(basketStart >= 0)
        val basketBlock = layout.substring(basketStart, layout.indexOf("/>", basketStart) + 2)
        assertTrue(basketBlock.contains("android:visibility=\"gone\""))

        assertTrue(strings.contains("Known subtotals remain labelled"))
        assertTrue(strings.contains("Review your shopping list."))
        assertFalse(strings.contains("Shop the plan, not one item."))
        assertTrue(strings.contains("Review the shopping list from Home"))
        assertFalse(strings.contains("Review the whole-basket plan created on Home"))
        assertFalse(strings.contains("Whole-basket optimization is not available"))
    }

    private fun activitySource(): File = moduleFile("src/main/java/com/valuepilot/app/MainActivity.kt")

    private fun layoutSource(): File = moduleFile("src/main/res/layout/activity_shell.xml")

    private fun stringsSource(): File = moduleFile("src/main/res/values/strings.xml")

    private fun moduleFile(relativePath: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for shell integration test"
            }
        return File(workingDirectory, relativePath).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}
