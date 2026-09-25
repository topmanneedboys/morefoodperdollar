package com.valuepilot.app

data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()
    fun isUsable(): Boolean = width > 24 && height > 20
}

data class NodePath(val childIndices: List<Int> = emptyList())

data class LocatorSeed(
    val windowId: Int,
    val viewId: String?,
    val contentDescription: String?,
    val bounds: ScreenBounds,
    val cardPath: NodePath,
    val clickPath: NodePath?
)

data class ProductCardSnapshot(
    val cardFingerprint: String,
    val contentFingerprint: String,
    val rawText: String,
    val locatorSeed: LocatorSeed,
    val capturedAtMillis: Long
)

data class ScrollLocator(
    val path: NodePath,
    val viewId: String?,
    val className: String,
    val bounds: ScreenBounds
)

data class ScanMetrics(
    val visitedNodes: Int,
    val priceNodes: Int,
    val candidateCards: Int,
    val captureMillis: Double,
    val fullTreePasses: Int = 1,
    val ancestorSubtreeTraversals: Int = 0,
    val truncated: Boolean = false
)

data class ScanBatch(
    val packageName: String,
    val windowId: Int,
    val observation: ContextObservation,
    val pageContentFingerprint: String,
    val cards: List<ProductCardSnapshot>,
    val scrollLocator: ScrollLocator?,
    val metrics: ScanMetrics
)

data class ParsedCard(
    val cardFingerprint: String,
    val contentFingerprint: String,
    val item: ValueItem?
)
