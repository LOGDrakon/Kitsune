package com.kitsune.core.diagnostics

/**
 * Replaces the user's own configured first/last name (when set) with neutral placeholder tokens,
 * wherever they occur in free text. Single source of truth for this redaction so it's applied
 * identically whether it's [BuildBugReportUseCase] actually building a report, or a screen
 * previewing to the user beforehand exactly what a flagged excerpt will look like once redacted —
 * a "what you see is what gets sent" guarantee that a second, independently maintained copy of the
 * same two `replace` calls could silently drift away from.
 */
object PersonalNameRedactor {
    const val FIRST_NAME_TOKEN = "Name User"
    const val LAST_NAME_TOKEN = "Last Name User"

    fun redact(text: String, firstName: String, lastName: String): String {
        var body = text
        if (firstName.isNotBlank()) body = body.replace(firstName, FIRST_NAME_TOKEN, ignoreCase = true)
        if (lastName.isNotBlank()) body = body.replace(lastName, LAST_NAME_TOKEN, ignoreCase = true)
        return body
    }
}
