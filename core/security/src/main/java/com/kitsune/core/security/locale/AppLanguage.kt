package com.kitsune.core.security.locale

/** Languages Kitsune ships translated string resources for (`values-xx/strings.xml`). */
enum class AppLanguage(val languageTag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español"),
    ARABIC("ar", "العربية"),
    HINDI("hi", "हिन्दी"),
    ITALIAN("it", "Italiano"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    DUTCH("nl", "Nederlands"),
    POLISH("pl", "Polski"),
    PORTUGUESE("pt", "Português"),
    RUSSIAN("ru", "Русский"),
    TURKISH("tr", "Türkçe"),
    CHINESE("zh", "中文");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.find { it.languageTag == tag }
    }
}
