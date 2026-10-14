package com.example.data.model

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object MarketplacePrice {
    private val inputPattern = Regex("^\\d{1,9}(?:\\.\\d{1,2})?$")

    fun parseInput(value: String): BigDecimal? {
        val trimmed = value.trim()
        if (!inputPattern.matches(trimmed)) return null
        return trimmed.toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO && it <= MAX_PRICE }
    }

    /** Reads numeric prices and legacy formatted strings without preserving old mojibake. */
    fun parseStored(value: Any?): BigDecimal? {
        val raw = when (value) {
            is Number -> value.toString()
            is String -> value
            else -> return null
        }
        val normalized = raw.replace(",", "").filter { it.isDigit() || it == '.' || it == '-' }
        return normalized.toBigDecimalOrNull()
            ?.takeIf { it >= BigDecimal.ZERO && it <= MAX_PRICE }
    }

    fun format(value: BigDecimal?): String {
        if (value == null) return "Price unavailable"
        return NumberFormat.getCurrencyInstance(PHILIPPINES).apply {
            currency = Currency.getInstance("PHP")
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(value)
    }

    private val MAX_PRICE = BigDecimal("999999999.99")
    private val PHILIPPINES = Locale("en", "PH")
}
