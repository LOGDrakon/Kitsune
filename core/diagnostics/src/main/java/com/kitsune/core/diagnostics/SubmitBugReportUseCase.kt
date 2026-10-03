package com.kitsune.core.diagnostics

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds a redacted bug report ([BuildBugReportUseCase]) and hands it to the system share sheet, so
 * the user decides where it goes — a GitHub issue, an email, a note — and can read every word of it
 * first.
 *
 * The hosted version encrypted the report and uploaded it to its backend automatically. There is no
 * backend to receive it any more, and an open-source project's bug tracker is public: a report that
 * goes anywhere should go there knowingly, which is what the share sheet gives.
 */
class SubmitBugReportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buildBugReportUseCase: BuildBugReportUseCase
) {
    suspend operator fun invoke(
        subject: String,
        description: String,
        chatId: String? = null,
        jobId: String? = null,
        detailed: Boolean = false,
        focusMessageId: String? = null,
        flaggedContentText: String? = null,
        flaggedContentCategory: String? = null,
        attachFullConversation: Boolean = true
    ): Result<Unit> = runCatching {
        val report = buildBugReportUseCase(
            subject, description, chatId, jobId, detailed,
            focusMessageId, flaggedContentText, flaggedContentCategory, attachFullConversation
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "[Kitsune] $subject")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(
            Intent.createChooser(send, "Envoyer le rapport de bug").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        /** Where bug reports and feature requests belong. */
        const val ISSUES_URL = "https://github.com/LOGDrakon/Kitsune/issues"
    }
}
