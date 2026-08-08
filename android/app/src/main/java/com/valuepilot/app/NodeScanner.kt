package com.valuepilot.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object NodeScanner {
    private val priceHint = Regex("(?:\\b(?:CA\\$|C\\$|US\\$|A\\$)|[$€£₹৳])\\s*(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?|\\b(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?\\s*(?:CAD|USD|EUR|GBP|INR|BDT|AUD)\\b", RegexOption.IGNORE_CASE)

    data class ScanResult(val items: List<ValueItem>, val candidateTexts: List<String>)

    fun scan(root: AccessibilityNodeInfo?, sourcePackage: String?): ScanResult {
        if (root == null) return ScanResult(emptyList(), emptyList())
        val candidateTexts = linkedSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < 5000) {
            val node = queue.removeFirst()
            visited++
            val own = ownText(node)
            if (own.isNotBlank() && priceHint.containsMatchIn(own)) {
                var cur: AccessibilityNodeInfo? = node
                var bestText = own
                var bestScore = scoreCandidate(node, own)
                repeat(7) {
                    cur = cur?.parent ?: return@repeat
                    val text = subtreeText(cur, 1200, 100)
                    val s = scoreCandidate(cur, text)
                    if (priceHint.containsMatchIn(text) && s > bestScore) {
                        bestScore = s
                        bestText = text
                    }
                }
                if (bestText.length in 4..1400) candidateTexts += bestText
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }

        val items = candidateTexts.mapNotNull { ValueEngine.analyze(it, sourcePackage) }
            .filterNot { item ->
                val t = item.rawText.lowercase()
                val totalish = Regex("\\b(subtotal|order total|estimated total|tax|service fee|delivery fee|tip|checkout)\\b").containsMatchIn(t)
                totalish && item.quantity == null && item.calories == null
            }
        return ScanResult(ValueEngine.dedupe(items), candidateTexts.toList())
    }

    fun findBestScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var visited = 0
        while (queue.isNotEmpty() && visited < 4000) {
            val n = queue.removeFirst()
            visited++
            if (n.isScrollable) {
                val r = Rect(); n.getBoundsInScreen(r)
                val area = r.width().toDouble() * r.height().toDouble()
                val cls = (n.className?.toString() ?: "").lowercase()
                var score = area
                if (r.height() > r.width() * .55) score *= 1.4
                if (Regex("recycler|list|scroll|webview|viewpager").containsMatchIn(cls)) score *= 1.35
                if (score > bestScore) { bestScore = score; best = n }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return best
    }

    private fun ownText(node: AccessibilityNodeInfo): String = buildString {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { append(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != node.text?.toString() }?.let {
            if (isNotEmpty()) append('\n'); append(it)
        }
    }.trim()

    private fun subtreeText(node: AccessibilityNodeInfo?, maxChars: Int, maxNodes: Int): String {
        if (node == null) return ""
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        val lines = linkedSetOf<String>()
        var count = 0
        var chars = 0
        while (queue.isNotEmpty() && count < maxNodes && chars < maxChars) {
            val n = queue.removeFirst(); count++
            val own = ownText(n).trim()
            if (own.isNotBlank() && own.length < 500) {
                lines += own
                chars += own.length + 1
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return lines.joinToString("\n").take(maxChars)
    }

    private fun scoreCandidate(node: AccessibilityNodeInfo?, text: String): Double {
        if (node == null) return -999.0
        var score = 0.0
        if (text.length in 12..700) score += 4 else if (text.length <= 1200) score += 1 else score -= 5
        val prices = ValueEngine.prices(text).size
        score += when (prices) { 1 -> 3.0; 2,3 -> 1.0; else -> -4.0 }
        val cls = (node.className?.toString() ?: "").lowercase()
        if (Regex("card|item|row|cell|viewgroup|layout").containsMatchIn(cls)) score += 2
        val r = Rect(); node.getBoundsInScreen(r)
        if (r.width() > 100 && r.height() > 35) score += 1
        return score
    }
}
