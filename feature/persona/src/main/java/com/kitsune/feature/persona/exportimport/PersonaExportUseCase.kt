package com.kitsune.feature.persona.exportimport

import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.PersonaImageEntity
import com.kitsune.core.data.repository.PersonaImageRepository
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.core.data.repository.EntrySceneRepository
import com.kitsune.core.data.local.entities.encodeEntryScenes
import com.kitsune.core.data.local.entities.encodeToneCards
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.security.storage.EncryptedImageStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class ExportedPersona(
    val persona: ExportedPersonaData,
    val images: List<ExportedImage>
)

@Serializable
data class ExportedPersonaData(
    val id: String,
    val name: String,
    val shortDescription: String,
    val personality: String,
    val scenario: String,
    val firstMessage: String,
    val exampleDialogues: String,
    // Ressorts intimes (2026-08-22). Defaults vides : un fichier exporte avant cette version doit
    // continuer a s'importer, pas echouer a la deserialisation.
    val desire: String = "",
    val fear: String = "",
    val flaw: String = "",
    val moralLine: String = "",
    val secret: String = "",
    val age: Int,
    val maturityTags: List<String>,
    val tags: List<String> = emptyList(),
    val visualSheetJson: String?,
    /** Author-written story tones, encoded like [visualSheetJson] — see `ToneCardPayload`. Null for a
     *  persona with no tones, and for every file exported before they existed. */
    val toneCardsJson: String? = null,
    /** Alternative starting points — see `EntryScenePayload`. */
    val entryScenesJson: String? = null,
    val avatarImageBase64: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ExportedImage(
    val id: String,
    val description: String,
    val imageBase64: String,
    val createdAt: Long
)

class PersonaExportUseCase @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val personaImageRepository: PersonaImageRepository,
    private val toneCardRepository: ToneCardRepository,
    private val entrySceneRepository: EntrySceneRepository,
    private val encryptedImageStore: EncryptedImageStore
) {
    suspend fun exportPersona(personaId: String): Result<String> = runCatching {
        val persona = personaRepository.getById(personaId)
            ?: throw IllegalArgumentException("Persona not found")

        val avatarBase64 = persona.avatarImageId?.let { imageId ->
            encryptedImageStore.load(imageId)?.let { bytes ->
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        }

        val images = personaImageRepository.getByPersona(personaId).mapNotNull { img ->
            encryptedImageStore.load(img.imageStoreId)?.let { bytes ->
                ExportedImage(
                    id = img.id,
                    description = img.description,
                    imageBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                    createdAt = img.createdAt
                )
            }
        }

        val exported = ExportedPersona(
            persona = ExportedPersonaData(
                id = persona.id,
                name = persona.name,
                shortDescription = persona.shortDescription,
                personality = persona.personality,
                scenario = persona.scenario,
                firstMessage = persona.firstMessage,
                exampleDialogues = persona.exampleDialogues,
                desire = persona.desire,
                fear = persona.fear,
                flaw = persona.flaw,
                moralLine = persona.moralLine,
                secret = persona.secret,
                age = persona.age,
                maturityTags = persona.maturityTags.map { it.name },
                tags = persona.tags,
                visualSheetJson = persona.visualSheetJson,
                toneCardsJson = encodeToneCards(toneCardRepository.getByPersona(persona.id)),
                entryScenesJson = encodeEntryScenes(entrySceneRepository.getByPersona(persona.id)),
                avatarImageBase64 = avatarBase64,
                createdAt = persona.createdAt,
                updatedAt = persona.updatedAt
            ),
            images = images
        )

        Json.encodeToString(exported)
    }
}
