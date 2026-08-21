package com.valuepilot.app

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

/**
 * Performs one bounded accessibility-tree pass and immediately drops all node references.
 * Expensive parsing happens later from immutable [ProductCardSnapshot] values.
 */
object NodeScanner {
    private const val MAX_NODES = 5_000
    private const val CAPTURE_BUDGET_MS = 30L
    private const val MAX_CARD_NODES = 100
    private const val MAX_CARD_CHARS = 1_400
    private val priceHint = Regex(
        "(?:\\b(?:CA\\$|C\\$|US\\$|A\\$)|[$€£₹৳])\\s*(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?|" +
            "\\b(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?\\s*(?:CAD|USD|EUR|GBP|INR|BDT|AUD)\\b",
        RegexOption.IGNORE_CASE
    )

    private data class PendingNode(
        val node: AccessibilityNodeInfo,
        val parentIndex: Int?,
        val childIndex: Int,
        val depth: Int
    )

    private data class NodeRecord(
        val parentIndex: Int?,
        val childIndex: Int,
        val depth: Int,
        val ownLines: List<String>,
        val text: String,
        val contentDescription: String,
        val viewId: String?,
        val className: String,
        val bounds: ScreenBounds,
        val clickable: Boolean,
        val scrollable: Boolean,
        val editable: Boolean,
        val focused: Boolean,
        val heading: Boolean,
        val children: MutableList<Int> = mutableListOf(),
        var subtreeNodes: Int = 1,
        var subtreePrices: Int = 0,
        var subtreeChars: Int = 0
    )

