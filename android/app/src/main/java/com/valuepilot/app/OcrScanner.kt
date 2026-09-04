package com.valuepilot.app

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrScanner {
    private const val MAX_RESULTS = CompareHereManualInputAdapter.MAX_OBSERVATIONS

    private val priceHint = Regex("(?:\\b(?:CA\\$|C\\$|US\\$|A\\$)|[$€£₹৳])\\s*(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?|\\b(?:\\d{1,3}(?:[ ,.]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?\\s*(?:CAD|USD|EUR|GBP|INR|BDT|AUD)\\b", RegexOption.IGNORE_CASE)

    fun scan(bitmap: Bitmap, callback: (List<String>, Throwable?) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val texts = linkedSetOf<String>()
                for (block in result.textBlocks) {
                    if (texts.size >= MAX_RESULTS) break
                    val bt = block.text.trim()
                    if (
                        bt.length <= CompareHereManualProductDraft.MAX_BLOCK_CHARS &&
                        priceHint.containsMatchIn(bt)
                    ) {
                        texts += bt
                    }
                    val lines = block.lines
                    for ((index, line) in lines.withIndex()) {
                        if (texts.size >= MAX_RESULTS) break
                        if (priceHint.containsMatchIn(line.text)) {
                            val start = maxOf(0, index - 2)
                            val end = minOf(lines.lastIndex, index + 2)
                            val context = (start..end).joinToString("\n") { lines[it].text }
                            if (context.length <= CompareHereManualProductDraft.MAX_BLOCK_CHARS) {
                                texts += context
                            }
                        }
                    }
                }
                recognizer.close()
                callback(texts.toList(), null)
            }
            .addOnFailureListener { err -> recognizer.close(); callback(emptyList(), err) }
    }
}
