package com.valuepilot.app.next

import android.content.Context
import com.valuepilot.app.CompareHerePrivatePriceMemoryAndroidStore
import com.valuepilot.app.CompareHerePrivatePriceMemoryState
import com.valuepilot.app.CompareHerePrivatePriceMemoryStoreIssue
import com.valuepilot.app.CompareHerePrivatePriceMemoryStore
import com.valuepilot.app.PracticalShoppingPrivatePriceHistoryExport
import com.valuepilot.app.PracticalShoppingPrivatePriceHistoryExportIssue
import com.valuepilot.app.PracticalShoppingPrivatePriceHistoryForgetPresentation
import com.valuepilot.app.PracticalShoppingPrivatePriceHistoryPresentation

internal enum class ValuePilotNextPrivatePriceHistoryStatus {
    LOADING,
    AVAILABLE,
    EMPTY,
    UNAVAILABLE
}

internal data class ValuePilotNextPrivatePriceHistoryUiState(
    val status: ValuePilotNextPrivatePriceHistoryStatus,
    val presentation: PracticalShoppingPrivatePriceHistoryPresentation? = null,
    val forgetPresentation: PracticalShoppingPrivatePriceHistoryForgetPresentation? = null,
    val exportPreview: String? = null,
    val notice: String? = null
)

