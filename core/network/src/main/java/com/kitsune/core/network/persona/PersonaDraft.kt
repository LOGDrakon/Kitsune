package com.kitsune.core.network.persona

import com.kitsune.core.network.visualsheet.PersonaVisualSheet

/** AI-generated fields only. Age and maturity tags are always set explicitly by the user afterwards. */
data class PersonaDraft(
    val name: String,
    val description: String,
    val personality: String,
    /** Intériorité dramatique, générée dans le même appel que le reste de la fiche (voir
     *  [GenerateQuickPersonaUseCase]). Vide si le modèle a omis le champ — jamais une erreur. */
    val desire: String = "",
    val fear: String = "",
    val flaw: String = "",
    val moralLine: String = "",
    val secret: String = "",
    val scenario: String,
    val firstMessage: String,
    val exampleDialogues: String,
    /** Generated in the same LLM call as the rest of the sheet (single credit) — see [com.kitsune.core.network.persona.GenerateQuickPersonaUseCase]. */
    val visualSheet: PersonaVisualSheet,
    /** A handful of AI-suggested descriptive tags (e.g. "romance", "slow-burn") — editable by the
     * user afterwards, same free-form tags as [com.kitsune.core.data.local.entities.PersonaEntity.tags]. */
    val tags: List<String> = emptyList()
)
