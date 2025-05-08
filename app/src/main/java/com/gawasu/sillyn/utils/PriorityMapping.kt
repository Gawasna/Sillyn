package com.gawasu.sillyn.utils

import android.util.Log // Import Log
import com.gawasu.sillyn.domain.model.Task

// Helper object to map keywords to Task.Priority
object PriorityMapping {

    private val TAG = "PriorityMapping" // Add TAG

    private val highPriorityKeywords = listOf(
        "urgent", "very urgent", "ASAP", "immediately", "right away", "high priority",
        "gấp", "cần gấp", "khẩn cấp", "ưu tiên cao", "làm ngay"
    )

    private val mediumPriorityKeywords = listOf(
        "important", "needs attention", "should do soon", "medium priority",
        "quan trọng", "nên làm sớm", "cần chú ý"
    )

    private val lowPriorityKeywords = listOf(
        "low priority", "can wait", "not urgent", "not important", "someday", "later",
        "có thể chờ", "làm sau", "để sau", "ít quan trọng"
    )

    private val nonePriorityKeywords = listOf(
        "no rush", "not needed", "optional", "none", "ignore",
        "không gấp", "bỏ qua", "không cần", "tùy", "không ưu tiên"
    )

    /**
     * Analyzes text to find the highest priority level indicated by keywords.
     * Case-insensitive check.
     */
    fun detectPriority(text: String): Task.Priority {
        if (text.isBlank()) {
            Log.d(TAG, "Input text is blank, returning NONE priority.")
            return Task.Priority.NONE
        }

        val lowerCaseText = text.lowercase(java.util.Locale.getDefault())
        Log.d(TAG, "Detecting priority for text: \"$lowerCaseText\"")

        // Check for High Priority keywords first
        if (highPriorityKeywords.any { lowerCaseText.contains(it) }) {
            Log.d(TAG, "Detected HIGH priority keyword.")
            return Task.Priority.HIGH
        }

        // Check for Medium Priority keywords
        if (mediumPriorityKeywords.any { lowerCaseText.contains(it) }) {
            Log.d(TAG, "Detected MEDIUM priority keyword.")
            return Task.Priority.MEDIUM
        }

        // Check for Low Priority keywords
        if (lowPriorityKeywords.any { lowerCaseText.contains(it) }) {
            Log.d(TAG, "Detected LOW priority keyword.")
            return Task.Priority.LOW
        }

        // Check for None Priority keywords (optional, as NONE is default)
        if (nonePriorityKeywords.any { lowerCaseText.contains(it) }) {
            Log.d(TAG, "Detected NONE priority keyword.")
            return Task.Priority.NONE // Explicitly return NONE if keywords like "none" are found
        }

        // If no specific keywords found, return NONE
        Log.d(TAG, "No specific priority keywords detected, returning NONE.")
        return Task.Priority.NONE
    }
}