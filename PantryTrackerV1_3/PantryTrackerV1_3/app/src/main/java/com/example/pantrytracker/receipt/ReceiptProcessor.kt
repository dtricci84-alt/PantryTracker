package com.example.pantrytracker.receipt

import android.graphics.Bitmap
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions


data class ReceiptCandidate(
    val originalName: String,
    val translatedName: String,
    val quantity: Double = 1.0
)

data class ReceiptScanResult(
    val sourceLanguage: String,
    val candidates: List<ReceiptCandidate>
)

class ReceiptProcessor {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languageIdentifier = LanguageIdentification.getClient()

    fun process(bitmap: Bitmap, onResult: (Result<ReceiptScanResult>) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { recognized ->
                val rawText = recognized.text.trim()
                if (rawText.isBlank()) {
                    onResult(Result.failure(IllegalStateException("No readable receipt text was found.")))
                    return@addOnSuccessListener
                }

                val parsed = parseReceiptLines(rawText)
                if (parsed.isEmpty()) {
                    onResult(Result.failure(IllegalStateException("No likely grocery items were found on the receipt.")))
                    return@addOnSuccessListener
                }

                languageIdentifier.identifyLanguage(rawText)
                    .addOnSuccessListener { languageCode ->
                        val normalized = normalizeSupportedLanguage(languageCode)
                        if (normalized == null || normalized == "en") {
                            onResult(
                                Result.success(
                                    ReceiptScanResult(
                                        sourceLanguage = normalized ?: "unknown",
                                        candidates = parsed.map {
                                            ReceiptCandidate(it.name, it.name, it.quantity)
                                        }
                                    )
                                )
                            )
                        } else {
                            translateCandidates(normalized, parsed, onResult)
                        }
                    }
                    .addOnFailureListener {
                        onResult(
                            Result.success(
                                ReceiptScanResult(
                                    sourceLanguage = "unknown",
                                    candidates = parsed.map { ReceiptCandidate(it.name, it.name, it.quantity) }
                                )
                            )
                        )
                    }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun translateCandidates(
        sourceLanguage: String,
        parsed: List<ParsedLine>,
        onResult: (Result<ReceiptScanResult>) -> Unit
    ) {
        val source = when (sourceLanguage) {
            "de" -> TranslateLanguage.GERMAN
            "nl" -> TranslateLanguage.DUTCH
            "fr" -> TranslateLanguage.FRENCH
            else -> null
        }

        if (source == null) {
            onResult(
                Result.success(
                    ReceiptScanResult(
                        sourceLanguage = sourceLanguage,
                        candidates = parsed.map { ReceiptCandidate(it.name, it.name, it.quantity) }
                    )
                )
            )
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                val output = mutableListOf<ReceiptCandidate>()
                fun translateAt(index: Int) {
                    if (index >= parsed.size) {
                        translator.close()
                        onResult(Result.success(ReceiptScanResult(sourceLanguage, output)))
                        return
                    }
                    val item = parsed[index]
                    translator.translate(item.name)
                        .addOnSuccessListener { english ->
                            output += ReceiptCandidate(
                                originalName = item.name,
                                translatedName = english.trim().ifBlank { item.name },
                                quantity = item.quantity
                            )
                            translateAt(index + 1)
                        }
                        .addOnFailureListener {
                            output += ReceiptCandidate(item.name, item.name, item.quantity)
                            translateAt(index + 1)
                        }
                }
                translateAt(0)
            }
            .addOnFailureListener {
                translator.close()
                onResult(Result.failure(IllegalStateException("The translation model could not be downloaded. Check your internet connection and try again.")))
            }
    }

    private fun normalizeSupportedLanguage(code: String?): String? {
        return when (code?.lowercase()) {
            "en" -> "en"
            "de" -> "de"
            "nl" -> "nl"
            "fr" -> "fr"
            else -> null
        }
    }

    private data class ParsedLine(val name: String, val quantity: Double)

    private fun parseReceiptLines(text: String): List<ParsedLine> {
        return text.lineSequence()
            .map { it.trim() }
            .mapNotNull(::parseLikelyProductLine)
            .distinctBy { it.name.lowercase() }
            .take(60)
            .toList()
    }

    private fun parseLikelyProductLine(line: String): ParsedLine? {
        if (line.length < 2) return null
        if (!line.any(Char::isLetter)) return null

        val lower = line.lowercase()
        val stopWords = listOf(
            "total", "subtotal", "summe", "gesamt", "zwischensumme", "totaal", "subtotaal",
            "tax", "vat", "mwst", "steuer", "btw", "tva", "change", "cash", "card", "visa",
            "mastercard", "payment", "zahlung", "betaling", "paiement", "discount", "rabatt",
            "korting", "remise", "coupon", "bon", "receipt", "kassenbon", "beleg", "ticket",
            "datum", "date", "tijd", "time", "heure", "tel", "phone", "www", "thank", "danke",
            "bedankt", "merci"
        )
        if (stopWords.any { it in lower }) return null

        if (Regex("\\b\\d{1,2}[:.]\\d{2}\\b").containsMatchIn(line)) return null
        if (Regex("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b").containsMatchIn(line)) return null

        var quantity = 1.0
        var name = line

        val qtyMatch = Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*[xX*]\\s+(.+)$").find(name)
        if (qtyMatch != null) {
            quantity = qtyMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 1.0
            name = qtyMatch.groupValues[2]
        }

        name = name
            .replace(Regex("\\s+[€$£]?\\s*\\d+[.,]\\d{2}\\s*[A-Za-z]?\\s*$"), "")
            .replace(Regex("\\s+\\d+[.,]\\d{2}\\s*[€$£]\\s*$"), "")
            .replace(Regex("^[-*#]+\\s*"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', '-', ':', '.')

        if (name.length < 2) return null
        if (!name.any(Char::isLetter)) return null
        if (name.count(Char::isLetter) < 2) return null

        return ParsedLine(name = name, quantity = quantity.coerceAtLeast(1.0))
    }
}
