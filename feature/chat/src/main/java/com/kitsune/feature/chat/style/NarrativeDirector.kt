package com.kitsune.feature.chat.style

/**
 * Choisit le **type de beat** du prochain tour, à partir de l'état local de l'histoire.
 *
 * ## Pourquoi ce fichier existe
 *
 * Les fiches de personnage et les fils en suspens donnent au modèle de la *matière*, mais ils ne lui
 * disent pas quoi en faire maintenant. Sans direction, un modèle de chat retombe naturellement sur le
 * même beat à chaque tour — réagir, relancer, réagir — et l'histoire n'avance qu'en surface. C'est ce
 * qui produit des échanges agréables mais plats.
 *
 * Ce directeur tranche ce que le modèle ne tranche pas de lui-même : quand compliquer, quand révéler,
 * quand laisser respirer, quand faire ressurgir un fil ancien.
 *
 * ## Zéro appel LLM
 *
 * Tout est déduit de signaux déjà présents en base : nombre de tours, âge des fils
 * (`LoreEntryEntity.anchorCreatedAt`), densité de moments clés. Les systèmes « satellites » qui
 * faisaient ce travail par des appels dédiés ont été supprimés en v18→v19 pour leur coût
 * (7 appels par déclenchement) ; la contrainte posée depuis est « zero extra LLM calls ». Ce fichier
 * la respecte : c'est de l'arithmétique locale.
 *
 * ## Pur et déterministe
 *
 * Aucune I/O, aucune horloge, aucun aléa non injecté : [decide] est une fonction de son entrée, donc
 * entièrement testable — ce qui compte d'autant plus que `feature:chat` n'a pas de test de
 * `ChatViewModel` et que l'assemblage du prompt, lui, ne l'est pas.
 */

/** Le type de beat demandé pour le prochain tour. */
enum class NarrativeBeat {
    /** Quelque chose se met en travers : contretemps, obstacle, mauvaise nouvelle. */
    COMPLICATION,

    /** Une information cachée fait surface et recadre ce qui précède. */
    REVELATION,

    /** Les enjeux montent d'un cran sur ce qui est déjà en cours. */
    ESCALATION,

    /** On laisse retomber : intimité, silence, détail sensoriel. Une histoire sans creux n'a pas de
     *  relief — et une tension continue finit par être aussi monotone qu'une absence de tension. */
    QUIET,

    /** Un fil planté il y a longtemps ressurgit. */
    CALLBACK
}

/**
 * État narratif observable localement.
 *
 * @param turnsSinceComplication tours écoulés depuis le dernier beat de complication demandé
 * @param turnsSinceQuiet tours écoulés depuis la dernière accalmie demandée
 * @param oldestOpenThreadAgeTurns âge, en tours, du fil non résolu le plus ancien ; `null` si aucun
 * @param openThreadCount nombre de fils non résolus
 * @param recentTensionSignals nombre de fils narratifs plantés récemment — approximation de la
 *   tension en cours. Un fil ne se plante que quand quelque chose de dramatique arrive (menace,
 *   question ouverte, dette), donc une rafale de fils récents signale une scène chargée. C'est le
 *   seul indicateur de tension disponible **sans I/O supplémentaire** au point d'injection.
 * @param totalTurns longueur de la conversation, pour ne pas brusquer une histoire qui démarre
 */
data class NarrativeState(
    val turnsSinceComplication: Int = 0,
    val turnsSinceQuiet: Int = 0,
    val oldestOpenThreadAgeTurns: Int? = null,
    val openThreadCount: Int = 0,
    val recentTensionSignals: Int = 0,
    val totalTurns: Int = 0
)

/** Seuils du rythme. Regroupés pour être lisibles et ajustables d'un seul endroit. */
object NarrativeThresholds {
    /** En dessous, l'histoire s'installe : on ne dirige rien, la scène d'ouverture doit respirer. */
    const val WARMUP_TURNS = 6

    /** Un fil qui traîne au-delà devient une promesse non tenue, et le modèle l'a de toute façon
     *  perdu de vue. */
    const val STALE_THREAD_TURNS = 25

    /** Au-delà, l'échange ronronne : il faut un obstacle. */
    const val COMPLICATION_INTERVAL = 10

    /** Au-delà de ce nombre de fils récemment plantés, la scène est déjà sous tension. */
    const val HIGH_TENSION_SIGNALS = 3

    /** En deçà de cet âge, un fil compte comme « fraîchement planté » pour la mesure de tension. */
    const val RECENT_THREAD_TURNS = 6

