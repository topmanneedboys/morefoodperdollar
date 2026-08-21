package com.valuepilot.app

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Experimental Android live-capture adapter. The permanent product core does not depend on this
 * class and continues to accept observations from other providers when it is absent.
 */
class AndroidLiveConnector : ProductObservationProvider<AccessibilityNodeInfo?> {
    override fun capture(input: AccessibilityNodeInfo?, sourceId: String): ScanBatch? =
        NodeScanner.capture(input, sourceId)

    fun scroll(root: AccessibilityNodeInfo?, locator: ScrollLocator?, forward: Boolean): Boolean =
        NodeScanner.performScroll(root, locator, forward)

    fun click(root: AccessibilityNodeInfo?, path: NodePath?): Boolean = NodeScanner.performClick(root, path)
}
