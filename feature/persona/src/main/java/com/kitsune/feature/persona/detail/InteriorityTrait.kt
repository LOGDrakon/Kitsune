package com.kitsune.feature.persona.detail

import androidx.annotation.StringRes
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.feature.persona.R

/**
 * Les cinq traits d'intériorité dramatique d'un persona (2026-08-22).
 *
 * ## Pourquoi cinq champs distincts et non le bloc `personality`
 *
 * `PersonaEntity.personality` reste de la prose libre, et c'est ce qui fait sa limite : « brave et
 * loyale » ne dit pas au modèle ce que le personnage *veut*, ni ce qu'il refusera de faire. Un
 * modèle suit bien plus fidèlement une contrainte nommée qu'un adjectif — c'est l'écart entre un
 * personnage cohérent en surface et un personnage qui produit des refus crédibles et des
 * complications qui lui ressemblent.
 *
 * ## Pourquoi une énumération
 *
 * Chaque trait est un `String` sur la même entité ; l'énumération porte l'accès (`read`/`write`) et
 * son libellé, ce qui permet d'écrire l'écran d'édition et le `ViewModel` une seule fois pour les
 * cinq, et de rendre exhaustif tout traitement qui les parcourt.
 */
enum class InteriorityTrait(
    @StringRes val label: Int,
    @StringRes val hint: Int,
    val read: (PersonaEntity) -> String,
    val write: (PersonaEntity, String) -> PersonaEntity
) {
    /** Ce qu'il veut : le moteur de ses actions. */
    DESIRE(
        R.string.persona_interiority_desire,
        R.string.persona_interiority_desire_hint,
        { it.desire },
        { p, v -> p.copy(desire = v) }
    ),

    /** Ce qu'il fuit : la source de sa tension. */
    FEAR(
        R.string.persona_interiority_fear,
        R.string.persona_interiority_fear_hint,
        { it.fear },
        { p, v -> p.copy(fear = v) }
    ),

    /** Comment il se saborde : ce qui génère les complications sans qu'elles tombent du ciel. */
    FLAW(
        R.string.persona_interiority_flaw,
        R.string.persona_interiority_flaw_hint,
        { it.flaw },
        { p, v -> p.copy(flaw = v) }
    ),

    /** Ce qu'il ne fera jamais : la ligne dure qui crée de vrais enjeux quand on la pousse. */
    MORAL_LINE(
        R.string.persona_interiority_moral_line,
        R.string.persona_interiority_moral_line_hint,
        { it.moralLine },
        { p, v -> p.copy(moralLine = v) }
    ),

    /** Ce qu'il cache : l'ironie dramatique, et un paiement différé pour plus tard. */
    SECRET(
        R.string.persona_interiority_secret,
        R.string.persona_interiority_secret_hint,
        { it.secret },
        { p, v -> p.copy(secret = v) }
    )
}