    fun capture(root: AccessibilityNodeInfo?, sourcePackage: String): ScanBatch? {
        if (root == null) return null
        val startedNanos = SystemClock.elapsedRealtimeNanos()
        val deadlineNanos = startedNanos + CAPTURE_BUDGET_MS * 1_000_000L
        val records = ArrayList<NodeRecord>(512)
        val queue = ArrayDeque<PendingNode>()
        queue.add(PendingNode(root, null, -1, 0))
        var truncated = false

        while (queue.isNotEmpty() && records.size < MAX_NODES) {
            if (records.size >= 256 && SystemClock.elapsedRealtimeNanos() >= deadlineNanos) {
                truncated = true
                break
            }
            val pending = queue.removeFirst()
            val node = pending.node
            val ownLines = ownLines(node)
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val index = records.size
            val record = NodeRecord(
                parentIndex = pending.parentIndex,
                childIndex = pending.childIndex,
                depth = pending.depth,
                ownLines = ownLines,
                text = node.text?.toString().orEmpty().trim(),
                contentDescription = node.contentDescription?.toString().orEmpty().trim(),
                viewId = runCatching { node.viewIdResourceName }.getOrNull(),
                className = node.className?.toString().orEmpty(),
                bounds = ScreenBounds(rect.left, rect.top, rect.right, rect.bottom),
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                editable = node.isEditable,
                focused = node.isFocused,
                heading = isHeading(node, pending.depth),
                subtreePrices = if (ownLines.any(priceHint::containsMatchIn)) 1 else 0,
                subtreeChars = ownLines.sumOf(String::length)
            )
            records += record
            pending.parentIndex?.let { parent -> records[parent].children += index }

            for (child in 0 until node.childCount) {
                node.getChild(child)?.let { queue.add(PendingNode(it, index, child, pending.depth + 1)) }
            }
        }
        if (queue.isNotEmpty()) truncated = true

        for (index in records.indices.reversed()) {
            val record = records[index]
            val parent = record.parentIndex ?: continue
            records[parent].subtreeNodes += record.subtreeNodes
            records[parent].subtreePrices += record.subtreePrices
            records[parent].subtreeChars += record.subtreeChars
        }

        val selected = linkedSetOf<Int>()
        val priceNodes = records.indices.filter { records[it].ownLines.any(priceHint::containsMatchIn) }
        for (priceIndex in priceNodes) {
            var current: Int? = priceIndex
            var best = priceIndex
            var bestScore = candidateScore(records[priceIndex])
            repeat(8) {
                val index = current ?: return@repeat
                val score = candidateScore(records[index])
                if (score > bestScore) {
                    best = index
                    bestScore = score
                }
                current = records[index].parentIndex
            }
            selected += best
        }

        val now = System.currentTimeMillis()
        val cards = selected.mapNotNull { index ->
            val rawText = collectSubtreeText(records, index)
            if (rawText.length !in 4..MAX_CARD_CHARS || !priceHint.containsMatchIn(rawText)) return@mapNotNull null
            val record = records[index]
            val roughName = roughName(rawText)
            if (roughName.isBlank()) return@mapNotNull null
            val quantityIdentity = Regex(
                "\\b\\d+(?:[.,]\\d+)?\\s*(?:mg|g|kg|oz|lb|ml|l|fl\\s*oz|ct|count|pack|pk|units?)\\b",
                RegexOption.IGNORE_CASE
            ).find(rawText)?.value.orEmpty().lowercase(Locale.ROOT)
            val cardIdentity = listOf(sourcePackage, record.viewId.orEmpty(), roughName, quantityIdentity).joinToString("|")
            val clickIndex = clickableTarget(records, index)
            ProductCardSnapshot(
                cardFingerprint = StableIds.text(cardIdentity),
                contentFingerprint = StableIds.text(ValueEngine.normalize(rawText)),
                rawText = rawText,
                locatorSeed = LocatorSeed(
                    windowId = root.windowId,
                    viewId = record.viewId,
                    contentDescription = record.contentDescription.ifBlank { null },
                    bounds = record.bounds,
                    cardPath = pathFor(records, index),
                    clickPath = clickIndex?.let { pathFor(records, it) }
                ),
                capturedAtMillis = now
            )
        }.distinctBy { "${it.cardFingerprint}|${it.contentFingerprint}" }

        val contextSignals = records.asSequence().take(900).map { record ->
            ContextSignal(
                text = record.text,
                contentDescription = record.contentDescription,
                viewId = record.viewId,
                className = record.className,
                editable = record.editable,
                focused = record.focused,
                heading = record.heading,
                depth = record.depth
            )
        }.toList()
        val observation = SearchContextDetector.detect(sourcePackage, contextSignals, now)
        val pageContent = records.asSequence()
            .filter { it.depth <= 7 }
            .flatMap { it.ownLines.asSequence() }
            .filterNot(priceHint::containsMatchIn)
            .take(40)
            .joinToString("|")
        val scrollable = bestScrollable(records)?.let { index ->
            val record = records[index]
            ScrollLocator(pathFor(records, index), record.viewId, record.className, record.bounds)
        }
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0

        return ScanBatch(
            packageName = sourcePackage,
            windowId = root.windowId,
            observation = observation,
            pageContentFingerprint = StableIds.text(pageContent),
            cards = cards,
            scrollLocator = scrollable,
            metrics = ScanMetrics(
                visitedNodes = records.size,
                priceNodes = priceNodes.size,
                candidateCards = cards.size,
                captureMillis = elapsedMs,
                truncated = truncated
            )
        )
    }

