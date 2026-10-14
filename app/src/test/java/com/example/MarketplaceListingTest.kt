package com.example

import com.example.data.model.MarketplaceDateFormatter
import com.example.data.model.MarketplaceItem
import com.example.data.model.MarketplaceListingOrdering
import com.example.data.model.MarketplaceTextLimits
import com.example.data.repository.MarketplaceStoragePath
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MarketplaceListingTest {
    @Test
    fun marketplaceImagePathsMatchTheStorageRuleShape() {
        assertEquals(
            "marketplace/resident-one/listing-1/image_0",
            MarketplaceStoragePath.imagePath("resident-one", "listing-1", 0)
        )
        assertEquals(
            "marketplace/resident-one/listing-1/image_2_${"a".repeat(32)}",
            MarketplaceStoragePath.imagePath("resident-one", "listing-1", 2, "a".repeat(32))
        )
        assertTrue(runCatching { MarketplaceStoragePath.imagePath("resident-one", "listing-1", 3) }.isFailure)
        assertTrue(runCatching { MarketplaceStoragePath.imagePath("resident-one", "listing-1", 0, "bad") }.isFailure)
    }

    @Test
    fun partitionsByCreatorUidAndSortsEachSectionNewestFirst() {
        val listings = listOf(
            listing("other-old", "other", 1000),
            listing("mine-old", "me", 2000),
            listing("mine-new", "me", 4000, status = MarketplaceItem.SOLD),
            listing("other-new", "other", 5000),
            listing("mine-undated", "me", null)
        )

        val sections = MarketplaceListingOrdering.partition(listings, "me")

        assertEquals(listOf("mine-new", "mine-old", "mine-undated"), sections.mine.map { it.id })
        assertEquals(listOf("other-new", "other-old"), sections.others.map { it.id })
        assertTrue((sections.mine + sections.others).map { it.id }.toSet().size == listings.size)
    }

    @Test
    fun legacyStatusAndMissingTimestampUseSafeDisplayValues() {
        val legacy = listing("legacy", "me", null)
        assertEquals(MarketplaceItem.AVAILABLE, legacy.displayAvailability)
        assertEquals("Date unavailable", MarketplaceDateFormatter.format(null))
    }

    @Test
    fun formatsCreationTimeInProvidedDeviceTimezoneAndLocale() {
        val timestamp = Timestamp(Date.from(java.time.Instant.parse("2026-10-12T02:45:00Z")))
        val formatted = MarketplaceDateFormatter.format(
            timestamp,
            Locale.US,
            TimeZone.getTimeZone("Asia/Manila")
        )
        assertEquals("Oct 12, 2026 · 10:45 AM", formatted)
    }

    @Test
    fun marketplaceTextLimitsCountUnicodeCodePointsAndTrimValues() {
        val supplementary = "😀"
        assertEquals(75, MarketplaceTextLimits.characterCount(supplementary.repeat(75)))
        assertEquals(supplementary.repeat(75), MarketplaceTextLimits.truncate(supplementary.repeat(76), 75))
        val (title, description) = MarketplaceTextLimits.validate("  Chair  ", "  Gently used  ")
        assertEquals("Chair", title)
        assertEquals("Gently used", description)
    }

    @Test
    fun marketplaceTextLimitsRejectBlankAndOverlongFields() {
        assertTrue(runCatching { MarketplaceTextLimits.validate("  ", "Details") }.isFailure)
        assertTrue(runCatching { MarketplaceTextLimits.validate("Title", " ") }.isFailure)
        assertTrue(runCatching { MarketplaceTextLimits.validate("x".repeat(76), "Details") }.isFailure)
        assertTrue(runCatching { MarketplaceTextLimits.validate("Title", "x".repeat(1001)) }.isFailure)
    }

    private fun listing(id: String, uid: String, epochMillis: Long?, status: String? = null) = MarketplaceItem(
        id = id,
        sellerUid = uid,
        title = id,
        price = BigDecimal("100"),
        seller = uid,
        contact = "09123456789",
        description = "",
        createdAt = epochMillis?.let { Timestamp(Date(it)) },
        availabilityStatus = status
    )
}
