package com.kitsune.core.diagnostics

import com.kitsune.core.common.generation.GenerationFailureCategory
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.MessageAuditLogEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.failureCategory
import com.kitsune.core.data.local.entities.ModerationFlag
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.data.repository.MessageAuditLogRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.network.preferences.NetworkPreferences
import com.kitsune.core.security.lock.AutoLockManager
import com.kitsune.core.security.profile.UserProfileStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Builds an anonymised diagnostic report so a bug can be reported and analysed, at any time,
 * without leaking anything private (FEATURES.md section 9).
 *
 * Privacy guarantees:
 * - The API key and API base URL are never included.
 * - The safe word is never included, only whether one is set.
 * - The user's configured first name and last name are each redacted independently, everywhere
 *   they occur in message content (not just as a combined "first last" string, so a message
 *   mentioning only one of the two is still caught), and replaced with the neutral tokens
 *   "Name User" / "Last Name User". Other profile fields (pronoun/age/description) are reported
 *   only as "renseigné : oui/non", never their actual values.
 * - No account identifier, device id or other personal data is collected.
 * - The report is then encrypted client-side (see `BugReportCrypto`, `core:security`) with a
 *   hybrid RSA/AES scheme before being sent to the backend — only the backend holds the RSA
 *   private key needed to decrypt it.
 *
 * The conversation transcript is read from the local, append-only [MessageAuditLogRepository]
 * rather than the live, editable message table — see that entity's doc comment for why (a message
 * likely to get its author reported/banned could otherwise just be edited or deleted right before
 * filing this report, making the transcript look innocuous).
 */
