package com.kitsune.core.common.style

data class PersonaTemplate(
    val id: String,
    val name: String,
    val description: String,
    val styleHint: String
)

/** Shared visual-style presets, reused by persona creation (`feature:persona`) and image generation (`feature:chat`). */
object PersonaTemplates {
    val all = listOf(
        PersonaTemplate(
            id = "anime",
            name = "Animé",
            description = "Style manga/anime japonais",
            styleHint = "Create a character in anime/manga style with expressive features, vibrant colors, and stylized proportions"
        ),
        PersonaTemplate(
            id = "3d",
            name = "3D Réaliste",
            description = "Rendu 3D photoréaliste",
            styleHint = "Create a character in realistic 3D rendered style, like a high-quality video game or CGI movie character"
        ),
        PersonaTemplate(
            id = "cyberpunk",
            name = "Cyberpunk",
            description = "Futuriste dystopique, néons, tech",
            styleHint = "Create a character in a cyberpunk setting with neon lights, advanced technology, dystopian aesthetic, and futuristic fashion"
        ),
        PersonaTemplate(
            id = "realistic",
            name = "Réaliste",
            description = "Style photographique naturel",
            styleHint = "Create a character in a realistic, photographic style with natural proportions and lifelike details"
        ),
        PersonaTemplate(
            id = "fantasy",
            name = "Fantasy",
            description = "Médiéval fantastique, magie",
            styleHint = "Create a character in a high fantasy setting with magical elements, medieval-inspired clothing, and mythical atmosphere"
        ),
        PersonaTemplate(
            id = "dark_fantasy",
            name = "Dark Fantasy",
            description = "Fantasy sombre, gothique",
            styleHint = "Create a character in a dark fantasy setting with gothic elements, mysterious atmosphere, and dramatic lighting"
        ),
        PersonaTemplate(
            id = "scifi",
            name = "Science-Fiction",
            description = "Spatial, futuriste propre",
            styleHint = "Create a character in a clean science fiction setting with advanced technology, space age aesthetic, and futuristic design"
        ),
        PersonaTemplate(
            id = "steampunk",
            name = "Steampunk",
            description = "Victorien, vapeur, engrenages",
            styleHint = "Create a character in a steampunk setting with Victorian-era aesthetics, steam-powered technology, brass and copper elements"
        ),
        PersonaTemplate(
            id = "noir",
            name = "Noir",
            description = "Années 40-50, détective, ombres",
            styleHint = "Create a character in a film noir style with 1940s-50s aesthetic, dramatic shadows, mystery atmosphere, and classic detective vibe"
        ),
        PersonaTemplate(
            id = "cartoon",
            name = "Cartoon",
            description = "Style dessin animé occidental",
            styleHint = "Create a character in a Western cartoon style with exaggerated features, bold outlines, and vibrant colors"
        )
    )

    fun getById(id: String): PersonaTemplate? = all.find { it.id == id }
}