    fun performScroll(root: AccessibilityNodeInfo?, locator: ScrollLocator?, forward: Boolean): Boolean {
        if (root == null) return false
        val pathNode = locator?.path?.let { nodeAtPath(root, it) }
        val target = pathNode?.takeIf { it.isScrollable }
            ?: locator?.viewId?.let { id ->
                runCatching { root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isScrollable } }.getOrNull()
            }
            ?: findBestScrollable(root)
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return target?.performAction(action) == true
    }

    fun performClick(root: AccessibilityNodeInfo?, path: NodePath?): Boolean {
        if (root == null || path == null) return false
        var node = nodeAtPath(root, path) ?: return false
        repeat(5) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = node.parent ?: return false
        }
        return false
    }

    private fun nodeAtPath(root: AccessibilityNodeInfo, path: NodePath): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = root
        for (childIndex in path.childIndices) current = current?.getChild(childIndex) ?: return null
        return current
    }

    private fun findBestScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var visited = 0
        while (queue.isNotEmpty() && visited < 2_500) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                val className = node.className?.toString().orEmpty().lowercase(Locale.ROOT)
                var score = rect.width().toDouble() * rect.height().toDouble()
                if (rect.height() > rect.width() * .55) score *= 1.35
                if (Regex("recycler|list|scroll|webview|viewpager").containsMatchIn(className)) score *= 1.3
                if (score > bestScore) {
                    best = node
                    bestScore = score
                }
            }
            for (child in 0 until node.childCount) node.getChild(child)?.let(queue::add)
        }
        return best
    }

    private fun ownLines(node: AccessibilityNodeInfo): List<String> = buildList {
        node.text?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() && it !in this }?.let(::add)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.trim()?.takeIf { it.isNotBlank() && it !in this }?.let(::add)
        }
    }

    private fun isHeading(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isHeading) return true
        val id = runCatching { node.viewIdResourceName }.getOrNull().orEmpty().lowercase(Locale.ROOT)
        val className = node.className?.toString().orEmpty().lowercase(Locale.ROOT)
        return depth <= 7 && (id.contains("title") || id.contains("heading") || className.contains("toolbar"))
    }

    private fun candidateScore(record: NodeRecord): Double {
        var score = 0.0
        score += when (record.subtreePrices) {
            1 -> 6.0
            2, 3 -> 3.0
            4 -> .5
            else -> -8.0
        }
        score += when {
            record.subtreeNodes in 2..45 -> 4.0
            record.subtreeNodes <= 90 -> 2.0
            record.subtreeNodes <= 140 -> 0.0
            else -> -5.0
        }
        score += when {
            record.subtreeChars in 12..700 -> 4.0
            record.subtreeChars <= MAX_CARD_CHARS -> 1.0
            else -> -5.0
        }
        val className = record.className.lowercase(Locale.ROOT)
        if (Regex("card|item|row|cell|viewgroup|layout").containsMatchIn(className)) score += 2.0
        if (record.bounds.isUsable()) score += 1.0
        if (record.clickable) score += 1.5
        if (record.scrollable) score -= 5.0
        return score
    }

    private fun collectSubtreeText(records: List<NodeRecord>, rootIndex: Int): String {
        val queue = ArrayDeque<Int>()
        queue.add(rootIndex)
        val lines = linkedSetOf<String>()
        var visited = 0
        var characters = 0
        while (queue.isNotEmpty() && visited < MAX_CARD_NODES && characters < MAX_CARD_CHARS) {
            val record = records[queue.removeFirst()]
            visited++
            for (line in record.ownLines) {
                if (line.length >= 500 || !lines.add(line)) continue
                characters += line.length + 1
                if (characters >= MAX_CARD_CHARS) break
            }
            record.children.forEach(queue::add)
        }
        return lines.joinToString("\n").take(MAX_CARD_CHARS)
    }

    private fun roughName(rawText: String): String = rawText.lineSequence()
        .map(ValueEngine::sanitizeNameLine)
        .firstOrNull { line ->
            line.length in 2..120 && Regex("\\p{L}").containsMatchIn(line) &&
                !Regex("^(?:add|select|customize|deal|sale|save|in stock|out of stock|delivery|sponsored)\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(line)
        }
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        ?.trim()
        .orEmpty()

    private fun clickableTarget(records: List<NodeRecord>, cardIndex: Int): Int? {
        var current: Int? = cardIndex
        repeat(5) {
            val index = current ?: return@repeat
            if (records[index].clickable) return index
            current = records[index].parentIndex
        }
        val queue = ArrayDeque<Int>()
        queue.add(cardIndex)
        var visited = 0
        while (queue.isNotEmpty() && visited < 80) {
            val index = queue.removeFirst()
            visited++
            if (records[index].clickable) return index
            records[index].children.forEach(queue::add)
        }
        return null
    }

    private fun pathFor(records: List<NodeRecord>, index: Int): NodePath {
        val path = ArrayDeque<Int>()
        var current: Int? = index
        while (current != null) {
            val record = records[current]
            if (record.childIndex >= 0) path.addFirst(record.childIndex)
            current = record.parentIndex
        }
        return NodePath(path.toList())
    }

    private fun bestScrollable(records: List<NodeRecord>): Int? = records.indices
        .asSequence()
        .filter { records[it].scrollable }
        .maxByOrNull { index ->
            val record = records[index]
            var score = record.bounds.area.toDouble()
            if (record.bounds.height > record.bounds.width * .55) score *= 1.35
            if (Regex("recycler|list|scroll|webview|viewpager", RegexOption.IGNORE_CASE).containsMatchIn(record.className)) score *= 1.3
            score
        }
}
