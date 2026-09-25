package com.valuepilot.app

import java.util.ArrayDeque
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerArchitecturePerformanceTest {
    private class SyntheticNode(
        val price: Boolean = false,
        var parent: SyntheticNode? = null,
        val children: MutableList<SyntheticNode> = mutableListOf()
    ) {
        fun add(child: SyntheticNode): SyntheticNode {
            child.parent = this
            children += child
            return child
        }
    }

    @Test
    fun onePassSnapshotsEliminateRepeatedAncestorSubtreeWalks() {
        for (products in listOf(20, 60, 100, 160, 250, 500)) {
            val root = tree(products)
            var legacyOperations = 0L
            val legacyNanos = measureNanoTime { legacyOperations = legacyWork(root) }
            var snapshotOperations = 0L
            val snapshotNanos = measureNanoTime { snapshotOperations = snapshotWork(root) }
            val reduction = legacyOperations.toDouble() / snapshotOperations

            println(
                "VALUEPIL_SCAN_OPS products=$products legacy_visits=$legacyOperations " +
                    "snapshot_visits=$snapshotOperations reduction=${String.format(Locale.US, "%.2f", reduction)}x " +
                    "legacy_ms=${String.format(Locale.US, "%.3f", legacyNanos / 1_000_000.0)} " +
                    "snapshot_ms=${String.format(Locale.US, "%.3f", snapshotNanos / 1_000_000.0)}"
            )
            assertTrue("expected at least 20x less scanner work for $products products", reduction >= 20.0)
        }
    }

    /** Mirrors the v101 shape: full BFS, then up to seven capped subtree walks per price node. */
    private fun legacyWork(root: SyntheticNode): Long {
        val queue = ArrayDeque<SyntheticNode>()
        queue.add(root)
        var work = 0L
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            work++
            if (node.price) {
                var current: SyntheticNode? = node
                repeat(7) {
                    current = current?.parent ?: return@repeat
                    work += cappedSubtreeSize(current, 100)
                }
            }
            node.children.forEach(queue::add)
        }
        return work
    }

    /** Mirrors the new shape: one tree snapshot plus one bounded gather for each distinct card. */
    private fun snapshotWork(root: SyntheticNode): Long {
        val queue = ArrayDeque<SyntheticNode>()
        queue.add(root)
        var work = 0L
        val cards = mutableListOf<SyntheticNode>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            work++
            if (node.parent?.parent == root) cards += node
            node.children.forEach(queue::add)
        }
        for (card in cards) work += cappedSubtreeSize(card, 100)
        return work
    }

    private fun cappedSubtreeSize(root: SyntheticNode, limit: Int): Int {
        val queue = ArrayDeque<SyntheticNode>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < limit) {
            val node = queue.removeFirst()
            count++
            node.children.forEach(queue::add)
        }
        return count
    }

    private fun tree(products: Int): SyntheticNode {
        val root = SyntheticNode()
        val list = root.add(SyntheticNode())
        repeat(products) {
            val card = list.add(SyntheticNode())
            card.add(SyntheticNode())
            card.add(SyntheticNode())
            card.add(SyntheticNode(price = true))
        }
        return root
    }
}
