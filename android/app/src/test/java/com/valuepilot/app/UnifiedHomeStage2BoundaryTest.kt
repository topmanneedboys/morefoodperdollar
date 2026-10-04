package com.valuepilot.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the Stage 2 Home shell presentation-only and bounded. The executable
 * renderer tests own the state contract; these checks prevent an accidental
 * reintroduction of shopping authority into the new visual surface.
 */
class UnifiedHomeStage2BoundaryTest {

    @Test
    fun home_command_center_uses_existing_render_state_and_shared_motion_tokens() {
        val source = source("PracticalShoppingHomeSurfaceView.kt")
        assertTrue(source.contains("private fun renderCommandCenter"))
        assertTrue(source.contains("state.confirmedPurchaseCount"))
        assertTrue(source.contains("ValuePilotMotion.reveal(commandCenterCard)"))
        assertTrue(source.contains("onOpenConfirmedPurchaseHistory?.invoke()"))
        assertTrue(source.contains("ValuePilotDesignTokens.Spacing"))
        assertTrue(source.contains("ValuePilotProductCard(context)"))
        assertTrue(!source.contains("LocalSamplePracticalShoppingDemo.initialModel()"))
    }

    @Test
    fun motion_boundary_respects_shared_duration_and_animator_disablement() {
        val source = source("ValuePilotMotion.kt")
        assertTrue(source.contains("ValuePilotDesignTokens.Motion.DISCLOSURE_DURATION_MS"))
        assertTrue(source.contains("ValueAnimator.areAnimatorsEnabled()"))
        assertTrue(source.contains("view.alpha = 1f"))
    }

    @Test
    fun home_route_hides_the_duplicate_long_shell_header() {
        val activity = source("MainActivity.kt")
        val layout = source("res/layout/activity_shell.xml")
        assertTrue(activity.contains("screenHeaderCard.visibility"))
        assertTrue(activity.contains("state.route == AppRoute.HOME"))
        assertTrue(layout.contains("android:id=\"@+id/screenHeaderCard\""))
    }

    @Test
    fun secondary_routes_expose_the_existing_typed_back_action() {
        val activity = source("MainActivity.kt")
        val layout = source("res/layout/activity_shell.xml")
        assertTrue(activity.contains("screenBackAction.setOnClickListener { dispatch(AppShellIntent.NavigateBack) }"))
        assertTrue(activity.contains("if (state.canNavigateBack) View.VISIBLE else View.GONE"))
        assertTrue(layout.contains("android:id=\"@+id/screenBackAction\""))
        assertTrue(layout.contains("@string/shell_back_description"))
    }

    private fun source(name: String): String {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                if (name.startsWith("res/")) {
                    File(directory, "app/src/main/$name")
                } else {
                    File(directory, "app/src/main/java/com/valuepilot/app/$name")
                }
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $name")
    }
}
