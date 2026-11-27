package com.mlbb.plus.tts

/**
 * Turns the on-screen guide text into something that sounds right when spoken.
 *
 * The screen keeps the full paragraph (6 lines); the voice gets a condensed
 * version of 8-18 words. Rules:
 *  - MLBB abbreviations are expanded on SPEAK ONLY ("Retri" -> "Retribution",
 *    "EXP" -> "E X P lane", "Flicker" stays "Flicker"). The visible text and the
 *    JSON assets are never modified.
 *  - Markdown, bullet glyphs, brackets, URLs, emoji and snake_case / camelCase
 *    identifiers are stripped, so a raw JSON field name can never be read aloud.
 *  - Clock values ("2:00") become words ("two minutes") because both engines
 *    read a bare "2:00" as digits. Time ranges ("0:00-0:40") are dropped: the
 *    manager prefixes the spoken line with [VoiceGuideManager.spokenTime] instead.
 *  - Long text is trimmed at a sentence or clause boundary, never mid-word, and
 *    never leaving a dangling "the" / "and" / "to".
 *
 * Pure functions only: no Android imports, so this is unit-testable on the JVM.
 */
object TtsTextNormalizer {

    /** Spec: keep spoken output at about 8-18 words. */
    const val MIN_WORDS = 8
    const val MAX_WORDS = 18

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Composes the spoken line from ordered fragments (highest priority first).
     * Whole fragments are kept while the total stays inside [maxWords]; the last
     * fragment that does not fit is condensed instead of dropped, so a step that
     * only has a long `instruction` still gets spoken.
     *
     * Example: `spoken(listOf("Setup", "Guard buff, don't feed", "Stand near the
     * buff pit and watch the enemy jungler."))` -> "Setup. Guard buff, don't
     * feed." plus as much of the third fragment as fits.
     */
    fun spoken(fragments: List<String>, maxWords: Int = MAX_WORDS): String {
        val cleaned = fragments
            .map { condense(it, maxWords) }
            .filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return ""

        val out = StringBuilder()
        var used = 0
        for ((index, fragment) in cleaned.withIndex()) {
            val words = countWords(fragment)
            val isLast = index == cleaned.lastIndex
            if (used + words <= maxWords) {
                appendFragment(out, fragment)
                used += words
                continue
            }
            // Does not fit whole: only condense further for the final fragment,
            // otherwise we would chop a secondary detail and lose the headline.
            if (isLast && used < maxWords) {
                val room = maxWords - used
                val trimmed = condense(fragment, room)
                if (countWords(trimmed) >= MIN_SHORT_TAIL) appendFragment(out, trimmed)
            }
            break
        }
        if (out.isEmpty()) {
            // Everything overflowed on the first fragment: force a hard fit.
            appendFragment(out, condense(cleaned.first(), maxWords))
        }
        return out.toString().trim()
    }

    /** Convenience overload for a single block of text. */
    fun spoken(text: String, maxWords: Int = MAX_WORDS): String = spoken(listOf(text), maxWords)

    /**
     * Markdown strip + abbreviation expansion + symbol and clock conversion.
     * Idempotent: `clean(clean(x)) == clean(x)`.
     */
    fun clean(raw: String): String {
        var text = raw

        // 1. Raw field names FIRST: they have to be gone before punctuation
        //    folding can disguise them ("objective_contest" would otherwise turn
        //    into two ordinary words and get read aloud).
        text = SNAKE_CASE_REGEX.replace(text, " ")
        text = CAMEL_CASE_REGEX.replace(text, " ")

        // 2. Structural noise: URLs, code spans, images, links, bullets.
        text = URL_REGEX.replace(text, " ")
        for (bullet in MARKDOWN_BULLETS) text = text.replace(bullet, ' ')
        text = IMAGE_REGEX.replace(text, " $2 ")
        text = LINK_REGEX.replace(text, " $1 ")
        text = text.replace("`", " ").replace("*", " ").replace("_", " ")
        text = HEADING_REGEX.replace(text, " ")
        text = BRACKET_REGEX.replace(text, " ")
        text = text.replace("<br>", " ").replace("<b>", " ").replace("</b>", " ")

        // 3. Symbols -> words (before numbers, so "2:00" is caught as a clock).
        text = TIME_RANGE_REGEX.replace(text, " ")
        // Ordinal and numeric ranges: "1st-2nd" -> "1st to 2nd", "20-30s" ->
        // "twenty to thirty seconds". Both must run before the plain range rule,
        // whose trailing \b cannot match the "s" of "20-30s".
        text = ORDINAL_RANGE_REGEX.replace(text, "$1 to $2")
        text = RANGE_SECONDS_REGEX.replace(text) {
            numberWord(it.groupValues[1]) + " to " + numberWord(it.groupValues[2]) + " seconds"
        }
        text = NUM_RANGE_REGEX.replace(text, "$1 to $2")
        text = CLOCK_REGEX.replace(text) { spokenClock(it.groupValues[1], it.groupValues[2]) }
        text = DURATION_REGEX.replace(text) { numberWord(it.groupValues[1]) + " seconds" }
        text = VERSUS_SCORE_REGEX.replace(text) { it.groupValues[1] + " versus " + it.groupValues[2] }
        for ((symbol, replacement) in SYMBOLS) text = text.replace(symbol, replacement)

        // 4. Abbreviations (word-boundary, longest key first).
        text = expandAbbreviations(text)

        // 5. Whitespace + stray punctuation.
        text = text.replace(EMOJI_REGEX, " ")
        text = WHITESPACE_REGEX.replace(text, " ")
        text = DOUBLE_PUNCT_REGEX.replace(text, "$1")
        text = text.replace(" ,", ",").replace(" .", ".").replace(" ;", ";")
        return text.trim()
    }

