package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [age] is validated and locked at creation (see FEATURES.md section 3: anti-contournement).
 * Only the persona-edit use case may ever write this column; the generation/roleplay pipeline
 * must never update a PersonaEntity row's age, by construction (it should only ever read it).
 */
@Entity(
    tableName = "personas",
    foreignKeys = [
        ForeignKey(
            entity = UniverseEntity::class,
            parentColumns = ["id"],
            childColumns = ["universeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("universeId"), Index("sourceListingId")]
)
data class PersonaEntity(
    @PrimaryKey val id: String,
    val universeId: String?,
    val name: String,
    val shortDescription: String,
    val personality: String,
    /**
     * Intériorité dramatique du personnage (2026-08-22).
     *
     * [personality] reste la prose qui donne la voix ; ces cinq champs donnent au modèle des
     * **leviers explicites** plutôt que des adjectifs. Un modèle suit bien plus fidèlement « refuse
     * de trahir un allié » qu'un « loyale » noyé dans un paragraphe : c'est ce qui produit des
     * refus crédibles, des complications qui découlent du personnage, et des secrets qui finissent
     * par tomber.
     *
     * Tous par défaut vides : les personas créés avant cette version restent valides et le prompt
     * omet simplement les sections correspondantes (voir MIGRATION_30_31).
     */
    /** Ce qu'il veut — moteur de ses initiatives. */
    val desire: String = "",
    /** Ce qu'il fuit — source de tension et de réticence. */
    val fear: String = "",
    /** Comment il se saborde — d'où naissent les complications. */
    val flaw: String = "",
    /** Ce qu'il ne fera jamais — crée de vrais enjeux et des refus qui ne sonnent pas arbitraires. */
    val moralLine: String = "",
    /** Ce qu'il cache — ironie dramatique, et paiement différé quand ça sort. */
    val secret: String = "",
    val scenario: String,
    val firstMessage: String,
    val exampleDialogues: String,
    val age: Int,
    val maturityTags: List<MaturityTag>,
    /** Free-form descriptive tags (e.g. "romance", "slow-burn") — user-editable, optionally
     * AI-suggested at creation time (see `GenerateQuickPersonaUseCase`). Also sent to the
     * marketplace listing's own `tags` field when publishing. */
    val tags: List<String> = emptyList(),
    /**
     * Structured JSON (encoded from `PersonaVisualSheet` in `core:network`): physical traits, art
     * style, palette, default outfit — reused verbatim in every image-generation prompt so the
     * character's appearance stays consistent across separate generations (FEATURES.md section 5,
     * visual continuity system). Null for personas created before this existed or if generation
     * failed; transparently backfilled by `EnsurePersonaVisualSheetUseCase` on first image generation.
     */
    val visualSheetJson: String?,
    /** Id into `EncryptedImageStore` (core:security) — never a raw filesystem path. */
    val avatarImageId: String? = null,
    /** Marketplace listing id this persona was downloaded from, if any — lets the marketplace
     * detect it's already in the user's collection and avoid creating a duplicate on re-download. */
    val sourceListingId: String? = null,
    /** Best-effort [com.kitsune.core.security.locale.AppLanguage] tag (e.g. "fr") the app was set
     * to when the sheet/opening message were last written — null for personas created before this
     * existed. Used only to decide whether to *offer* the "translate this sheet" action
     * (`TranslatePersonaUseCase`); the AI may still have written some fields in a different
     * language (e.g. it follows the user's own free-text description), so this is a hint, not a
     * guarantee. */
    val contentLanguage: String? = null,
    val version: Int = 1,
    val createdAt: Long,
    val updatedAt: Long
)
