package com.azuredevid.cassy.core.util

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class RupiahVisualTransformationTest {

    @Test
    fun `formats raw digits with Indonesian thousand separators and maps offsets`() {
        val result = ThousandsSeparatorTransformation.filter(AnnotatedString("200000"))
        assertEquals("200.000", result.text.text)
        assertEquals(0, result.offsetMapping.originalToTransformed(0))
        assertEquals(4, result.offsetMapping.originalToTransformed(3))
        assertEquals(7, result.offsetMapping.originalToTransformed(6))
        assertEquals(6, result.offsetMapping.transformedToOriginal(7))
    }

    @Test
    fun `handles millions and empty strings safely`() {
        assertEquals("", ThousandsSeparatorTransformation.filter(AnnotatedString("")).text.text)
        assertEquals("1.000.000", ThousandsSeparatorTransformation.filter(AnnotatedString("1000000")).text.text)
    }
}