/** Debug Compose route over the existing device-only private comparison-history store. */
internal class ValuePilotNextPrivatePriceHistoryJourney(
    context: Context,
    private val publish: (ValuePilotNextPrivatePriceHistoryUiState?) -> Unit,
    memoryStore: CompareHerePrivatePriceMemoryStore? = null
) {
    private val store = memoryStore ?: CompareHerePrivatePriceMemoryAndroidStore(
        context.applicationContext ?: context
    )
    private var routeVisible = false
    private var closed = false
    private var currentUiState: ValuePilotNextPrivatePriceHistoryUiState? = null

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return
        routeVisible = visible
        if (visible) {
            refresh()
        } else {
            currentUiState = null
            publish(null)
        }
    }

    fun refresh() {
        if (closed || !routeVisible) return
        val loaded = store.load()
        val memory = loaded.state
        if (!loaded.accepted || memory == null) {
            publishUnavailable(loaded.issue)
            return
        }
        publishMemory(memory)
    }

    /** Re-reads the store immediately before returning text to the explicit share action. */
    fun exportForExplicitShare(expectedPreview: String): String? {
        if (closed || !routeVisible) return null
        val loaded = store.load()
        val latest = loaded.state
        if (!loaded.accepted || latest == null) {
            publishUnavailable(loaded.issue)
            return null
        }

        val export = PracticalShoppingPrivatePriceHistoryExport.from(latest)
        if (!export.accepted) {
            publishMemory(
                latest,
                notice = export.issue.toExportMessage()
            )
            return null
        }
        if (export.preview != expectedPreview) {
            publishMemory(
                latest,
                notice = "History changed since you reviewed the preview. Review the updated copy before sharing."
            )
            return null
        }
        publishMemory(latest)
        return export.text
    }

    fun forgetObservation(observationId: String) {
        if (closed || !routeVisible) return
        val loaded = store.load()
        val latest = loaded.state
        if (!loaded.accepted || latest == null) {
            publishUnavailable(loaded.issue)
            return
        }
        if (latest.entries.none { entry -> entry.observationId == observationId }) {
            publishMemory(latest, notice = "That observation changed. History has been refreshed.")
            return
        }

        val result = store.remove(observationId)
        val updated = result.state
        if (!result.accepted || updated == null) {
            val refreshed = store.load()
            refreshed.state?.let { memory ->
                publishMemory(memory, notice = result.issue.toMutationMessage())
            } ?: publishUnavailable(refreshed.issue ?: result.issue)
            return
        }
        publishMemory(updated, notice = "That private observation was removed from this device.")
    }

    fun clearAll() {
        if (closed || !routeVisible) return
        val result = store.clear()
        val updated = result.state
        if (!result.accepted || updated == null) {
            refreshWithNotice(result.issue.toMutationMessage())
            return
        }
        publishMemory(updated, notice = "Private comparison history was cleared from this device.")
    }

    fun reportShareUnavailable() {
        if (closed || !routeVisible) return
        currentUiState?.let { current ->
            publish(current.copy(notice = "No app is available to share this private history."))
        }
    }

    fun close() {
        if (closed) return
        closed = true
        routeVisible = false
        currentUiState = null
        publish(null)
    }

    private fun refreshWithNotice(notice: String) {
        val loaded = store.load()
        val latest = loaded.state
        if (!loaded.accepted || latest == null) {
            publishUnavailable(loaded.issue)
        } else {
            publishMemory(latest, notice)
        }
    }

    private fun publishUnavailable(issue: CompareHerePrivatePriceMemoryStoreIssue?) {
        val notice = issue.toLoadMessage()
        publish(
            ValuePilotNextPrivatePriceHistoryUiState(
                status = ValuePilotNextPrivatePriceHistoryStatus.UNAVAILABLE,
                notice = notice
            )
        )
    }

    private fun publishMemory(memory: CompareHerePrivatePriceMemoryState, notice: String? = null) {
        val presentation = PracticalShoppingPrivatePriceHistoryPresentation.from(memory)
        val export = PracticalShoppingPrivatePriceHistoryExport.from(memory)
        val next = ValuePilotNextPrivatePriceHistoryUiState(
            status = if (memory.entries.isEmpty()) {
                ValuePilotNextPrivatePriceHistoryStatus.EMPTY
            } else {
                ValuePilotNextPrivatePriceHistoryStatus.AVAILABLE
            },
            presentation = presentation,
            forgetPresentation = PracticalShoppingPrivatePriceHistoryForgetPresentation.from(memory),
            exportPreview = export.preview,
            notice = notice ?: export.issue.toExportMessageOrNull()
        )
        currentUiState = next
        publish(next)
    }

    private fun CompareHerePrivatePriceMemoryStoreIssue?.toLoadMessage(): String =
        when (this) {
            CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_TOO_LARGE ->
                "This device's private history could not be opened because the saved file is too large. Try again or open Scan & compare for its recovery options."
            CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID ->
                "This device's private history could not be opened. Try again or open Scan & compare for its recovery options."
            else ->
                "This device's private history is unavailable right now. Try again or open Scan & compare for its recovery options."
        }

    private fun CompareHerePrivatePriceMemoryStoreIssue?.toMutationMessage(): String =
        when (this) {
            CompareHerePrivatePriceMemoryStoreIssue.ENTRY_NOT_FOUND ->
                "That observation changed. History has been refreshed."
            CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID,
            CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_TOO_LARGE,
            CompareHerePrivatePriceMemoryStoreIssue.READ_FAILED ->
                "The private history could not be read. Try again."
            else -> "The change could not be saved. Try again."
        }

    private fun PracticalShoppingPrivatePriceHistoryExportIssue?.toExportMessage(): String =
        when (this) {
            PracticalShoppingPrivatePriceHistoryExportIssue.EMPTY ->
                "There are no observations to share."
            PracticalShoppingPrivatePriceHistoryExportIssue.OUTPUT_TOO_LARGE ->
                "This history is too large to export as one text copy."
            null -> "This history could not be prepared for sharing."
        }

    private fun PracticalShoppingPrivatePriceHistoryExportIssue?.toExportMessageOrNull(): String? =
        when (this) {
            PracticalShoppingPrivatePriceHistoryExportIssue.EMPTY -> null
            PracticalShoppingPrivatePriceHistoryExportIssue.OUTPUT_TOO_LARGE ->
                "This history is too large to export as one text copy."
            null -> null
        }
}
