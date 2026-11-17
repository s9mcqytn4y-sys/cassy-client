package com.azuredevid.cassy.core.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Formats raw digit strings as Indonesian thousand-grouped numbers (e.g. "200000" -> "200.000")
 * while keeping ViewModel state as plain digits for lossless Long parsing.
 */
object ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val sb = StringBuilder()
        val rawToTransformed = IntArray(raw.length + 1)
        var tIdx = 0
        for (i in raw.indices) {
            if (i > 0 && (raw.length - i) % 3 == 0) {
                sb.append('.')
                tIdx++
            }
            rawToTransformed[i] = tIdx
            sb.append(raw[i])
            tIdx++
        }
        rawToTransformed[raw.length] = tIdx

        val transformed = sb.toString()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return rawToTransformed[offset.coerceIn(0, raw.length)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, transformed.length)
                for (i in raw.indices) {
                    if (rawToTransformed[i] >= clamped) return i
                }
                return raw.length
            }
        }
        return TransformedText(AnnotatedString(transformed), mapping)
    }
}
