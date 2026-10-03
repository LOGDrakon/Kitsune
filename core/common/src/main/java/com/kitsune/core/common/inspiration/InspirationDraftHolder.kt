package com.kitsune.core.common.inspiration

/**
 * Hands the free-text description synthesized by the "Je ne sais pas quoi créer" wizard (`app`
 * module) over to the persona/universe creation screen it navigates into next (`feature:persona`/
 * `feature:universe`), without threading a potentially long, unencoded text blob through a
 * Navigation-Compose route argument — mirrors [com.kitsune.core.common.memory.MemorySettingsHolder]'s
 * pattern of a small `@Volatile`-backed singleton for cross-module ephemeral state.
 *
 * [consume] reads and clears atomically so only the specific navigation right after the wizard
 * observes a non-null value — every other way of reaching persona/universe creation (FAB "Décrire
 * moi-même", NPC conversion, universe "+", draft review…) sees null, same as before this existed.
 */
object InspirationDraftHolder {
    @Volatile private var pendingDescription: String? = null

    fun set(description: String) {
        pendingDescription = description
    }

    fun consume(): String? {
        val description = pendingDescription
        pendingDescription = null
        return description
    }
}