    /**
     * Dictionary pass only. Exposed for tests and for the "announce enemy spells"
     * feature, which reuses the same vocabulary without re-composing a line.
     */
    fun expandAbbreviations(text: String): String {
        var out = text
        // "EXP lane" must not become "E X P lane lane".
        out = EXP_LANE_REGEX.replace(out, "EXP")
        for ((pattern, replacement) in ABBREVIATIONS) {
            out = pattern.replace(out, replacement)
        }
        return out
    }

    /**
     * Shortens [text] to at most [maxWords] words, cutting at a sentence or
     * clause boundary. Falls back to a word boundary with trailing function
     * words removed when even the first clause is too long.
     */
    fun condense(text: String, maxWords: Int = MAX_WORDS): String {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return ""
        if (countWords(cleaned) <= maxWords) return cleaned

        // Whole sentences first: the opening sentence of a guide step carries the
        // directive ("Rotate to Gold lane when...").
        val sentences = sentences(cleaned)
        val builder = StringBuilder()
        var used = 0
        for (sentence in sentences) {
            val words = countWords(sentence)
            if (used + words <= maxWords) {
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(sentence)
                used += words
            } else {
                break
            }
        }
        if (used > 0) return builder.toString().trim()

        // First sentence alone is too long -> cut at the last clause that fits.
        val first = sentences.first()
        val clause = cutAtClause(first, maxWords)
        if (clause.isNotEmpty()) return clause

        // No usable boundary -> hard cut on a word edge, dropping dangling
        // connectors so the line still ends cleanly.
        return trimToWords(first, maxWords)
    }

