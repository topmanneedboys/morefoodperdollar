package com.valuepilot.app

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrScanner {
    private val priceHint = Regex("(?:\\b(?:CA\\$|C\\$|US\\$|A\\$)|[$€£₹৳])\\s*(?:\\d{1,3}(?:[ ,]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?|\\b(?:\\d{1,3}(?:[ ,]\\d{3})+|\\d{1,6})(?:[.,]\\d{1,2})?\\s*(?:CAD|USD|EUR|GBP|INR|BDT|AUD)\\b", RegexOption.IGNORE_CASE)

    fun scan(bitmap: Bitmap, sourcePackage: String?, callback: (List<ValueItem>, Throwable?) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val texts = linkedSetOf<String>()
                for (block in result.textBlocks) {
                    val bt = block.text.trim()
                    if (priceHint.containsMatchIn(bt)) texts += bt
                    val lines = block.lines
                    lines.forEachIndexed { index, line ->
                        if (priceHint.containsMatchIn(line.text)) {
                            val start = maxOf(0, index - 2)
                            val end = minOf(lines.lastIndex, index + 2)
                            texts += (start..end).joinToString("\n") { lines[it].text }
                        }
                    }
                }
                val items = ValueEngine.dedupe(texts.mapNotNull { ValueEngine.analyze(it, sourcePackage) })
                recognizer.close()
                callback(items, null)
            }
            .addOnFailureListener { err -> recognizer.close(); callback(emptyList(), err) }
    }
}
