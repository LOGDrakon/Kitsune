package com.kitsune.core.moderation.filter

import com.kitsune.core.moderation.model.ModerationCategory
import com.kitsune.core.moderation.model.ModerationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight local prefilter (FEATURES.md section 3): a last-resort net for blatant, obvious
 * cases only. It is deliberately conservative — most checks require two term lists to co-occur
 * (e.g. an explicit term AND a minor-indicator term) rather than firing on a single word, to avoid
 * false-positives on legitimate adult dark-fiction content that this app explicitly allows.
 *
 * This is NOT the primary safeguard against minor content — the locked, mandatory 18+ persona age
 * field (see `PersonaCreationViewModel`) is. This filter exists to catch attempts to introduce
 * minor content through free-form chat text despite that structural guard. Ambiguous cases are
 * meant to go through a second layer of API-based classification (see IDEAS.md — not yet built).
 */
@Singleton
class LocalKeywordFilter @Inject constructor() {

    fun check(text: String): ModerationResult {
        val normalized = text.lowercase()

        standaloneUnambiguousTerms.forEach { (term, category) ->
            if (normalized.contains(term)) return ModerationResult.Flagged(category, "matched \"$term\"")
        }

        val hasExplicitSexualTerm = containsAny(normalized, explicitSexualTerms) ||
            explicitSexualStandaloneRegexes.any { it.containsMatchIn(normalized) }
        val hasMinorIndicator = containsAny(normalized, minorIndicatorTerms) ||
            minorIndicatorStandaloneRegexes.any { it.containsMatchIn(normalized) }
        if (hasExplicitSexualTerm && hasMinorIndicator) {
            return ModerationResult.Flagged(ModerationCategory.MINOR_CONTENT, "explicit content combined with a minor-age indicator")
        }

        if (containsAny(normalized, realWorldFramingTerms) && containsAny(normalized, illegalActivityTerms)) {
            return ModerationResult.Flagged(ModerationCategory.ILLEGAL_INSTRUCTIONS, "real-world framing combined with an illegal-activity term")
        }

        if (containsAny(normalized, realWorldFramingTerms) && containsAny(normalized, nonConsensualInstructionalTerms)) {
            return ModerationResult.Flagged(
                ModerationCategory.NON_CONSENSUAL_INSTRUCTIONAL,
                "real-world framing combined with a non-consensual instructional term"
            )
        }

        if (containsAny(normalized, hateIncitementTerms)) {
            return ModerationResult.Flagged(ModerationCategory.HATE_INCITEMENT, "hate incitement phrase")
        }

        return ModerationResult.Clean
    }

    private fun containsAny(text: String, terms: List<String>) = terms.any { text.contains(it) }

