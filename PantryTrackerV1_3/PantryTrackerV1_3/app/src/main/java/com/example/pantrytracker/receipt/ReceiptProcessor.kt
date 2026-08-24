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
    val storeName: String,
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

                val storeParse = StoreReceiptParser.parse(rawText)
                if (storeParse.lines.isEmpty()) {
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
                                        storeName = storeParse.store.displayName,
                                        candidates = storeParse.lines.map {
                                            ReceiptCandidate(it.name, it.name, it.quantity)
                                        }
                                    )
                                )
                            )
                        } else {
                            translateCandidates(
                                sourceLanguage = normalized,
                                storeName = storeParse.store.displayName,
                                parsed = storeParse.lines,
                                onResult = onResult
                            )
                        }
                    }
                    .addOnFailureListener {
                        onResult(
                            Result.success(
                                ReceiptScanResult(
                                    sourceLanguage = "unknown",
                                    storeName = storeParse.store.displayName,
                                    candidates = storeParse.lines.map {
                                        ReceiptCandidate(it.name, it.name, it.quantity)
                                    }
                                )
                            )
                        )
                    }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun translateCandidates(
        sourceLanguage: String,
        storeName: String,
        parsed: List<StoreParsedLine>,
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
                        storeName = storeName,
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
                        onResult(Result.success(ReceiptScanResult(sourceLanguage, storeName, output)))
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
                onResult(
                    Result.failure(
                        IllegalStateException(
                            "The translation model could not be downloaded. Check your internet connection and try again."
                        )
                    )
                )
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
}
