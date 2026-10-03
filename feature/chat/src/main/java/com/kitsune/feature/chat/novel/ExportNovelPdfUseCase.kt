package com.kitsune.feature.chat.novel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.Spanned
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.KeyMomentRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.network.visualsheet.EnsurePersonaVisualSheetUseCase
import com.kitsune.core.network.visualsheet.buildPersonaImageContext
import com.kitsune.core.security.storage.EncryptedImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

/**
 * Behind the "Export Roman Illustré" cosmetic (bought once, real money — see
 * `SkuDefinitions.cosmeticProducts` / `CosmeticCatalog.exportNovel`). Turns a chat into an
 * offline-readable PDF: illustrated cover with the cast's avatars, chapters cut at each recorded
 * [KeyMomentEntity] instead of a raw message dump, and a lore appendix if the chat has any.
 *
 * Pure `android.graphics.pdf.PdfDocument` — no third-party PDF library, no new dependency.
 */
class ExportNovelPdfUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val personaRepository: PersonaRepository,
    private val npcRepository: NpcRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val keyMomentRepository: KeyMomentRepository,
    private val loreEntryRepository: LoreEntryRepository,
    private val imageStore: EncryptedImageStore,
    private val ensurePersonaVisualSheetUseCase: EnsurePersonaVisualSheetUseCase,
    private val generateNovelCoverUseCase: GenerateNovelCoverUseCase
) {
    suspend fun export(chatId: String, output: OutputStream) = withContext(Dispatchers.IO) {
        val chat = requireNotNull(chatRepository.getById(chatId)) { "Chat $chatId not found" }
        val messages = messageRepository.getRecent(chatId, Int.MAX_VALUE)
            .asReversed()
            .filter { it.role != MessageRole.SYSTEM && it.role != MessageRole.STYLE_DIRECTIVE && it.content.isNotBlank() }
        val moments = keyMomentRepository.getByChat(chatId).sortedBy { it.momentOrder }
        val lore = loreEntryRepository.getByChat(chatId)

        val protagonistId = chat.personaId
        val castMembers: List<CastMember> = if (protagonistId != null) {
            listOfNotNull(personaRepository.getById(protagonistId)?.let { personaCastMember(it) })
        } else {
            chatParticipantRepository.getByChat(chatId).mapNotNull { participant ->
                when (participant.participantType) {
                    ParticipantType.PERSONA -> personaRepository.getById(participant.participantId)?.let { personaCastMember(it) }
                    ParticipantType.NPC -> npcRepository.getById(participant.participantId)?.let { npcCastMember(it) }
                }
            }
        }
        val characters = castMembers.map { Character(it.name, it.avatarImageId) }

        val title = chat.title.ifBlank { characters.firstOrNull()?.name ?: "Une histoire Kitsune" }
        val coverBitmap = runCatching {
            generateNovelCoverUseCase(
                title = title,
                storySummary = chat.summary,
                characterContext = castMembers.joinToString("\n\n") { it.imageContext },
                allowMatureContent = castMembers.any { it.allowMature }
            )?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }.getOrNull()

        val doc = PdfDocument()
        val painter = PdfPainter(doc)
        painter.drawCoverPage(title, characters, coverBitmap) { id -> id?.let { loadAvatarBitmap(it) } }
        painter.drawChapters(buildChapters(messages, moments))
        if (lore.isNotEmpty()) painter.drawLoreAppendix(lore)
        painter.finish()

        doc.writeTo(output)
        doc.close()
    }

    /** Backfills a visual sheet transparently (mirroring `ImageGenerationViewModel
     * .buildCastImageContext`) and persists it if newly generated, so every later cover/image
     * generation for this persona keeps describing the same look. */
    private suspend fun personaCastMember(persona: PersonaEntity): CastMember {
        val sheet = ensurePersonaVisualSheetUseCase(
            name = persona.name,
            shortDescription = persona.shortDescription,
            personality = persona.personality,
            existingVisualSheetJson = persona.visualSheetJson
        )
        val resolved = if (sheet.wasGenerated) {
            persona.copy(visualSheetJson = sheet.visualSheetJson, updatedAt = System.currentTimeMillis()).also {
                personaRepository.upsert(it)
            }
        } else {
            persona
        }
        return CastMember(
            name = resolved.name,
            avatarImageId = resolved.avatarImageId,
            imageContext = buildPersonaImageContext(resolved.name, resolved.shortDescription, resolved.visualSheetJson),
            allowMature = MaturityTag.NSFW in resolved.maturityTags || MaturityTag.DARK in resolved.maturityTags
        )
    }

    private fun npcCastMember(npc: NpcEntity): CastMember {
        val appearance = npc.physicalDescription?.takeIf { it.isNotBlank() }?.let { ". Apparence : $it" }.orEmpty()
        return CastMember(
            name = npc.name,
            avatarImageId = npc.avatarImageId,
            imageContext = "${npc.name} (${npc.role}): ${npc.description}$appearance",
            allowMature = false
        )
    }

    private fun loadAvatarBitmap(id: String): Bitmap? {
        val bytes = imageStore.load(id) ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    /** Splits the chronological message list into chapters at each [KeyMomentEntity]'s anchor
     * message. Chats with no recorded moments (or moments never anchored to a message) fall back
     * to a single "Chapitre 1" — still a real export, never an error. */
    private fun buildChapters(messages: List<MessageEntity>, moments: List<KeyMomentEntity>): List<Pair<String, List<MessageEntity>>> {
        val titleByMessageId = moments.mapNotNull { moment -> moment.messageId?.let { it to moment.title } }.toMap()
        if (titleByMessageId.isEmpty()) return listOf("Chapitre 1" to messages)

        val chapters = mutableListOf<Pair<String, MutableList<MessageEntity>>>()
        var current = mutableListOf<MessageEntity>()
        var currentTitle: String? = null
        var autoChapterCount = 0

        for (message in messages) {
            val newTitle = titleByMessageId[message.id]
            if (newTitle != null) {
                if (current.isNotEmpty()) {
                    chapters += (currentTitle ?: "Chapitre ${++autoChapterCount}") to current
                }
                current = mutableListOf()
                currentTitle = newTitle
            }
            current.add(message)
        }
        if (current.isNotEmpty()) {
            chapters += (currentTitle ?: "Chapitre ${++autoChapterCount}") to current
        }
        return chapters
    }
}

private data class Character(val name: String, val avatarImageId: String?)

/** A cast member enriched with what [GenerateNovelCoverUseCase] needs beyond the avatar-row
 * cover's plain [Character] — the same visual-continuity text used for any other image
 * generation of this character ([imageContext]), and whether their maturity tags mean the cover
 * generation call should run with the mature system-prompt variant on ([allowMature]). */
private data class CastMember(val name: String, val avatarImageId: String?, val imageContext: String, val allowMature: Boolean)

private fun sectionLabel(type: LoreEntryType): String = when (type) {
    LoreEntryType.CHARACTER -> "Personnages"
    LoreEntryType.LOCATION -> "Lieux"
    LoreEntryType.FACTION -> "Factions"
    LoreEntryType.EVENT -> "Événements"
    LoreEntryType.ITEM -> "Objets"
    LoreEntryType.THREAD -> "Fils narratifs"
}

/** `**bold**` / `*italic actions*` / `(asides)` → real bold/italic spans, print-convention only
 * (no color, unlike the in-app Compose renderer — a book page is monochrome). */
private fun styledSpannable(raw: String): SpannableString {
    val plain = StringBuilder()
    val boldRanges = mutableListOf<IntRange>()
    val italicRanges = mutableListOf<IntRange>()
    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end == -1) { plain.append(raw.substring(i)); i = raw.length }
                else {
                    val start = plain.length
                    plain.append(raw.substring(i + 2, end))
                    boldRanges += start until plain.length
                    i = end + 2
                }
            }
            raw[i] == '*' -> {
                val end = raw.indexOf('*', i + 1)
                if (end == -1) { plain.append(raw.substring(i)); i = raw.length }
                else {
                    val start = plain.length
                    plain.append(raw.substring(i + 1, end))
                    italicRanges += start until plain.length
                    i = end + 1
                }
            }
            raw[i] == '(' -> {
                val end = raw.indexOf(')', i + 1)
                if (end == -1) { plain.append(raw.substring(i)); i = raw.length }
                else {
                    val start = plain.length
                    plain.append(raw.substring(i, end + 1))
                    italicRanges += start until plain.length
                    i = end + 1
                }
            }
            else -> { plain.append(raw[i]); i++ }
        }
    }
    return SpannableString(plain.toString()).apply {
        boldRanges.forEach { setSpan(StyleSpan(Typeface.BOLD), it.first, it.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        italicRanges.forEach { setSpan(StyleSpan(Typeface.ITALIC), it.first, it.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
}

/** Paginates flowing text/heading/avatar drawing across as many A4 [PdfDocument] pages as needed. */
private class PdfPainter(private val doc: PdfDocument) {

    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 56f
    }

    private val contentWidth = PAGE_WIDTH - 2 * MARGIN
    private val contentBottom = PAGE_HEIGHT - MARGIN

    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var yCursor = MARGIN
    private var pageNumber = 0

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNumber++
        val started = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        page = started
        canvas = started.canvas
        yCursor = MARGIN
    }

    fun finish() {
        page?.let { doc.finishPage(it) }
        page = null
    }

    private fun ensureSpace(height: Float) {
        if (page == null) { newPage(); return }
        if (yCursor + height > contentBottom) newPage()
    }

    private fun drawParagraph(text: CharSequence, paint: TextPaint, spacingAfter: Float = 14f, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
        if (text.isBlank()) return
        if (page == null) newPage()
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, contentWidth.toInt())
            .setAlignment(align)
            .setLineSpacing(0f, 1.18f)
            .setIncludePad(false)
            .build()

        var lineIndex = 0
        while (lineIndex < layout.lineCount) {
            ensureSpace(paint.textSize * 1.18f)
            val remaining = contentBottom - yCursor
            val startTop = layout.getLineTop(lineIndex)
            var endLine = lineIndex
            while (endLine < layout.lineCount && (layout.getLineBottom(endLine) - startTop) <= remaining) {
                endLine++
            }
            if (endLine == lineIndex) endLine = lineIndex + 1

            val top = layout.getLineTop(lineIndex)
            val bottom = layout.getLineBottom(endLine - 1)
            val c = requireNotNull(canvas)
            c.save()
            c.translate(MARGIN, yCursor - top)
            c.clipRect(0f, top.toFloat(), contentWidth, bottom.toFloat())
            layout.draw(c)
            c.restore()
            yCursor += (bottom - top)
            lineIndex = endLine
            if (lineIndex < layout.lineCount) newPage()
        }
        yCursor += spacingAfter
    }

    private fun centeredTextPaint(size: Float, style: Int = Typeface.NORMAL, italic: Boolean = false, color: Int = Color.BLACK): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, if (italic) Typeface.ITALIC else style)
        }

    fun drawCoverPage(title: String, characters: List<Character>, coverImage: Bitmap?, loadAvatar: (String?) -> Bitmap?) {
        newPage()
        val c = requireNotNull(canvas)

        if (coverImage != null) {
            drawIllustratedCover(c, title, coverImage)
            return
        }

        yCursor = PAGE_HEIGHT / 3.2f
        c.drawText(title, PAGE_WIDTH / 2f, yCursor, centeredTextPaint(28f, Typeface.BOLD))
        yCursor += 26f
        c.drawText("Un roman généré avec Kitsune", PAGE_WIDTH / 2f, yCursor, centeredTextPaint(13f, italic = true, color = Color.DKGRAY))
        yCursor += 56f

        if (characters.isNotEmpty()) {
            val avatarSize = 64f
            val spacing = 22f
            val shown = characters.take(4)
            val totalWidth = shown.size * avatarSize + (shown.size - 1) * spacing
            var cx = PAGE_WIDTH / 2f - totalWidth / 2f + avatarSize / 2f
            val namePaint = centeredTextPaint(11f, color = Color.DKGRAY).apply { typeface = Typeface.DEFAULT }

            shown.forEach { character ->
                val bmp = loadAvatar(character.avatarImageId)
                val top = yCursor
                if (bmp != null) {
                    val clip = Path().apply { addCircle(cx, top + avatarSize / 2f, avatarSize / 2f, Path.Direction.CW) }
                    c.save()
                    c.clipPath(clip)
                    c.drawBitmap(
                        bmp,
                        Rect(0, 0, bmp.width, bmp.height),
                        RectF(cx - avatarSize / 2f, top, cx + avatarSize / 2f, top + avatarSize),
                        Paint(Paint.ANTI_ALIAS_FLAG)
                    )
                    c.restore()
                } else {
                    c.drawCircle(cx, top + avatarSize / 2f, avatarSize / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY })
                    c.drawText(
                        character.name.take(1).uppercase(),
                        cx,
                        top + avatarSize / 2f + 9f,
                        centeredTextPaint(24f, Typeface.BOLD, color = Color.WHITE)
                    )
                }
                c.drawText(character.name, cx, top + avatarSize + 16f, namePaint)
                cx += avatarSize + spacing
            }
            yCursor += avatarSize + 30f
        }
    }

    /** Full-bleed AI-generated cover art (see [GenerateNovelCoverUseCase]) with the title overlaid
     * near the top on a translucent dark band for legibility regardless of the art's own colors —
     * a real book-cover layout, replacing the plain-text + avatar-row cover entirely rather than
     * combining both (the art already conveys the cast visually). Center-crops [coverImage] to the
     * page's aspect ratio, same idea as a CSS `background-size: cover`. */
    private fun drawIllustratedCover(c: Canvas, title: String, coverImage: Bitmap) {
        val pageRatio = PAGE_WIDTH.toFloat() / PAGE_HEIGHT.toFloat()
        val srcRatio = coverImage.width.toFloat() / coverImage.height.toFloat()
        val srcRect = if (srcRatio > pageRatio) {
            val cropWidth = (coverImage.height * pageRatio).toInt().coerceAtMost(coverImage.width)
            val xOffset = (coverImage.width - cropWidth) / 2
            Rect(xOffset, 0, xOffset + cropWidth, coverImage.height)
        } else {
            val cropHeight = (coverImage.width / pageRatio).toInt().coerceAtMost(coverImage.height)
            val yOffset = (coverImage.height - cropHeight) / 2
            Rect(0, yOffset, coverImage.width, yOffset + cropHeight)
        }
        c.drawBitmap(coverImage, srcRect, RectF(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG))

        val titleY = PAGE_HEIGHT / 3.6f
        c.drawRect(
            0f, titleY - 40f, PAGE_WIDTH.toFloat(), titleY + 66f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        )
        c.drawText(title, PAGE_WIDTH / 2f, titleY, centeredTextPaint(28f, Typeface.BOLD, color = Color.WHITE))
        c.drawText(
            "Un roman généré avec Kitsune",
            PAGE_WIDTH / 2f,
            titleY + 26f,
            centeredTextPaint(13f, italic = true, color = Color.LTGRAY)
        )
    }

    fun drawChapters(chapters: List<Pair<String, List<MessageEntity>>>) {
        val narrationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val playerLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }

        chapters.forEach { (chapterTitle, messages) ->
            newPage()
            yCursor += 36f
            val c = requireNotNull(canvas)
            c.drawText(chapterTitle, PAGE_WIDTH / 2f, yCursor, centeredTextPaint(19f, Typeface.BOLD))
            yCursor += 8f
            c.drawLine(
                PAGE_WIDTH / 2f - 26f, yCursor + 4f, PAGE_WIDTH / 2f + 26f, yCursor + 4f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 1f }
            )
            yCursor += 34f

            messages.forEach { message ->
                if (message.role == MessageRole.USER) {
                    drawParagraph("Vous", playerLabelPaint, spacingAfter = 3f)
                }
                drawParagraph(styledSpannable(message.content), narrationPaint, spacingAfter = 14f)
            }
        }
    }

    fun drawLoreAppendix(lore: List<LoreEntryEntity>) {
        newPage()
        yCursor += 24f
        requireNotNull(canvas).drawText("Résumé de l'univers", PAGE_WIDTH / 2f, yCursor, centeredTextPaint(19f, Typeface.BOLD))
        yCursor += 44f

        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 13.5f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 11.5f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = 10.5f; typeface = Typeface.SERIF
        }

        val grouped = lore.groupBy { it.entryType }
        listOf(
            LoreEntryType.CHARACTER, LoreEntryType.LOCATION, LoreEntryType.FACTION,
            LoreEntryType.EVENT, LoreEntryType.ITEM
        ).forEach { type ->
            val entries = grouped[type] ?: return@forEach
            drawParagraph(sectionLabel(type), sectionPaint, spacingAfter = 10f, align = Layout.Alignment.ALIGN_NORMAL)
            entries.forEach { entry ->
                drawParagraph(entry.name, namePaint, spacingAfter = 2f)
                drawParagraph(entry.summary.ifBlank { entry.content }, bodyPaint, spacingAfter = 16f)
            }
        }
    }
}
