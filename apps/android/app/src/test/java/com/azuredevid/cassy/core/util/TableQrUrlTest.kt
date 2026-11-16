package com.azuredevid.cassy.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableQrUrlTest {

    @Test
    fun buildsOrderUrlOnServerOrigin() {
        assertEquals(
            "https://demo.cassy.test/order/AbC_123-xyz",
            TableQrUrl.build("https://demo.cassy.test/", "AbC_123-xyz")
        )
    }

    @Test
    fun encodesUnexpectedTokenCharacters() {
        assertEquals("http://10.0.2.2:8080/order/a%2Fb", TableQrUrl.build("http://10.0.2.2:8080", "a/b"))
    }

    @Test
    fun refusesMissingTokenOrUnusableOrigin() {
        assertNull(TableQrUrl.build("https://demo.cassy.test", null))
        assertNull(TableQrUrl.build("https://demo.cassy.test", "  "))
        assertNull(TableQrUrl.build("demo.cassy.test", "tok"))
        assertNull(TableQrUrl.build("ftp://demo.cassy.test", "tok"))
        assertNull(TableQrUrl.build("https://", "tok"))
    }
}