    /** "Turtle in 2:00" style countdown -> "2:00"; used for widget copy, not speech. */
    fun spokenTime(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            minutes == 0 && rest == 0 -> "now"
            minutes == 0 -> if (rest == 1) "1 second" else "$rest seconds"
            rest == 0 -> if (minutes == 1) "1 minute" else "$minutes minutes"
            minutes == 1 -> "1 minute $rest seconds"
            else -> "$minutes minutes $rest seconds"
        }
    }

    /** "2:00" -> "two minutes", "0:30" -> "thirty seconds", "1:30" -> "one minute thirty seconds". */
    fun spokenClock(minutes: String, seconds: String): String {
        val m = minutes.toIntOrNull() ?: 0
        val s = seconds.toIntOrNull() ?: 0
        val minutePart = when {
            m == 0 -> ""
            m == 1 -> "one minute"
            else -> "${numberWord(m.toString())} minutes"
        }
        val secondPart = when {
            s == 0 -> ""
            s == 1 -> "one second"
            else -> "${numberWord(s.toString())} seconds"
        }
        return listOf(minutePart, secondPart).filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { "zero seconds" }
    }

    /** Sentence split that keeps the terminal punctuation and ignores "2.2.23". */
    fun sentences(text: String): List<String> = text.split(SENTENCE_SPLIT_REGEX)
        .map { it.trim() }
        .filter { it.isNotEmpty() && countWords(it) > 0 }

    /** Word count that ignores pure punctuation. */
    fun countWords(text: String): Int = text.split(WHITESPACE_REGEX).count { WORD_REGEX.matches(it) }

    // ---------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------

    /** Below this, a condensed tail is noise ("Also.") and gets dropped. */
    private const val MIN_SHORT_TAIL = 3

    private fun appendFragment(out: StringBuilder, fragment: String) {
        if (out.isEmpty()) {
            out.append(fragment)
            return
        }
        if (!out.endsWith(".") && !out.endsWith("!") && !out.endsWith("?")) out.append('.')
        out.append(' ').append(fragment)
    }

    /** Cut at the last comma / semicolon / dash boundary that still fits. */
    private fun cutAtClause(sentence: String, maxWords: Int): String {
        val parts = sentence.split(CLAUSE_SPLIT_REGEX)
        val builder = StringBuilder()
        var used = 0
        var matched = false
        for (part in parts) {
            val words = countWords(part)
            if (used + words > maxWords) break
            if (builder.isNotEmpty()) builder.append(',')
            builder.append(part.trim())
            used += words
            matched = true
        }
        if (!matched || parts.size < 2) return ""
        val result = builder.toString().trim().trimEnd(',', ';')
        // A single clause that equals the whole sentence is not a real cut.
        if (countWords(result) >= countWords(sentence)) return ""
        return if (result.isEmpty()) "" else "$result."
    }

    private fun trimToWords(sentence: String, maxWords: Int): String {
        val words = sentence.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        val kept = words.take(maxWords).toMutableList()
        while (kept.isNotEmpty() && kept.last().trimEnd('.', ',', ';', ':').lowercase() in TRAILING_STOP_WORDS) {
            kept.removeAt(kept.lastIndex)
        }
        if (kept.isEmpty()) return ""
        val last = kept.removeAt(kept.lastIndex).trimEnd('.', ',', ';', ':', '-')
        kept.add(last)
        return kept.joinToString(" ").trimEnd('.', ',', ';', ':') + "."
    }

    /** Digits -> words for values the engines read poorly (clocks, "120s"). */
    private fun numberWord(value: String): String {
        val n = value.toIntOrNull() ?: return value
        return if (n in SMALL_NUMBERS.indices) SMALL_NUMBERS[n] else n.toString()
    }

    // ---------------------------------------------------------------
    // Vocabulary
    // ---------------------------------------------------------------

    private val SMALL_NUMBERS = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen", "twenty", "twenty-one", "twenty-two",
        "twenty-three", "twenty-four", "twenty-five", "twenty-six", "twenty-seven",
        "twenty-eight", "twenty-nine", "thirty", "thirty-one", "thirty-two",
        "thirty-three", "thirty-four", "thirty-five", "thirty-six", "thirty-seven",
        "thirty-eight", "thirty-nine", "forty", "forty-one", "forty-two", "forty-three",
        "forty-four", "forty-five", "forty-six", "forty-seven", "forty-eight",
        "forty-nine", "fifty", "fifty-one", "fifty-two", "fifty-three", "fifty-four",
        "fifty-five", "fifty-six", "fifty-seven", "fifty-eight", "fifty-nine"
    )

    private val TRAILING_STOP_WORDS = setOf(
        "a", "an", "the", "and", "or", "but", "of", "to", "in", "on", "for", "with",
        "at", "by", "as", "into", "when", "while", "if", "then", "so"
    )

    /**
     * Abbreviation dictionary. Longest / most specific patterns first: "EXP lane"
     * is collapsed before bare "EXP" is expanded, and the role lookaheads stop
     * "Gold advantage" or "mid game" from being rewritten into nonsense.
     */
    private val ABBREVIATIONS: List<Pair<Regex, String>> = listOf(
        Regex("\\bRetri\\b", RegexOption.IGNORE_CASE) to "Retribution",
        Regex("\\bRetrib\\b", RegexOption.IGNORE_CASE) to "Retribution",
        Regex("\\bEXP\\b") to "E X P lane",
        // Lane names are CASE SENSITIVE on purpose. The real guide JSON uses
        // capital "Gold" / "Mid" for the lane and lowercase "gold" for currency
        // ("Gold lane gives extra gold", "take tower gold"), and lowercase "mid"
        // for the mid laner ("Help mid clear"). Expanding the lowercase forms
        // produced "extra gold lane". The lookaheads are case-insensitive even
        // though the match is not, so an already-qualified name stays intact:
        // "Gold tower", "Mid Game", "mid priority".
        Regex("\\bGold\\b(?![\\s-]*(?i:lane|tower|river|bush|pit|angle|side|advantage|buff|item|items|diff|lead|gap))") to "gold lane",
        Regex("\\bMid\\b(?![\\s-]*(?i:lane|tower|river|bush|pit|angle|side|game|fight|team|jungle|gold|tier|priority|late|early|push))") to "mid lane",
        Regex("\\bLv\\.?\\s*(\\d+)") to "level $1",
        Regex("\\bLv\\.?\\b") to "level",
        Regex("\\bDPS\\b") to "damage dealer",
        Regex("\\bJG\\b", RegexOption.IGNORE_CASE) to "jungle",
        Regex("\\bJung\\b", RegexOption.IGNORE_CASE) to "jungle",
        Regex("\\bMM\\b") to "marksman",
        Regex("\\bCC\\b") to "crowd control",
        Regex("\\bKB\\b") to "knowledge base",
        Regex("\\bMPL\\b") to "M P L",
        Regex("\\bAoE\\b", RegexOption.IGNORE_CASE) to "area",
        Regex("\\bHP\\b") to "H P",
        Regex("\\bMP\\b") to "mana",
        Regex("\\bCD\\b") to "cooldown",
        Regex("\\bCDs\\b") to "cooldowns",
        Regex("\\bMS\\b") to "movement speed",
        Regex("\\bAS\\b") to "attack speed",
        Regex("\\bATK\\b", RegexOption.IGNORE_CASE) to "attack",
        Regex("\\bDEF\\b", RegexOption.IGNORE_CASE) to "defense",
        Regex("\\bAA\\b") to "auto attack",
        Regex("\\bQ\\b(?=\\s*(?:skill|ability))") to "first skill",
        Regex("\\bvs\\.?\\b", RegexOption.IGNORE_CASE) to "versus",
        Regex("\\be\\.g\\.?,?", RegexOption.IGNORE_CASE) to "for example",
        Regex("\\bi\\.e\\.?,?", RegexOption.IGNORE_CASE) to "that is",
        Regex("\\betc\\.?", RegexOption.IGNORE_CASE) to " "
    )

    /** Symbol table. Order matters: multi-char symbols before single-char. */
    private val SYMBOLS: List<Pair<String, String>> = listOf(
        // Order matters: "<->" contains both "<-" and "->", so the longest form
        // has to be folded first ("Rotate mid <-> Gold" -> "Rotate mid and Gold").
        "<->" to " and ",
        "<-->" to " and ",
        "<-" to " from ",
        "->" to " then ",
        "-->" to " then ",
        "\u2192" to " then ",
        "\u21d2" to " then ",
        "\u2265" to " at least ",
        "\u2264" to " at most ",
        "\u00d7" to " times ",
        "\u2013" to " to ",
        "\u2014" to ", ",
        "\u2022" to ", ",
        "\u00b7" to ", ",
        "\u2713" to " ",
        "\u2717" to " ",
        "\u26a0" to " ",
        " & " to " and ",
        "%" to " percent",
        "+" to " plus ",
        "~" to "about ",
        "/" to " or ",
        ":" to ", ",
        ";" to ", ",
        "\"" to " ",
        // Contractions must survive: "don't feed" spoken as "don t feed" sounds
        // broken. Fold the curly apostrophe onto the straight one instead of
        // deleting it.
        "\u2019" to "'",
        "\u2018" to "'",
        "\u201c" to " ",
        "\u201d" to " "
    )

    private val URL_REGEX = Regex("https?://\\S+|www\\.\\S+")
    private val IMAGE_REGEX = Regex("!\\[([^]]*)]\\(([^)]*)\\)")
    private val LINK_REGEX = Regex("\\[([^]]*)]\\([^)]*\\)")
    private val HEADING_REGEX = Regex("(?m)^#{1,6}\\s*")
    private val BRACKET_REGEX = Regex("[\\[(){}<>|]")
    private val SNAKE_CASE_REGEX = Regex("\\b[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)+\\b")
    private val CAMEL_CASE_REGEX = Regex("\\b[a-z]+(?:[A-Z][a-z]*)+\\b")
    private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cs}\\p{Cn}]")
    private val TIME_RANGE_REGEX = Regex("\\d{1,2}:\\d{2}\\s*[-\u2013\u2014]\\s*\\d{1,2}:\\d{2}")
    private val ORDINAL_RANGE_REGEX =
        Regex("\\b(\\d{1,2}(?:st|nd|rd|th))\\s*[-\u2013]\\s*(\\d{1,2}(?:st|nd|rd|th))\\b", RegexOption.IGNORE_CASE)
    private val RANGE_SECONDS_REGEX = Regex("\\b(\\d{1,4})\\s*[-\u2013]\\s*(\\d{1,4})\\s*s\\b")
    private val NUM_RANGE_REGEX = Regex("\\b(\\d{1,4})\\s*[-\u2013]\\s*(\\d{1,4})\\b")
    private val CLOCK_REGEX = Regex("\\b(\\d{1,3}):(\\d{2})\\b")
    private val DURATION_REGEX = Regex("\\b(\\d{1,4})\\s*s\\b")
    private val VERSUS_SCORE_REGEX = Regex("\\b(\\d)\\s*[vV]\\s*(\\d)\\b")
    private val EXP_LANE_REGEX = Regex("\\bEXP\\s+lane\\b", RegexOption.IGNORE_CASE)
    private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?])\\s+(?=[A-Z\u201c])")
    private val CLAUSE_SPLIT_REGEX = Regex("[,;]|\\s+\\u2013\\s+|\\s+-\\s+")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DOUBLE_PUNCT_REGEX = Regex("([.,!?]){2,}")
    private val WORD_REGEX = Regex(".*[A-Za-z0-9].*")
    private const val MARKDOWN_BULLETS = "\u2022\u25cf\u25aa\u2043\u00b7"
}
