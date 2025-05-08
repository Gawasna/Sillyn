package com.gawasu.sillyn.utils

import android.content.Context
import android.util.Log
import com.gawasu.sillyn.domain.model.Task
import com.google.android.gms.tasks.Tasks // Keep Tasks for isModelDownloaded check
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.DateTimeEntity // Still need DateTimeEntity for casting and granularity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
// import java.util.concurrent.TimeUnit // Not needed
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Data class to hold parsing results
data class ParsedEntities(
    val category: String? = null,
    val priority: Task.Priority = Task.Priority.NONE,
    val dueDate: Date? = null,
    val dueTime: Calendar? = null // Use Calendar to hold time component separately
) {
    override fun toString(): String { // Add toString for easy logging
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dueDateStr = dueDate?.let { dateFormat.format(it) } ?: "null"
        val dueTimeStr = dueTime?.let { dateFormat.format(it.time) } ?: "null" // Log time using its Date representation
        return "ParsedEntities(category=$category, priority=$priority, dueDate=$dueDateStr, dueTime=$dueTimeStr)"
    }
}

class ParseHelper(private val context: Context) {

    private val TAG = "ParseHelper"
    private var entityExtractor: EntityExtractor? = null
    // Keep these public for dialog to check model status. Initialize to false.
    var isModelReady: Boolean = false
    private var isModelDownloading = false // Track if download is in progress

    // Regex for category: ~Category_Name (captures text after ~ until next ~ or end)
    private val categoryRegex = "~([^~]+)".toRegex()

    init {
        initializeExtractor()
    }

    // Asynchronous initialization of ML Kit Entity Extractor
    private fun initializeExtractor() {
        Log.d(TAG, "Initializing EntityExtractor...")
        // Prefer English as requested, ML Kit doesn't support Vietnamese well for entities
        // Use ModelIdentifier constant from EntityExtractorOptions
        val options = EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        entityExtractor = EntityExtraction.getClient(options)

        // Check if model is already downloaded first (non-blocking check)
        entityExtractor?.isModelDownloaded()
            ?.addOnSuccessListener { isDownloaded ->
                if (isDownloaded) {
                    isModelReady = true
                    Log.d(TAG, "ML Kit Entity Extraction model is already downloaded and ready.")
                } else {
                    // Download model if needed (run asynchronously)
                    Log.d(TAG, "ML Kit model not downloaded. Attempting to download...")
                    isModelDownloading = true
                    entityExtractor?.downloadModelIfNeeded()
                        ?.addOnSuccessListener {
                            isModelReady = true
                            isModelDownloading = false
                            Log.d(TAG, "ML Kit Entity Extraction model downloaded/ready successfully.")
                        }
                        ?.addOnFailureListener { e ->
                            isModelReady = false
                            isModelDownloading = false
                            Log.e(TAG, "ML Kit Entity Extraction model download failed: ${e.message}", e)
                            // Handle failure, maybe retry or notify user
                        }
                        ?.addOnCanceledListener {
                            isModelReady = false
                            isModelDownloading = false
                            Log.d(TAG, "ML Kit Entity Extraction model download cancelled.")
                        }
                }
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Failed to check if ML Kit model is downloaded: ${e.message}", e)
                // Model status is uncertain, cannot proceed with extraction until re-initialized or user action
            }
    }


