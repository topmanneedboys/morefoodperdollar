package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedRouteCoordinatorTest {

    @Test
    fun `session is lazy and first visible route entry refreshes exactly once`() {
        val factory = RecordingSessionFactory()
        val coordinator = PracticalShoppingSavedRouteCoordinator(factory::create)

        coordinator.onRouteVisibilityChanged(false)
        assertEquals(0, factory.createCalls)

        coordinator.onRouteVisibilityChanged(true)
        assertEquals(1, factory.createCalls)
        assertEquals(1, factory.session.refreshCalls)

        coordinator.onRouteVisibilityChanged(true)
        assertEquals(1, factory.createCalls)
        assertEquals(1, factory.session.refreshCalls)
    }

    @Test
    fun `leaving and reentering refreshes same Activity session`() {
        val factory = RecordingSessionFactory()
        val coordinator = PracticalShoppingSavedRouteCoordinator(factory::create)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onRouteVisibilityChanged(false)
        coordinator.onRouteVisibilityChanged(true)

        assertEquals(1, factory.createCalls)
        assertEquals(2, factory.session.refreshCalls)
        assertFalse(factory.session.closed)
    }

    @Test
    fun `refresh action is accepted only while Saved route is visible`() {
        val factory = RecordingSessionFactory()
        val coordinator = PracticalShoppingSavedRouteCoordinator(factory::create)

        coordinator.onSurfaceAction(PracticalShoppingSavedSurfaceAction.Refresh)
        assertEquals(0, factory.createCalls)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(PracticalShoppingSavedSurfaceAction.Refresh)
        assertEquals(2, factory.session.refreshCalls)

        coordinator.onRouteVisibilityChanged(false)
        coordinator.onSurfaceAction(PracticalShoppingSavedSurfaceAction.Refresh)
        assertEquals(2, factory.session.refreshCalls)
    }

    @Test
    fun `typed preference action cannot manufacture session before route entry and is forwarded unchanged after entry`() {
        val factory = RecordingSessionFactory()
        val coordinator = PracticalShoppingSavedRouteCoordinator(factory::create)
        val exactAction =
            PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(
                ShoppingItemKey("eggs")
            )
        val surfaceAction = PracticalShoppingSavedSurfaceAction.Preference(exactAction)

        coordinator.onSurfaceAction(surfaceAction)
        assertEquals(0, factory.createCalls)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(surfaceAction)

        assertEquals(1, factory.session.actions.size)
        assertSame(exactAction, factory.session.actions.single())
    }

    @Test
    fun `check price action is navigation only and never mutates the Saved session`() {
        val factory = RecordingSessionFactory()
        val coordinator = PracticalShoppingSavedRouteCoordinator(factory::create)
        val action =
            PracticalShoppingSavedSurfaceAction.CheckProductPrice(
                itemKey = ShoppingItemKey("eggs"),
                displayName = "Free-range eggs"
            )

        coordinator.onSurfaceAction(action)
        assertEquals(0, factory.createCalls)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(action)

        assertEquals(1, factory.createCalls)
        assertTrue(factory.session.actions.isEmpty())
        assertEquals(1, factory.session.refreshCalls)
    }

    @Test
    fun `close releases current session and permanently ignores later route and surface events`() {
        val factory = RecordingSessionFactory()
        val coordinator = PracticalShoppingSavedRouteCoordinator(factory::create)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.close()

        assertTrue(factory.session.closed)
        assertEquals(1, factory.session.closeCalls)

        coordinator.onRouteVisibilityChanged(false)
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(PracticalShoppingSavedSurfaceAction.Refresh)
        coordinator.close()

        assertEquals(1, factory.createCalls)
        assertEquals(1, factory.session.refreshCalls)
        assertEquals(1, factory.session.closeCalls)
    }

    private class RecordingSessionFactory {
        val session = RecordingSession()
        var createCalls = 0

        fun create(): PracticalShoppingSavedRouteSession {
            createCalls += 1
            return session
        }
    }

    private class RecordingSession : PracticalShoppingSavedRouteSession {
        var refreshCalls = 0
        val actions = mutableListOf<PracticalShoppingSavedExactPreferenceUiAction>()
        var closeCalls = 0
        var closed = false

        override fun refresh() {
            refreshCalls += 1
        }

        override fun selectAction(action: PracticalShoppingSavedExactPreferenceUiAction) {
            actions += action
        }

        override fun close() {
            closeCalls += 1
            closed = true
        }
    }
}