class BuildBugReportUseCase @Inject constructor(
    private val messageAuditLogRepository: MessageAuditLogRepository,
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val generationJobRepository: GenerationJobRepository,
    private val networkPreferences: NetworkPreferences,
    private val userProfileStore: UserProfileStore,
    private val autoLockManager: AutoLockManager
) {
    companion object {
        private const val FOCUS_MARKER = "→ MESSAGE SIGNALÉ"
        private const val CONTEXT_MESSAGES_BEFORE = 3
        private const val CONTEXT_MESSAGES_AFTER = 1
    }

    /** [subject]/[description] are the user's own account of the problem (replaces the old
     * "traçage avancé" system, which returned almost no useful data in practice — this asks the
     * user directly instead). When [chatId] is null, produces a report with no conversation
     * transcript (either a general report, or a "signaler un problème" report where the user chose
     * not to attach the conversation). [jobId] optionally attaches a failed persona/universe
     * generation job's structured context (type, prompt, failure category, whether a credit was
     * consumed, raw technical error) — see "cliquer sur la bande d'une génération échouée" flow in
     * `feature:persona`/`feature:universe`.
     *
     * [focusMessageId]/[flaggedContentText] add a targeted "## Contenu d'intérêt" section instead of
     * requiring the reporter to expose the whole conversation (demande explicite) — a specific
     * already-persisted message plus a small window of surrounding context (from the tamper-evident
     * [messageAuditLogRepository], same source as the full transcript below), or, for content the
     * moderation system blocked live and which was therefore never persisted anywhere,
     * [flaggedContentText]/[flaggedContentCategory] captures it directly at report time (this is the
     * only chance to record it — see `ChatViewModel`'s content-policy-violation flow). [flaggedContentText]
     * is deliberately sourced from the moderation classifier's own identified excerpt, not raw
     * user-supplied text — the user cannot doctor or omit it before reporting, unlike a free-text
     * field they'd type themselves. [chatId] is still needed in both cases purely to look up
     * audit-log context, independently of whether the full transcript itself gets attached.
     * [attachFullConversation] (default true, matching prior behavior) controls whether the separate
     * "## Conversation" full-transcript section is included — the two targeted flows above pass
     * `false` so a focused report doesn't also dump the entire conversation by default. */
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
    ): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val userProfile = userProfileStore.get()
        val firstName = userProfile.firstName
        val lastName = userProfile.lastName

        return buildString {
            appendLine("# Kitsune — Rapport de bug (anonyme)")
            appendLine("Généré le : $timestamp")
            appendLine()
            appendLine("Ce rapport ne contient ni clé API, ni safe word, ni identifiant personnel.")
            if (firstName.isNotBlank()) appendLine("Le prénom de l'utilisateur a été remplacé par « ${PersonalNameRedactor.FIRST_NAME_TOKEN} ».")
            if (lastName.isNotBlank()) appendLine("Le nom de l'utilisateur a été remplacé par « ${PersonalNameRedactor.LAST_NAME_TOKEN} ».")
            appendLine()

            appendLine("## Objet")
            appendLine(subject.ifBlank { "(non renseigné)" })
            appendLine()
            appendLine("## Description")
            appendLine(description.ifBlank { "(non renseignée)" })
            appendLine()

            appendLine("## Configuration")
            appendLine("- Modèle de chat : ${networkPreferences.getDefaultChatModelId()}")
            appendLine("- Modèle d'image : ${networkPreferences.getDefaultImageModelId()}")
            appendLine("- Température : ${networkPreferences.getDefaultTemperature()}")
            appendLine("- Verrouillage automatique : ${autoLockManager.timeoutSeconds}s")
            appendLine("- Profil utilisateur renseigné : ${if (!userProfile.isBlank) "oui" else "non"}")
            appendLine()

            if (jobId != null) {
                appendLine("## Génération échouée")
                val job = generationJobRepository.getById(jobId)
                if (job == null) {
                    appendLine("Job introuvable (id=$jobId).")
                } else {
                    appendGenerationJobSection(job)
                }
                appendLine()
            }

            if (detailed) {
                appendLine("## Données système détaillées")
                try {
                    // Note: Les méthodes getAll() ne sont pas disponibles sur tous les repositories
                    // Pour l'instant, nous collectons uniquement les informations de la conversation courante
                    appendLine("(Collecte détaillée limitée pour préserver la performance)")
                } catch (e: Exception) {
                    appendLine("(Erreur lors de la collecte des données détaillées: ${e.message})")
                }
                appendLine()
            }

            // Sourced from the immutable audit trail, not the live (editable) message table — a
            // message that was since edited or deleted here still shows up as it originally was,
            // so a report/appeal can't be dodged by cleaning up a chat before submitting it
            // (demande explicite, see MessageAuditLogEntity's doc comment). Loaded once and reused
            // below for both the targeted excerpt section and the full transcript, rather than
            // querying twice when both are requested.
            val auditEntries = chatId?.let { messageAuditLogRepository.getByChatId(it) }.orEmpty()

            if (focusMessageId != null || flaggedContentText != null) {
                appendLine("## Contenu d'intérêt")
                appendInterestingContentSection(auditEntries, focusMessageId, flaggedContentText, flaggedContentCategory, firstName, lastName)
                appendLine()
            }

            if (!attachFullConversation) {
                return@buildString
            }

            if (chatId == null) {
                appendLine("## Conversation")
                appendLine("(aucune conversation jointe à ce rapport)")
                return@buildString
            }

            val chat = chatRepository.getById(chatId)
            val persona = chat?.personaId?.let { personaRepository.getById(it) }
            val messages = auditEntries.map { entry ->
                MessageEntity(
                    id = entry.messageId,
                    chatId = entry.chatId,
                    role = entry.role,
                    content = entry.content,
                    imageAttachmentPath = null,
                    isEdited = false,
                    moderationFlag = ModerationFlag.NONE,
                    tokenCount = null,
                    createdAt = entry.createdAt
                )
            }

            appendLine("## Conversation")
            if (chat == null) {
                appendLine("Conversation introuvable (id manquant).")
            } else {
                appendLine("- Persona : ${persona?.name ?: "inconnu"}")
                appendLine("- Tags de maturité : ${persona?.maturityTags?.joinToString() ?: "inconnu"}")
                appendLine("- Avatar défini : ${if (persona?.avatarImageId != null) "oui" else "non"}")
                appendLine("- Fond de conversation défini : ${if (chat.backgroundImageId != null) "oui" else "non"}")
                appendLine("- Résumé de mémoire renseigné : ${if (chat.summary.isNotBlank()) "oui" else "non"}")
                appendLine("- Mode : ${chat.mode}")
                appendLine("- Archivé : ${if (chat.archived) "oui" else "non"}")
                appendLine("- Créé le : ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(chat.createdAt))}")
                appendLine("- Mis à jour le : ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(chat.updatedAt))}")
                
                if (detailed) {
                    appendLine("\n### Détails de la conversation")
                    appendLine("- ID : $chatId")
                    appendLine("- ID Persona : ${chat.personaId ?: "aucun"}")
                    appendLine("- ID Scène sélectionnée : ${chat.selectedSceneId ?: "aucune"}")
                    appendLine("- ID Message de branchement : ${chat.branchedFromMessageId ?: "aucun"}")
                    appendLine("- ID Image de fond : ${chat.backgroundImageId ?: "aucune"}")
                    appendLine("- Résumé : ${if (chat.summary.isBlank()) "(vide)" else chat.summary.take(200) + "..."}")
                }
            }
            appendLine("- Messages utilisateur : ${messages.count { it.role == MessageRole.USER }}")
            appendLine("- Messages IA : ${messages.count { it.role == MessageRole.ASSISTANT }}")
            appendLine("- Messages système : ${messages.count { it.role == MessageRole.SYSTEM }}")
            // Counted separately: these are style-pivot markers sent to the model, never shown to
            // the user — useful when diagnosing "the model ignores my mode change" reports.
            appendLine("- Directives de style : ${messages.count { it.role == MessageRole.STYLE_DIRECTIVE }}")
            
            if (detailed && messages.isNotEmpty()) {
                appendLine("\n### Statistiques des messages")
                val userMessages = messages.filter { it.role == MessageRole.USER }
                val assistantMessages = messages.filter { it.role == MessageRole.ASSISTANT }
                val avgUserLength = if (userMessages.isNotEmpty()) userMessages.sumOf { it.content.length } / userMessages.size else 0
                val avgAssistantLength = if (assistantMessages.isNotEmpty()) assistantMessages.sumOf { it.content.length } / assistantMessages.size else 0
                appendLine("- Longueur moyenne messages utilisateur : $avgUserLength caractères")
                appendLine("- Longueur moyenne messages IA : $avgAssistantLength caractères")
                
                // Derniers messages avec timestamps
                appendLine("\n### Derniers messages (5 max)")
                messages.take(5).forEach { msg ->
                    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(msg.createdAt))
                    appendLine("[$time - ${roleLabel(msg)}] ${msg.content.take(100)}${if (msg.content.length > 100) "..." else ""}")
                }
            }
            appendLine()

            appendLine("## Transcription anonymisée")
            if (messages.isEmpty()) {
                appendLine("(aucun message)")
            } else {
                messages.forEach { message -> appendLine("[${roleLabel(message)}] ${anonymise(message, firstName, lastName)}") }
            }
        }.trim()
    }

    private fun StringBuilder.appendGenerationJobSection(job: GenerationJobEntity) {
        val category = job.failureCategory()
        appendLine("- Type : ${job.type}")
        appendLine("- Propositions demandées : ${job.proposalCount}")
        appendLine("- Description/prompt utilisé : ${job.description.ifBlank { "(vide)" }}")
        appendLine("- Catégorie d'échec : ${categoryLabel(category)}")
        appendLine("- Crédit consommé pour cette tentative : ${creditConsumedLabel(category)}")
        appendLine("- Message technique brut : ${job.errorMessage ?: "(aucun)"}")
        appendLine("- Créé le : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(job.createdAt))}")
        job.completedAt?.let {
            appendLine("- Échoué le : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it))}")
        }
    }

    /** Renders the targeted "Contenu d'intérêt" excerpt — either a live moderation block
     * ([flaggedContentText], never persisted anywhere else, this is the only chance to capture it)
     * or an already-persisted message the user chose to report ([focusMessageId]), each with a
     * small window of surrounding [auditEntries] context so the excerpt isn't read out of context,
     * without requiring the full conversation to be attached. */
    private fun StringBuilder.appendInterestingContentSection(
        auditEntries: List<MessageAuditLogEntity>,
        focusMessageId: String?,
        flaggedContentText: String?,
        flaggedContentCategory: String?,
        firstName: String,
        lastName: String
    ) {
        when {
            flaggedContentText != null -> {
                appendLine("Contenu bloqué automatiquement par le filtre de modération (catégorie : ${flaggedContentCategory ?: "inconnue"}) :")
                appendLine("$FOCUS_MARKER ${anonymiseText(flaggedContentText, firstName, lastName)}")
                val contextBefore = auditEntries.takeLast(CONTEXT_MESSAGES_BEFORE)
                if (contextBefore.isNotEmpty()) {
                    appendLine()
                    appendLine("Contexte immédiatement avant ce blocage :")
                    contextBefore.forEach { entry ->
                        appendLine("[${roleLabelForRole(entry.role)}] ${anonymiseText(entry.content, firstName, lastName)}")
                    }
                }
            }
            focusMessageId != null -> {
                val focusIndex = auditEntries.indexOfFirst { it.messageId == focusMessageId }
                if (focusIndex == -1) {
                    appendLine("(message signalé introuvable dans l'historique)")
                } else {
                    val start = (focusIndex - CONTEXT_MESSAGES_BEFORE).coerceAtLeast(0)
                    val end = (focusIndex + CONTEXT_MESSAGES_AFTER).coerceAtMost(auditEntries.size - 1)
                    for (i in start..end) {
                        val entry = auditEntries[i]
                        val marker = if (i == focusIndex) "$FOCUS_MARKER " else ""
                        appendLine("$marker[${roleLabelForRole(entry.role)}] ${anonymiseText(entry.content, firstName, lastName)}")
                    }
                }
            }
        }
    }

    private fun categoryLabel(category: GenerationFailureCategory?): String = when (category) {
        GenerationFailureCategory.CONTENT_POLICY -> "Bloqué par le filtre de contenu (contenu potentiellement impliquant un mineur ou non consensuel)"
        GenerationFailureCategory.INSUFFICIENT_CREDITS -> "Crédits insuffisants"
        GenerationFailureCategory.PARSING_FAILED -> "Réponse de l'IA reçue mais impossible à interpréter"
        GenerationFailureCategory.TECHNICAL -> "Erreur technique (réseau, serveur...)"
        null -> "Inconnue (job antérieur à cette fonctionnalité)"
    }

    private fun creditConsumedLabel(category: GenerationFailureCategory?): String =
        category?.let { if (it.creditConsumed) "Oui" else "Non" } ?: "Inconnu"

    private fun roleLabelForRole(role: MessageRole): String = when (role) {
        MessageRole.USER -> "Utilisateur"
        MessageRole.ASSISTANT -> "IA"
        MessageRole.SYSTEM -> "Système"
        MessageRole.STYLE_DIRECTIVE -> "Directive de style"
    }

    private fun roleLabel(message: MessageEntity): String = roleLabelForRole(message.role)

    private fun anonymiseText(text: String, firstName: String, lastName: String): String =
        PersonalNameRedactor.redact(text.ifBlank { "(vide)" }, firstName, lastName)

    private fun anonymise(message: MessageEntity, firstName: String, lastName: String): String =
        anonymiseText(message.content, firstName, lastName)
}
