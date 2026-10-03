package com.kitsune.feature.persona.exportimport

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.PersonaImageEntity
import com.kitsune.core.data.repository.PersonaImageRepository
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.core.data.repository.EntrySceneRepository
import com.kitsune.core.data.local.entities.decodeEntryScenes
import com.kitsune.core.data.local.entities.decodeToneCards
import com.kitsune.core.data.local.entities.toEntity
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.security.storage.EncryptedImageStore
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

class PersonaImportUseCase @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val personaImageRepository: PersonaImageRepository,
    private val toneCardRepository: ToneCardRepository,
    private val entrySceneRepository: EntrySceneRepository,
    private val encryptedImageStore: EncryptedImageStore,
    private val databaseProvider: KitsuneDatabaseProvider
) {
    /**
     * BUG-007 (BUGS.md): the persona row and every image row it references used to be written as
     * independent, unrelated `upsert` calls — a failure partway through the images loop (e.g. a
     * corrupted `imageBase64`, BUG-008) left a real, already-visible persona in the user's list
     * with only some of its gallery images, even though [importPersona] reported failure. The
     * persona/image DB writes below now run inside [KitsuneDatabaseProvider.runInTransaction]:
     * either the whole persona (with all its images) commits, or none of it does. This does not
     * (and structurally cannot) roll back the [EncryptedImageStore.save] calls also made before a
     * failure — those write plain encrypted files, outside the SQL transaction — so a failed
     * import can still leave a handful of orphaned encrypted image files on disk. That's an
     * accepted, much lower-severity trade-off (wasted disk space, no data-integrity or security
     * impact, no dangling *database* reference to them) rather than the DB itself ending up
     * inconsistent.
     */
    suspend fun importPersona(json: String): Result<String> = runCatching {
        val exported = Json.decodeFromString<ExportedPersona>(json)

        val avatarImageId = exported.persona.avatarImageBase64?.let { base64 ->
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            encryptedImageStore.save(bytes)
        }

        val newPersonaId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val maturityTags = exported.persona.maturityTags.mapNotNull { tagName ->
            try {
                MaturityTag.valueOf(tagName)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val persona = PersonaEntity(
            id = newPersonaId,
            universeId = null,
            name = exported.persona.name,
            shortDescription = exported.persona.shortDescription,
            personality = exported.persona.personality,
            scenario = exported.persona.scenario,
            firstMessage = exported.persona.firstMessage,
            exampleDialogues = exported.persona.exampleDialogues,
            desire = exported.persona.desire,
            fear = exported.persona.fear,
            flaw = exported.persona.flaw,
            moralLine = exported.persona.moralLine,
            secret = exported.persona.secret,
            age = exported.persona.age,
            maturityTags = maturityTags,
            tags = exported.persona.tags,
            visualSheetJson = exported.persona.visualSheetJson,
            avatarImageId = avatarImageId,
            createdAt = now,
            updatedAt = now
        )

        // Images are decoded/saved to disk (outside the transaction, see doc comment above)
        // before entering the transaction, so nothing inside it can throw for a Base64/IO reason —
        // only the DB writes themselves remain to roll back together.
        val personaImages = exported.images.map { img ->
            val bytes = android.util.Base64.decode(img.imageBase64, android.util.Base64.NO_WRAP)
            val imageStoreId = encryptedImageStore.save(bytes)
            PersonaImageEntity(
                id = UUID.randomUUID().toString(),
                personaId = newPersonaId,
                imageStoreId = imageStoreId,
                description = img.description,
                createdAt = img.createdAt
            )
        }

        // Inside the same transaction as the persona itself: a half-imported character that has its
        // tones but not its images (or the reverse) is the atomicity problem this block exists for.
        val toneCards = decodeToneCards(exported.persona.toneCardsJson)
            .map { it.toEntity(personaId = newPersonaId) }
        val entryScenes = decodeEntryScenes(exported.persona.entryScenesJson)
            .map { it.toEntity(personaId = newPersonaId) }

        databaseProvider.runInTransaction {
            personaRepository.upsert(persona)
            personaImages.forEach { personaImageRepository.upsert(it) }
            toneCards.forEach { toneCardRepository.upsert(it) }
            entryScenes.forEach { entrySceneRepository.upsert(it) }
        }

        newPersonaId
    }
}
