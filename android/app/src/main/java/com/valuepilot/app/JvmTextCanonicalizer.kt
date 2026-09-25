package com.valuepilot.app

import com.valuepilot.core.TextCanonicalizer
import java.text.Normalizer
import java.util.Locale

/** JVM adapter for deterministic internal identity. Never use the device display locale. */
object JvmTextCanonicalizer : TextCanonicalizer {
    override fun identity(value: String?): String = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    override fun search(value: String?): String = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .lowercase(Locale.ROOT)
        .replace('&', ' ')
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
