package com.example

import com.example.data.model.MarketplacePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MarketplacePriceTest {
    @Test
    fun inputValidationKeepsPositivePhpAmountsNumeric() {
        assertEquals(BigDecimal("1500"), MarketplacePrice.parseInput("1500"))
        assertEquals(BigDecimal("1500.50"), MarketplacePrice.parseInput("1500.50"))
        assertNull(MarketplacePrice.parseInput("₱1500"))
        assertNull(MarketplacePrice.parseInput("-1"))
        assertNull(MarketplacePrice.parseInput("1500.999"))
    }

    @Test
    fun legacyMojibakePriceCanBeReadAsAmount() {
        assertEquals(BigDecimal("1500"), MarketplacePrice.parseStored("â‚±1500"))
        assertEquals(BigDecimal("1500.5"), MarketplacePrice.parseStored(1500.5))
    }

    @Test
    fun displaysPhilippinePesoWithTwoDecimalPlaces() {
        assertEquals("₱1,500.00", MarketplacePrice.format(BigDecimal("1500")))
    }
}
