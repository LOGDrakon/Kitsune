package com.kitsune.core.common.style

/**
 * Shared max length for persona/universe names — enforced both at the source (AI generation,
 * manual input) and defensively at display time (ellipsis), so no name can ever break layouts
 * like the chat top bar (avatar + name + Standard/Pro toggle sharing one line).
 */
const val MAX_NAME_LENGTH = 40
