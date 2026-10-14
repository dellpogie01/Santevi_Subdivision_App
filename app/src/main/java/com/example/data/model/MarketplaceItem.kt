package com.example.data.model

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.google.firebase.Timestamp

data class MarketplaceItem(
    val id: String,
    val sellerUid: String,
    val title: String,
    val price: BigDecimal?,
    val seller: String,
    val contact: String,
    val blockLotPhase: String = "",
    val description: String,
    val imageUrls: List<String> = emptyList(),
    val imagePaths: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val availabilityStatus: String? = null
) {
    val displayAvailability: String
        get() = availabilityStatus ?: AVAILABLE

    companion object {
        const val AVAILABLE = "AVAILABLE"
        const val SOLD = "SOLD"
    }
}

object MarketplaceContactNumber {
    private val localDigitsPattern = Regex("^\\d{10}$")
    private val storedNumberPattern = Regex("^\\+63 \\d{10}$")

    fun isValidLocalDigits(value: String): Boolean = localDigitsPattern.matches(value)

    fun isValidStoredNumber(value: String): Boolean = storedNumberPattern.matches(value)

    fun formatLocalDigits(value: String): String {
        require(isValidLocalDigits(value)) { "Enter a 10-digit Philippine mobile number." }
        return "+63 $value"
    }
}

data class MarketplaceListingSections(
    val mine: List<MarketplaceItem>,
    val others: List<MarketplaceItem>
)

object MarketplaceListingOrdering {
    fun partition(items: List<MarketplaceItem>, authenticatedUid: String): MarketplaceListingSections {
        val newestFirst = compareByDescending<MarketplaceItem> { it.createdAt?.toDate()?.time ?: Long.MIN_VALUE }
            .thenBy { it.id }
        return MarketplaceListingSections(
            mine = items.filter { it.sellerUid == authenticatedUid }.sortedWith(newestFirst),
            others = items.filter { it.sellerUid != authenticatedUid }.sortedWith(newestFirst)
        )
    }
}

object MarketplaceTextLimits {
    const val TITLE = 75
    const val DESCRIPTION = 1000
    const val BLOCK_LOCATION = 120

    fun characterCount(value: String): Int = value.codePointCount(0, value.length)

    fun truncate(value: String, maxCharacters: Int): String {
        val count = characterCount(value)
        return if (count <= maxCharacters) value
        else value.substring(0, value.offsetByCodePoints(0, maxCharacters))
    }

    fun validateTitle(title: String): String {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Enter an item title." }
        require(characterCount(cleanTitle) <= TITLE) { "Item title must be $TITLE characters or fewer." }
        return cleanTitle
    }

    fun validateDescription(description: String): String {
        val cleanDescription = description.trim()
        require(cleanDescription.isNotEmpty()) { "Enter an item description." }
        require(characterCount(cleanDescription) <= DESCRIPTION) {
            "Item description must be $DESCRIPTION characters or fewer."
        }
        return cleanDescription
    }

    fun validate(title: String, description: String): Pair<String, String> {
        return validateTitle(title) to validateDescription(description)
    }
}

object MarketplaceDateFormatter {
    fun format(
        timestamp: Timestamp?,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        if (timestamp == null) return "Date unavailable"
        return runCatching {
            SimpleDateFormat("MMM d, yyyy · h:mm a", locale).apply { this.timeZone = timeZone }
                .format(timestamp.toDate())
        }.getOrDefault("Date unavailable")
    }
}