    private companion object {
        // Standalone terms unambiguous enough to flag on their own, regardless of context.
        val standaloneUnambiguousTerms: List<Pair<String, ModerationCategory>> = listOf(
            "child porn" to ModerationCategory.MINOR_CONTENT,
            "child sexual abuse" to ModerationCategory.MINOR_CONTENT,
            "csam" to ModerationCategory.MINOR_CONTENT,
            "loli porn" to ModerationCategory.MINOR_CONTENT,
            "pedophil" to ModerationCategory.MINOR_CONTENT,
            "pédophil" to ModerationCategory.MINOR_CONTENT
        )

        val explicitSexualTerms: List<String> = listOf(
            "sexe", "porn", "nude", "fuck", "orgasm", "masturbat"
        )

        val minorIndicatorTerms: List<String> = listOf(
            "kid ",
            "toddler", "prepubescent", "pré-pubère", "elementary school", "école primaire",
            "12yo", "13yo", "14yo", "15yo", "16yo", "17yo"
        )

        // Word-boundary regexes for single, otherwise false-positive-prone words — see BUGS.md
        // BUG-069 (server-side twin of this exact bug): "nue" (naked) is a raw substring of
        // extremely common French words ending in "-nue" ("inconnue" = stranger, "continue",
        // "tenue" = outfit, "revenue", "obtenue", "retenue"...); "enfant" (child) is a substring
        // of "enfantin(e)" (childish — a tone/behavior adjective, not a reference to an actual
        // child).
        // "sex" is the same problem: `text.contains("sex")` also matches "Sexual" — and this app's
        // own system-prompt template appends "Sexual orientation: ..." whenever the user has filled
        // in that profile field, resent every turn. Not directly exploitable client-side (this
        // filter only checks the text the user actually typed, not the system prompt), but fixed
        // here too for consistency with the server-side twin of this bug (BUG-075).
        // "baiser" is the same problem again — BUG-076: as a noun, "un baiser" just means "a kiss",
        // extremely common in even entirely PG romance scenes; as a verb it's vulgar slang for "to
        // fuck". Excludes the clearest noun-only determiners (see server-side twin's doc comment for
        // the full list/reasoning); "le baiser" is deliberately still matched (ambiguous with the
        // pronoun+verb reading).
        // "erection"/"érection" — proactive hardening pass, BUG-077: both English and French also
        // mean "erecting" a building/monument/statue, plausible in this app's period/fantasy
        // settings. Excludes the "erection of/erecting a" construction.
        // "au"/"du" (contractions of "à le"/"de le") added via BUG-078 (bug report, 2026-08-03) — a
        // confirmed false positive on "elle répond au baiser avec une intensité égale" (she responds to
        // the kiss). Unlike bare "le", "au"/"du" can never also be a direct-object pronoun before an
        // infinitive verb — French has no "au/du + infinitive" construction — so these two are exactly
        // as unambiguously noun-only as "un baiser" already excluded above.
        // "nue" has a second non-physical sense on top of the "-nue" word-ending collisions above —
        // BUG-079 (bug report, 2026-08-03): as an adjective it also modifies an abstract noun
        // metaphorically ("une intensité nue" = a raw/naked intensity, an unguarded emotion, not
        // physical nudity). Excludes the two most predictable such collocations ("intensité nue",
        // "vérité nue" — the common French idiom "the naked/bare truth"); not exhaustive.
        val explicitSexualStandaloneRegexes: List<Regex> = listOf(
            Regex(
                "(?<!\\b(?:intensité|vérité)\\s)\\bnue\\b",
                RegexOption.IGNORE_CASE
            ),
            Regex("\\bsex\\b", RegexOption.IGNORE_CASE),
            Regex(
                "(?<!\\b(?:un|au|du|ce|cet|cette|son|ton|mon|notre|votre|leur|premier|dernier|petit|doux|tendre|chaste)\\s)" +
                    "\\bbaisers?\\b",
                RegexOption.IGNORE_CASE
            ),
            Regex("\\berections?\\b(?!\\s+of\\b)", RegexOption.IGNORE_CASE),
            Regex("\\bérections?\\b(?!\\s+d['’]|\\s+de\\s)", RegexOption.IGNORE_CASE)
        )

        // "mineur"/"mineure" is a harder case than "nue"/"enfant" above — not a substring of an
        // unrelated word, but the SAME word used with unrelated meanings in French: an underage
        // person ("un mineur"), "small/lesser" ("un problème mineur"), an academic minor field of
        // study ("une mineure en philosophie" — a real university term), or a musical key ("en
        // mineur"). Confirmed false positive (bug report, 2026-08-02): a university-set scene
        // involving only adults was hard-blocked purely for containing "mineur"/"mineure" in one of
        // these unrelated senses. Restricted to the two constructions that overwhelmingly mean "an
        // underage person" in natural French — a noun phrase ("un/cette/des mineur(e)(s)") or a form
        // of "être" used as a predicate ("elle EST mineure") — excluding when immediately followed
        // by "en"/"de" (the academic-minor construction). Deliberately still lets through the rarer
        // "ce problème EST mineur" (severity via être, far less common word order than "un problème
        // mineur"): "elle est mineure" is how this app's dark-fiction users would actually state a
        // character's age in dialogue, and missing that matters far more than an occasional false
        // positive on this rarer phrasing.
        // "minor" (English) has the exact same problem "mineur" (French) has above — see BUG-075
        // (server-side twin of this bug): the app's own ensemble/universe system-prompt template
        // says "brief, minor unnamed background characters", which a bare `contains("minor")`
        // matched unconditionally. Not directly exploitable client-side (same reason as "sex"
        // above), fixed here too for consistency. Restricted to "minor(s)" directly preceded by a
        // determiner/possessive and not immediately followed by a common non-person noun ("a minor
        // issue/character/..."), same trade-off reasoning as the server-side fix.
        // "enfant" has the exact same simile problem as "gamin"/"gamine" below — BUG-079 (bug
        // report, 2026-08-03): "comme une enfant" ("like a child") describing an adult character's
        // carefree/innocent demeanor, zero reference to an actual minor. Confirmed false positive
        // co-occurring with "nue" used metaphorically ("une intensité nue") in the very same AI
        // message (see [explicitSexualStandaloneRegexes]'s own BUG-079 entry). Excludes the simile
        // construction, mirroring gamin/gamine exactly.
        val minorIndicatorStandaloneRegexes: List<Regex> = listOf(
            Regex("(?<!\\bcomme\\s(?:un|une)\\s)\\benfants?\\b", RegexOption.IGNORE_CASE),
            Regex(
                "\\b(?:un|une|le|la|les|des|ce|cette|ces|est|es|suis|sommes|êtes|sont|était|étais|étions|étiez|étaient|soit|soient)" +
                    "\\s+mineure?s?\\b(?!\\s+(?:en|de)\\b)",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "\\b(?:a|an|the|this|that|these|those|her|his|their|my|your)\\s+minors?\\b" +
                    "(?!\\s+(?:issue|issues|problem|problems|character|characters|role|roles|detail|details|" +
                    "point|points|thing|things|inconvenience|inconveniences|setback|setbacks|complaint|" +
                    "complaints|delay|delays|change|changes|difference|differences|version|versions|key|" +
                    "injury|injuries|wound|wounds|distraction|distractions|annoyance|annoyances|unnamed|" +
                    "background)\\b)",
                RegexOption.IGNORE_CASE
            ),
            // "gamin"/"gamine" (kid) has the same problem — BUG-076: "comme des gamin(e)s" ("like
            // children") is a common simile for immature/petty behavior between adults, with zero
            // reference to an actual minor. Excludes exactly that comparison construction.
            Regex("(?<!\\bcomme\\s(?:des|un)\\s)\\bgamins?\\b", RegexOption.IGNORE_CASE),
            Regex("(?<!\\bcomme\\s(?:des|une)\\s)\\bgamines?\\b", RegexOption.IGNORE_CASE),
            // BUG-077 (proactive audit): "child" is the exact English twin of "enfant"/"enfantin"
            // above but was never given the same word-boundary treatment — bare `contains("child")`
            // also matched "childish", with zero relation to an actual minor.
            Regex("\\bchild(ren)?\\b", RegexOption.IGNORE_CASE),
            // Age phrases collide with "il y a X ans" ("X years ago") / "ça fait X ans que" — common
            // French backstory framing with zero relation to anyone's age.
            Regex(
                "(?<!\\b(?:il y a|ça fait|cela fait|depuis|voilà)\\s)\\b1[2-7]\\s?ans\\b",
                RegexOption.IGNORE_CASE
            ),
            // Widened to also catch the far more common hyphenated "17-year-old" form (previously
            // only "17 year old" matched), which in turn opens up "a 17-year-old tradition/rivalry" —
            // excluded here the same way as "minor"/"mineur" above.
            Regex(
                "\\b1[2-7][\\s-]?years?[\\s-]?old\\b" +
                    "(?!\\s+(?:car|house|building|tree|business|company|bottle|tradition|rivalry|friendship|" +
                    "feud|mystery|secret|promise|grudge|curse|prophecy|ritual|photo|photograph|letter|case|" +
                    "scandal)\\b)",
                RegexOption.IGNORE_CASE
            )
        )

        // Deliberately excludes generic words like "réellement"/"for real"/"actually do" and secrecy
        // phrases like "sans qu'elle le sache" ("without her knowing") — both are everyday
        // French/English words that show up constantly in ordinary narrative fiction (a hidden
        // feeling, a secret gift, a surprise) with no real-world-instruction-seeking intent at all,
        // and were causing this filter to false-positive on unrelated DARK-tagged roleplay (BUGS.md).
        // Only phrases that are themselves a fairly unambiguous signal remain.
        val realWorldFramingTerms: List<String> = listOf(
            "in real life", "dans la vraie vie", "irl",
            "step by step guide", "guide étape par étape", "how do i really", "comment faire réellement"
        )

        val illegalActivityTerms: List<String> = listOf(
            "make a bomb", "fabriquer une bombe", "synthesize meth", "synthétiser de la méthamphétamine",
            "hotwire a car", "voler une voiture", "how to hack into", "pirater le compte de"
        )

        val nonConsensualInstructionalTerms: List<String> = listOf(
            "how to drug someone", "comment droguer quelqu'un", "how to assault", "comment agresser"
        )

        val hateIncitementTerms: List<String> = listOf(
            "kill all ", "tuez tous les", "exterminate all", "exterminer tous les"
        )
    }
}
