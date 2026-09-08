package nl.part66l.logbook.domain

/**
 * A CRS numbering template (§9.2). Placeholders: `{PREFIX}`, `{YYYY}` (four
 * digits), `{REG}` (the aircraft registration, or `NOREG` for component/bench
 * work — always pre-normalised to alphanumeric-only by the caller) and
 * `{SEQ:N}` (zero-padded to N digits) — each usable at most once, with
 * arbitrary literal text around and between them. `{SEQ:N}` is required
 * exactly once and must be the last placeholder: nothing else may vary after
 * it, or "the highest issued number" stops being well-defined.
 *
 * The sequence is shared across every registration and NOREG alike — `{REG}`
 * varies the printed number, not the counter. A number-matching pass therefore
 * treats `{REG}` as a wildcard, the same way `{YYYY}` is treated when
 * [annualReset] is off.
 *
 * Pure and stateless — it only computes what the next number *would* be from
 * what has already been issued. Nothing is reserved or persisted here.
 * Invariant #2 (numbers allocated at signing, never at draft creation) is a
 * caller responsibility: call [nextNumber] at the moment of signing, not when
 * a draft is created, so an abandoned draft leaves no gap.
 */
class CrsNumberFormat(
    val template: String,
    val prefix: String,
    val annualReset: Boolean,
    val startAt: Int = 1,
) {
    private val tokens: List<Token> = tokenize(template)
    private val hasYear: Boolean = tokens.any { it is Token.Year }
    private val hasRegistration: Boolean = tokens.any { it is Token.Registration }
    private val sequenceWidth: Int

    init {
        require(startAt >= 1) { "startAt must be at least 1, was $startAt" }
        val seqIndex = tokens.indexOfFirst { it is Token.Sequence }
        require(seqIndex >= 0 && tokens.count { it is Token.Sequence } == 1) {
            "Template must contain exactly one {SEQ:N} placeholder: $template"
        }
        require(tokens.drop(seqIndex + 1).none { it !is Token.Literal }) {
            "{SEQ:N} must be the last placeholder in the template: $template"
        }
        sequenceWidth = (tokens[seqIndex] as Token.Sequence).width
    }

    /**
     * Renders a concrete number. [year] is required when the template contains
     * `{YYYY}`; [registration] is required when it contains `{REG}` — already
     * resolved by the caller to the normalised registration or `NOREG`.
     */
    fun format(sequence: Int, year: Int? = null, registration: String? = null): String {
        require(!hasYear || year != null) { "Template requires a year: $template" }
        require(!hasRegistration || registration != null) { "Template requires a registration: $template" }
        require(sequence >= 0 && sequence.toString().length <= sequenceWidth) {
            "Sequence $sequence does not fit {SEQ:$sequenceWidth} in $template"
        }
        return tokens.joinToString("") { token ->
            when (token) {
                is Token.Literal -> token.text
                is Token.Prefix -> prefix
                is Token.Year -> year!!.toString().padStart(4, '0')
                is Token.Registration -> registration!!
                is Token.Sequence -> sequence.toString().padStart(token.width, '0')
            }
        }
    }

    /**
     * The next number to allocate, given every number issued so far under any
     * format. Only numbers this format could itself have produced are counted
     * — everything else (imported historical numbers, or numbers from a
     * since-changed format) is ignored rather than misread, per §11: imported
     * numbers "never enter the live sequence".
     *
     * With [annualReset], only numbers matching [year] count, so a new year
     * starts back at [startAt]. Without it, numbers from every year count, so
     * the sequence keeps climbing across year boundaries even though `{YYYY}`
     * may still appear in the printed number.
     */
    fun nextNumber(existingNumbers: Collection<String>, year: Int? = null, registration: String? = null): String =
        format(nextSequence(existingNumbers, year), year, registration)

    /** The bare sequence integer [nextNumber] would format — what [CrsEntity.sequence] stores alongside the printed number. */
    fun nextSequence(existingNumbers: Collection<String>, year: Int? = null): Int {
        require(!hasYear || year != null) { "Template requires a year: $template" }
        val matcher = matcherFor(pinnedYear = if (annualReset) year else null)
        val highest = existingNumbers
            .mapNotNull { matcher.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()
        return maxOf(startAt, (highest ?: 0) + 1)
    }

    /** The §9.2 collision check required before accepting a changed format. */
    fun collidesWith(candidate: String, existingNumbers: Collection<String>): Boolean =
        candidate in existingNumbers

    /** Matches only numbers this exact format could have produced. Null [pinnedYear] means any year. */
    private fun matcherFor(pinnedYear: Int?): Regex {
        val pattern = tokens.joinToString("") { token ->
            when (token) {
                is Token.Literal -> Regex.escape(token.text)
                is Token.Prefix -> Regex.escape(prefix)
                is Token.Year -> if (pinnedYear != null) {
                    Regex.escape(pinnedYear.toString().padStart(4, '0'))
                } else {
                    "\\d{4}"
                }
                is Token.Registration -> "[A-Za-z0-9]+"
                is Token.Sequence -> "(\\d{${token.width}})"
            }
        }
        return Regex("^$pattern$")
    }

    private sealed interface Token {
        data class Literal(val text: String) : Token
        data object Prefix : Token
        data object Year : Token
        data object Registration : Token
        data class Sequence(val width: Int) : Token
    }

    companion object {
        private val TOKEN_PATTERN = Regex("\\{PREFIX\\}|\\{YYYY\\}|\\{REG\\}|\\{SEQ:(\\d+)\\}")

        private fun tokenize(template: String): List<Token> {
            val tokens = mutableListOf<Token>()
            var last = 0
            for (match in TOKEN_PATTERN.findAll(template)) {
                if (match.range.first > last) tokens += Token.Literal(template.substring(last, match.range.first))
                tokens += when (match.value) {
                    "{PREFIX}" -> Token.Prefix
                    "{YYYY}" -> Token.Year
                    "{REG}" -> Token.Registration
                    else -> Token.Sequence(match.groupValues[1].toInt())
                }
                last = match.range.last + 1
            }
            if (last < template.length) tokens += Token.Literal(template.substring(last))
            return tokens
        }
    }
}
