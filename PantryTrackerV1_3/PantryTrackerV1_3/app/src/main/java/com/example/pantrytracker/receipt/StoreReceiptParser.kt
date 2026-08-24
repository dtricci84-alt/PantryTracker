package com.example.pantrytracker.receipt

import java.util.Locale

internal enum class ReceiptStore(val displayName: String) {
    REWE("REWE"),
    EDEKA("EDEKA"),
    LIDL("Lidl"),
    ALDI("Aldi"),
    ALBERT_HEIJN("Albert Heijn"),
    CARREFOUR("Carrefour"),
    WALMART("Walmart"),
    COSTCO("Costco"),
    UNKNOWN("Unknown store")
}

internal data class StoreParseResult(
    val store: ReceiptStore,
    val lines: List<StoreParsedLine>
)

internal data class StoreParsedLine(
    val name: String,
    val quantity: Double = 1.0
)

internal object StoreReceiptParser {
    fun parse(text: String): StoreParseResult {
        val store = detectStore(text)
        val parsed = text.lineSequence()
            .map { it.trim() }
            .mapNotNull { parseLine(store, it) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .take(80)
            .toList()

        return StoreParseResult(store, parsed)
    }

    private fun detectStore(text: String): ReceiptStore {
        val value = text.lowercase(Locale.ROOT)
        return when {
            Regex("\\brewe\\b").containsMatchIn(value) -> ReceiptStore.REWE
            Regex("\\bedeka\\b").containsMatchIn(value) -> ReceiptStore.EDEKA
            Regex("\\blidl\\b").containsMatchIn(value) -> ReceiptStore.LIDL
            Regex("\\baldi(?:\\s+s[uü]d|\\s+nord)?\\b").containsMatchIn(value) -> ReceiptStore.ALDI
            "albert heijn" in value || Regex("\\bah\\b").containsMatchIn(value) && "bonus" in value -> ReceiptStore.ALBERT_HEIJN
            Regex("\\bcarrefour\\b").containsMatchIn(value) -> ReceiptStore.CARREFOUR
            Regex("\\bwalmart\\b").containsMatchIn(value) -> ReceiptStore.WALMART
            Regex("\\bcostco\\b").containsMatchIn(value) -> ReceiptStore.COSTCO
            else -> ReceiptStore.UNKNOWN
        }
    }

    private fun parseLine(store: ReceiptStore, line: String): StoreParsedLine? {
        if (line.length < 2 || !line.any(Char::isLetter)) return null
        val lower = line.lowercase(Locale.ROOT)

        if (isUniversalNonProduct(lower, line)) return null
        if (isStoreSpecificNonProduct(store, lower)) return null

        var quantity = 1.0
        var name = line

        val leadingQty = Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*[xX*]\\s*(.+)$").find(name)
        if (leadingQty != null) {
            quantity = leadingQty.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 1.0
            name = leadingQty.groupValues[2]
        }

        val trailingQty = Regex("^(.+?)\\s+(\\d+(?:[.,]\\d+)?)\\s*[xX]\\s*$").find(name)
        if (trailingQty != null) {
            name = trailingQty.groupValues[1]
            quantity = trailingQty.groupValues[2].replace(',', '.').toDoubleOrNull() ?: quantity
        }

        name = stripPriceAndMarkers(store, name)
            .replace(Regex("^[-*#>]+\\s*"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', '-', ':', '.', '*')

        if (name.length < 2 || name.count(Char::isLetter) < 2) return null
        if (looksLikeSkuOnly(name)) return null

        return StoreParsedLine(name, quantity.coerceAtLeast(1.0))
    }

    private fun isUniversalNonProduct(lower: String, original: String): Boolean {
        val stopWords = listOf(
            "total", "subtotal", "summe", "gesamt", "zwischensumme", "totaal", "subtotaal",
            "tax", "vat", "mwst", "steuer", "btw", "tva", "change", "cash", "card", "visa",
            "mastercard", "zahlung", "betaling", "paiement", "receipt", "kassenbon", "beleg", "ticket",
            "datum", "date", "tijd", "time", "heure", "tel", "phone", "www", "thank", "danke",
            "bedankt", "merci", "customer", "kunde", "klant", "client", "filiale", "store", "branch"
        )
        if (stopWords.any { it in lower }) return true
        if (Regex("\\b\\d{1,2}[:.]\\d{2}\\b").containsMatchIn(original)) return true
        if (Regex("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b").containsMatchIn(original)) return true
        if (Regex("^[0-9 .,:/-]+$").matches(original)) return true
        return false
    }

    private fun isStoreSpecificNonProduct(store: ReceiptStore, lower: String): Boolean {
        val keywords = when (store) {
            ReceiptStore.REWE -> listOf("payback", "treuepunkt", "pfand", "leergut", "rabatt", "coupon", "ersparnis")
            ReceiptStore.EDEKA -> listOf("deutschlandcard", "pfand", "leergut", "rabatt", "coupon", "ersparnis")
            ReceiptStore.LIDL -> listOf("lidl plus", "pfand", "leergut", "coupon", "rabatt", "ersparnis", "preisvorteil")
            ReceiptStore.ALDI -> listOf("pfand", "leergut", "rabatt", "coupon", "ersparnis")
            ReceiptStore.ALBERT_HEIJN -> listOf("bonus", "bonuskaart", "statiegeld", "korting", "koopzegels", "emballage")
            ReceiptStore.CARREFOUR -> listOf("remise", "fidelite", "fidélité", "coupon", "consigne", "promotion")
            ReceiptStore.WALMART -> listOf("savings", "coupon", "rollback", "tax", "cash back", "change due")
            ReceiptStore.COSTCO -> listOf("membership", "instant savings", "coupon", "tax", "subtotal", "renewal")
            ReceiptStore.UNKNOWN -> listOf("discount", "rabatt", "korting", "remise", "coupon", "pfand", "statiegeld", "consigne")
        }
        return keywords.any { it in lower }
    }

    private fun stripPriceAndMarkers(store: ReceiptStore, value: String): String {
        var result = value
        result = result.replace(Regex("\\s+[€$£]?\\s*[-+]?\\d+[.,]\\d{2}\\s*[A-Za-z]?\\s*$"), "")
        result = result.replace(Regex("\\s+[-+]?\\d+[.,]\\d{2}\\s*[€$£]\\s*$"), "")
        result = result.replace(Regex("\\s+[A-Z]\\s*$"), "")

        if (store == ReceiptStore.WALMART || store == ReceiptStore.COSTCO) {
            result = result.replace(Regex("^\\s*\\d{5,14}\\s+"), "")
        }
        if (store == ReceiptStore.ALBERT_HEIJN) {
            result = result.replace(Regex("^\\s*[A-Z]?\\s*\\d{5,14}\\s+"), "")
        }
        return result
    }

    private fun looksLikeSkuOnly(value: String): Boolean {
        val compact = value.replace(" ", "")
        return compact.length >= 5 && compact.all { it.isDigit() || it == '-' }
    }
}