    /** Une accalmie ne s'impose que si la précédente est loin — sinon l'histoire stagne. */
    const val MIN_TURNS_BETWEEN_QUIET = 8

    /** Décalage appliqué au compteur d'accalmie, pour que complication et accalmie ne tombent jamais
     *  sur le même tour — sinon la priorité en écraserait une, et l'un des deux beats ne jouerait
     *  jamais. Une valeur simplement première avec les deux intervalles suffit. */
    const val QUIET_PHASE = 5

    /** Séparateur entre le contrat de style et la directive de beat, qui partagent le même tour. */
    const val SECTION_BREAK = "\n\n"

    /** Trop de fils ouverts à la fois : on en referme un plutôt que d'en ouvrir un de plus. */
    const val THREAD_SATURATION = 4
}

/**
 * Décide du beat suivant.
 *
 * L'ordre des règles est l'ordre des priorités, et il est délibéré :
 * 1. une histoire qui commence n'est pas dirigée ;
 * 2. **retomber** d'abord si la tension est déjà haute — ajouter une complication sur une scène
 *    saturée produit du bruit, pas de la tension ;
 * 3. **payer** un fil trop ancien : c'est la dette narrative la plus visible pour le lecteur ;
 * 4. **compliquer** si l'échange ronronne ;
 * 5. **révéler** quand plusieurs fils sont ouverts — refermer plutôt qu'accumuler ;
 * 6. sinon **escalader** doucement.
 *
 * @return `null` quand il n'y a rien à imposer — l'absence de directive est un résultat valide, pas
 *         un défaut : mieux vaut ne rien dire que dire quelque chose de creux.
 */
fun decideBeat(state: NarrativeState): NarrativeBeat? {
    if (state.totalTurns < NarrativeThresholds.WARMUP_TURNS) return null

    val tensionIsHigh = state.recentTensionSignals >= NarrativeThresholds.HIGH_TENSION_SIGNALS
    val quietIsDue = state.turnsSinceQuiet >= NarrativeThresholds.MIN_TURNS_BETWEEN_QUIET
    if (tensionIsHigh && quietIsDue) return NarrativeBeat.QUIET

    val oldest = state.oldestOpenThreadAgeTurns
    if (oldest != null && oldest >= NarrativeThresholds.STALE_THREAD_TURNS) return NarrativeBeat.CALLBACK

    if (state.turnsSinceComplication >= NarrativeThresholds.COMPLICATION_INTERVAL) {
        return NarrativeBeat.COMPLICATION
    }

    if (state.openThreadCount >= NarrativeThresholds.THREAD_SATURATION) return NarrativeBeat.REVELATION

    return NarrativeBeat.ESCALATION
}

/**
 * Rend le beat en une consigne courte, à poser **en fin de contexte**.
 *
 * Le placement compte plus que le texte : c'est la leçon de la refonte du 2026-08-16
 * (FEATURES.md) — une consigne posée dans le prompt système, à des dizaines de messages du point de
 * génération, perd contre l'inertie de style du transcript. [buildStyleContract] est déjà injecté
 * là ; cette directive l'accompagne.
 *
 * Volontairement bref, et formulé comme une intention de scène et non comme un ordre mécanique :
 * une consigne trop directive fait écrire une scène qui *annonce* son beat au lieu de le jouer.
 */
fun buildBeatDirective(beat: NarrativeBeat?): String? = when (beat) {
    null -> null
    NarrativeBeat.COMPLICATION -> "Ce tour : introduis une complication concrète qui contrarie ce que " +
        "le personnage veut. Elle doit découler de la situation ou de son propre défaut, jamais tomber du ciel."
    NarrativeBeat.REVELATION -> "Ce tour : laisse une information cachée affleurer — pas forcément " +
        "dite, un geste ou un silence suffisent. Elle doit recadrer quelque chose que le joueur croyait acquis."
    NarrativeBeat.ESCALATION -> "Ce tour : monte d'un cran ce qui est déjà en jeu. Rends le prochain " +
        "choix du joueur plus coûteux qu'il ne l'était."
    NarrativeBeat.QUIET -> "Ce tour : laisse retomber. Pas d'événement neuf — un moment de proximité, " +
        "un détail sensoriel, une respiration. La tension doit reprendre son souffle, pas disparaître."
    NarrativeBeat.CALLBACK -> "Ce tour : fais ressurgir un fil laissé en suspens depuis longtemps, " +
        "sans le résoudre entièrement. Rappelle au joueur qu'il n'a pas été oublié."
}
