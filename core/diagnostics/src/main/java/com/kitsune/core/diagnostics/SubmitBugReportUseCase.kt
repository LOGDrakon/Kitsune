package com.kitsune.core.diagnostics

import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.security.bugreport.BugReportCrypto
import javax.inject.Inject

/**
 * Redacts ([BuildBugReportUseCase]), encrypts ([BugReportCrypto]) and submits a bug report to the
 * backend automatically — no user action beyond triggering report generation, and no plaintext
 * report is ever shown or copyable on-device. The backend only ever receives ciphertext; see
 * [BugReportCrypto] for the hybrid RSA/AES scheme and rationale.
 */
class SubmitBugReportUseCase @Inject constructor(
    private val buildBugReportUseCase: BuildBugReportUseCase,
    private val bugReportCrypto: BugReportCrypto,
    private val backendClient: KitsuneBackendClient
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
    ): Result<Unit> {
        val report = buildBugReportUseCase(
            subject, description, chatId, jobId, detailed,
            focusMessageId, flaggedContentText, flaggedContentCategory, attachFullConversation
        )
        val encrypted = bugReportCrypto.encrypt(report)
        return backendClient.submitBugReport(encrypted.encryptedKey, encrypted.iv, encrypted.ciphertext)
    }
}
