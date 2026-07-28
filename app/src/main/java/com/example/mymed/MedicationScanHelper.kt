package com.example.mymed

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Ergebnis des ML Kit Scans
 * Alle Felder sind editierbar im Review-Dialog
 */
data class ScanResult(
    val name: String = "",
    val dosage: String = "",
    val notes: String = "",
    val rawText: String = ""   // Vollständiger erkannter Text (für Debug)
)

/**
 * Verarbeitet ein Foto mit ML Kit Text Recognition
 * und extrahiert Name, Dosis und Notizen
 */
object MedicationScanHelper {

    // Dosierungs-Pattern: Zahlen gefolgt von mg/ml/g/mcg/IE oder "Tablette/Kapsel"
    private val DOSAGE_REGEX = Regex(
        """(\d+[\.,]?\d*)\s*(mg|ml|g|mcg|µg|IE|mmol|Tablette[n]?|Kapsel[n]?|Tropfen|Stück)""",
        RegexOption.IGNORE_CASE
    )

    // Hinweis-Wörter die auf Anwendungsnotizen hindeuten
    private val NOTES_KEYWORDS = listOf(
        "täglich", "morgens", "abends", "mit wasser", "nach dem essen",
        "vor dem essen", "nüchtern", "einnahme", "anwendung", "einmal",
        "zweimal", "dreimal", "tablette", "kapsel", "lösung"
    )

    // Wörter die sicher KEIN Medikamentenname sind (Verpackungstext)
    private val IGNORE_WORDS = listOf(
        "charge", "lot", "exp", "haltbar", "apotheke", "packung",
        "bitte", "lesen", "beipackzettel", "hinweis", "achtung",
        "aufbewahrung", "lagerung", "kinder", "reichweite", "kühl",
        "ph", "gmbh", "ag", "kg", "www", "http", "tel", "fax"
    )

    /**
     * Hauptfunktion: Foto → ScanResult
     * Callback wegen ML Kit async API
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
     * Parst den erkannten Text und versucht Name, Dosis und Notizen zu extrahieren
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

            // Ignoriere Zeilen mit bekannten Nicht-Name-Wörtern
            if (IGNORE_WORDS.any { lineLower.contains(it) }) continue

            // Dosierung suchen
            val dosageMatch = DOSAGE_REGEX.find(line)
            if (dosageMatch != null && dosage.isBlank()) {
                // Ganze Zeile als Dosis-Kandidat
                dosage = line.trim()
                // Name ist oft die Zeile VOR der Dosierung (falls noch leer)
                if (name.isBlank()) {
                    val idx = lines.indexOf(line)
                    if (idx > 0) name = lines[idx - 1].trim()
                }
                continue
            }

            // Anwendungshinweise suchen
            if (NOTES_KEYWORDS.any { lineLower.contains(it) }) {
                notesLines.add(line.trim())
                continue
            }

            // Erste sinnvolle Zeile als Name-Kandidat
            if (name.isBlank() && line.length in 3..50 && !line.all { it.isDigit() || it == '.' }) {
                name = line.trim()
            }
        }

        // Fallback: Erste Zeile als Name wenn noch leer
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

