package com.valuepilot.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserObservedPriceConfirmationDraftProofContentSelectionCoordinatorTest {

    @Test
    fun `selection request is allowed only while exact route owner is visible`() {
        var launches = 0
        val presentations = mutableListOf<UserObservedPriceConfirmationDraftProofContentSelectionPresentation>()
        val coordinator = coordinator({ launches += 1 }, presentations)

        coordinator.onSelectRequested()
        assertTrue(launches == 0)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSelectRequested()
        assertTrue(launches == 1)
        assertTrue(
            presentations.last() ==
                UserObservedPriceConfirmationDraftProofContentSelectionPresentation.AwaitingSelection
        )

        coordinator.onRouteVisibilityChanged(false)
        coordinator.onSelectRequested()
        assertTrue(launches == 1)
        assertTrue(
            presentations.last() ==
                UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Inactive
        )
    }

    @Test
    fun `successful bounded read is retained transiently with defensive snapshots`() {
        val presentations = mutableListOf<UserObservedPriceConfirmationDraftProofContentSelectionPresentation>()
        val coordinator = coordinator({}, presentations)
        val input = byteArrayOf(4, 8, 15, 16, 23, 42)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onContentReadResult(UserObservedPriceProofContentReadResult(bytes = input))

        val snapshot = coordinator.selectedContentSnapshotOrNull()
        assertArrayEquals(input, snapshot)
        assertTrue(
            presentations.last() ==
                UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Ready(input.size)
        )

        input[0] = 99
        assertArrayEquals(
            byteArrayOf(4, 8, 15, 16, 23, 42),
            coordinator.selectedContentSnapshotOrNull()
        )
        requireNotNull(snapshot)[1] = 88
        assertArrayEquals(
            byteArrayOf(4, 8, 15, 16, 23, 42),
            coordinator.selectedContentSnapshotOrNull()
        )
    }

    @Test
    fun `rejected read clears prior content and preserves typed issue`() {
        val presentations = mutableListOf<UserObservedPriceConfirmationDraftProofContentSelectionPresentation>()
        val coordinator = coordinator({}, presentations)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onContentReadResult(
            UserObservedPriceProofContentReadResult(bytes = byteArrayOf(1, 2, 3))
        )
        assertTrue(coordinator.selectedContentSnapshotOrNull() != null)

        coordinator.onContentReadResult(
            UserObservedPriceProofContentReadResult(
                bytes = null,
                issue = UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE
            )
        )

        assertNull(coordinator.selectedContentSnapshotOrNull())
        assertTrue(
            presentations.last() ==
                UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Rejected(
                    UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE
                )
        )
    }

    @Test
    fun `read result outside visible route cannot seed current or later route`() {
        val presentations = mutableListOf<UserObservedPriceConfirmationDraftProofContentSelectionPresentation>()
        val coordinator = coordinator({}, presentations)

        coordinator.onContentReadResult(
            UserObservedPriceProofContentReadResult(bytes = byteArrayOf(1, 2))
        )
        assertNull(coordinator.selectedContentSnapshotOrNull())

        coordinator.onRouteVisibilityChanged(true)
        assertNull(coordinator.selectedContentSnapshotOrNull())
        coordinator.onContentReadResult(
            UserObservedPriceProofContentReadResult(bytes = byteArrayOf(3, 4))
        )
        assertArrayEquals(byteArrayOf(3, 4), coordinator.selectedContentSnapshotOrNull())

        coordinator.onRouteVisibilityChanged(false)
        assertNull(coordinator.selectedContentSnapshotOrNull())

        coordinator.onRouteVisibilityChanged(true)
        assertNull(coordinator.selectedContentSnapshotOrNull())
    }

    @Test
    fun `closed owner rejects launches reads and snapshots`() {
        var launches = 0
        val presentations = mutableListOf<UserObservedPriceConfirmationDraftProofContentSelectionPresentation>()
        val coordinator = coordinator({ launches += 1 }, presentations)
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onContentReadResult(
            UserObservedPriceProofContentReadResult(bytes = byteArrayOf(7, 8))
        )

        coordinator.close()
        coordinator.onSelectRequested()
        coordinator.onContentReadResult(
            UserObservedPriceProofContentReadResult(bytes = byteArrayOf(9, 10))
        )
        coordinator.onRouteVisibilityChanged(true)

        assertTrue(coordinator.isClosed())
        assertFalse(coordinator.isVisible())
        assertTrue(launches == 0)
        assertNull(coordinator.selectedContentSnapshotOrNull())
        assertTrue(
            presentations.last() ==
                UserObservedPriceConfirmationDraftProofContentSelectionPresentation.Inactive
        )
    }

    private fun coordinator(
        launch: () -> Unit,
        presentations: MutableList<UserObservedPriceConfirmationDraftProofContentSelectionPresentation>
    ) = UserObservedPriceConfirmationDraftProofContentSelectionCoordinator(
        requestForegroundSelection = launch,
        observer =
            UserObservedPriceConfirmationDraftProofContentSelectionObserver { presentation ->
                presentations += presentation
            }
    )
}
