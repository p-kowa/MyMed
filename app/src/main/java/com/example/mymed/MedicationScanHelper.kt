package com.example.mymed

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Result of an ML Kit scan.
 * All fields are editable in the review dialog.
 */
data class ScanResult(
    val name: String = "",
    val dosage: String = "",
    val notes: String = "",
    val rawText: String = ""   // Full recognized text (for debugging)
)

/**
 * Processes an image with ML Kit Text Recognition
 * and extracts name, dosage, and notes.
 */
object MedicationScanHelper {

    // Dosage pattern: number followed by mg/ml/g/mcg/IU or tablet/capsule tokens
    private val DOSAGE_REGEX = Regex(
        """(\d+[\.,]?\d*)\s*(mg|ml|g|mcg|µg|IE|mmol|Tablette[n]?|Kapsel[n]?|Tropfen|Stück)""",
        RegexOption.IGNORE_CASE
    )

    // Keywords that indicate usage notes
    private val NOTES_KEYWORDS = listOf(
        "täglich", "morgens", "abends", "mit wasser", "nach dem essen",
        "vor dem essen", "nüchtern", "einnahme", "anwendung", "einmal",
        "zweimal", "dreimal", "tablette", "kapsel", "lösung"
    )

    // Words that are definitely NOT medication names (package text)
    private val IGNORE_WORDS = listOf(
        "charge", "lot", "exp", "haltbar", "apotheke", "packung",
        "bitte", "lesen", "beipackzettel", "hinweis", "achtung",
        "aufbewahrung", "lagerung", "kinder", "reichweite", "kühl",
        "ph", "gmbh", "ag", "kg", "www", "http", "tel", "fax"
    )

    /**
     * Main function: image -> ScanResult
     * Callback style due to ML Kit async API.
     */
    fun scanImage(
        context: Context,
        imageUri: Uri,
        onSuccess: (ScanResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = parseText(visionText.text)
                    onSuccess(result)
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    onError(e)
                    recognizer.close()
                }
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Parses recognized text and tries to extract name, dosage, and notes.
     */
    private fun parseText(rawText: String): ScanResult {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }

        var name = ""
        var dosage = ""
        val notesLines = mutableListOf<String>()

        for (line in lines) {
            val lineLower = line.lowercase()

            // Ignore lines with known non-name words
            if (IGNORE_WORDS.any { lineLower.contains(it) }) continue

            // Find dosage
            val dosageMatch = DOSAGE_REGEX.find(line)
            if (dosageMatch != null && dosage.isBlank()) {
                // Entire line as dosage candidate
                dosage = line.trim()
                // Name is often the line BEFORE dosage (if still empty)
                if (name.isBlank()) {
                    val idx = lines.indexOf(line)
                    if (idx > 0) name = lines[idx - 1].trim()
                }
                continue
            }

            // Search for usage hints
            if (NOTES_KEYWORDS.any { lineLower.contains(it) }) {
                notesLines.add(line.trim())
                continue
            }

            // First plausible line as name candidate
            if (name.isBlank() && line.length in 3..50 && !line.all { it.isDigit() || it == '.' }) {
                name = line.trim()
            }
        }

        // Fallback: first line as name if still empty
        if (name.isBlank() && lines.isNotEmpty()) {
            name = lines.first().take(50)
        }

        return ScanResult(
            name = name,
            dosage = dosage,
            notes = notesLines.take(3).joinToString(", "),
            rawText = rawText
        )
    }
}