    // Suspend function to parse text
    // Will wait for model to be ready if needed (or skip ML Kit part if not ready)
    suspend fun parseText(title: String, description: String): ParsedEntities = withContext(Dispatchers.Default) {
        Log.d(TAG, "Parsing text: Title=\"$title\", Description=\"$description\"")

        // Combine title and description
        val fullText = if (title.isBlank() && description.isBlank()) {
            ""
        } else if (title.isBlank()) {
            description
        } else if (description.isBlank()) {
            title
        } else {
            "$title $description" // Combine with space
        }

        if (fullText.isBlank()) {
            Log.d(TAG, "Full text is blank, returning empty ParsedEntities.")
            return@withContext ParsedEntities() // Return empty result
        }

        // --- 1. Parse Category using Regex ---
        var parsedCategory: String? = null
        // Find all matches and take the last one
        val categoryMatches = categoryRegex.findAll(fullText).toList()
        Log.d(TAG, "Category regex found ${categoryMatches.size} matches.")
        if (categoryMatches.isNotEmpty()) {
            val lastMatch = categoryMatches.last()
            // Capture group 1 contains the text after '~'
            val rawCategoryName = lastMatch.groupValues[1].trim()
            Log.d(TAG, "Last category match: \"${lastMatch.value}\", Raw name: \"$rawCategoryName\"")
            if (rawCategoryName.isNotBlank()) {
                // Replace underscores with spaces for the final category name
                parsedCategory = rawCategoryName.replace("_", " ")
                Log.d(TAG, "Parsed Category: \"$parsedCategory\"")
            } else {
                Log.d(TAG, "Raw category name is blank, skipping.")
            }
        } else {
            Log.d(TAG, "No category regex matches found.")
        }


        // --- 2. Parse Priority using Mapping Dictionary ---
        val parsedPriority = PriorityMapping.detectPriority(fullText)
        Log.d(TAG, "Parsed Priority: ${parsedPriority.name}")


        // --- 3. Parse Date/Time using ML Kit ---
        var parsedDueDate: Date? = null
        var parsedDueTime: Calendar? = null // Use Calendar to store time info

        if (isModelReady) {
            Log.d(TAG, "ML Kit model is ready, performing Date/Time extraction.")
            try {
                // Call the updated extraction function which returns List<EntityAnnotation>
                val entityAnnotations = extractEntitiesMLKit(fullText)
                Log.d(TAG, "ML Kit extraction returned ${entityAnnotations.size} annotations.")

                // Find the latest EntityAnnotation that contains a DATE_TIME entity
                val dateTimeAnnotations = entityAnnotations
                    .filter { annotation ->
                        annotation.entities.any { it.type == Entity.TYPE_DATE_TIME }
                    }
                Log.d(TAG, "Found ${dateTimeAnnotations.size} annotations containing DATE_TIME entities.")

                val latestDateTimeAnnotation = dateTimeAnnotations
                    .maxByOrNull { it.end } // Find the one ending latest in the text

                latestDateTimeAnnotation?.let { annotation ->
                    Log.d(TAG, "Found latest DATE_TIME annotation at [${annotation.start}, ${annotation.end}): \"${annotation.annotatedText}\"")
                    // From the latest annotation, find the actual DateTimeEntity
                    val dateTimeEntity = annotation.entities
                        .firstOrNull { it.type == Entity.TYPE_DATE_TIME }
                        ?.asDateTimeEntity() // Safe cast

                    dateTimeEntity?.let { dtEntity ->
                        val timestampMillis = dtEntity.timestampMillis
                        val granularity = dtEntity.dateTimeGranularity

                        Log.d(TAG, "DateTimeEntity found: timestamp=$timestampMillis, granularity=$granularity")

                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = timestampMillis // Set calendar using the timestamp
                        }

                        parsedDueDate = calendar.time // Always set the date part based on timestamp

                        // Check granularity to determine if time is included
                        // If granularity is HOUR or finer, include the time component
                        // Granularities: YEAR=0, MONTH=1, WEEK=2, DAY=3, HOUR=4, MINUTE=5, SECOND=6
                        if (granularity >= DateTimeEntity.GRANULARITY_HOUR) {
                            // Calendar already has the time from timeInMillis.
                            parsedDueTime = calendar
                            Log.d(TAG, "Parsed DateTime based on granularity >= HOUR: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(parsedDueDate!!)}")
                        } else {
                            // Granularity is Day or coarser, treat as date only (time is midnight)
                            // Ensure the time part of parsedDueDate is midnight
                            val dateOnlyCal = Calendar.getInstance().apply { time = parsedDueDate!! }
                            dateOnlyCal.set(Calendar.HOUR_OF_DAY, 0)
                            dateOnlyCal.set(Calendar.MINUTE, 0)
                            dateOnlyCal.set(Calendar.SECOND, 0)
                            dateOnlyCal.set(Calendar.MILLISECOND, 0)
                            parsedDueDate = dateOnlyCal.time // Update dueDate to be date-only at midnight
                            parsedDueTime = null // Explicitly set time to null
                            Log.d(TAG, "Parsed Date only based on granularity < HOUR: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(parsedDueDate!!)}")
                        }
                    } ?: run {
                        Log.w(TAG, "Latest DATE_TIME annotation found but asDateTimeEntity() returned null.")
                    }
                } ?: run  {
                    Log.d(TAG, "No DATE_TIME annotation found in the text.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit entity extraction failed unexpectedly: ${e.message}", e)
                // Parsing failed, keep parsedDueDate/Time as null (they were null by default)
            }
        } else {
            if (isModelDownloading) {
                Log.w(TAG, "ML Kit model is still downloading, skipping Date/Time extraction.")
            } else {
                Log.w(TAG, "ML Kit model is not ready and not downloading, skipping Date/Time extraction.")
            }
        }


        val result = ParsedEntities(
            category = parsedCategory,
            priority = parsedPriority,
            dueDate = parsedDueDate,
            dueTime = parsedDueTime
        )
        Log.d(TAG, "parseText finished, returning: $result")
        return@withContext result
    }

    // Helper suspend function to wrap ML Kit async task
    // Updated return type to List<EntityAnnotation> and use annotate(params)
    private suspend fun extractEntitiesMLKit(text: String): List<EntityAnnotation> = suspendCoroutine { continuation ->
        Log.d(TAG, "Calling ML Kit annotate for text: \"$text\"")
        val params = EntityExtractionParams.Builder(text).build() // Use builder for params
        entityExtractor?.annotate(params) // Use annotate with params
            ?.addOnSuccessListener { annotations ->
                Log.d(TAG, "ML Kit annotate success. Received ${annotations.size} annotations.")
                continuation.resume(annotations)
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "ML Kit annotate failed: ${e.message}", e)
                continuation.resume(emptyList()) // Return empty list on failure
            } ?: run {
            Log.e(TAG, "ML Kit annotate called but extractor is null.")
            continuation.resume(emptyList()) // Return empty list if extractor is null
        }
    }

    // Check if the model is currently downloading
    fun isModelDownloadInProgress(): Boolean {
        // This flag is updated by the download listener.
        return isModelDownloading
    }

    // Check if the model is downloaded and ready for use
    fun isModelDownloaded(): Boolean {
        // This is a non-blocking check based on the flag updated by the listener.
        return isModelReady
    }


    // Don't forget to release the extractor when done (e.g., in ViewModel's onCleared or Fragment's onDestroy)
    fun releaseExtractor() {
        Log.d(TAG, "Releasing EntityExtractor resources.")
        entityExtractor?.close()
        entityExtractor = null
        isModelReady = false // Reset ready state
        isModelDownloading = false // Reset download state
    }
}