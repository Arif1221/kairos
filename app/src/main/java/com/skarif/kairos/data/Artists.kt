package com.skarif.kairos.data

/**
 * Splits an artist tag into the individual artists, so "Akon feat. Eminem" counts for both.
 * Splits on , ; / & and the usual "feat." forms. Band names that contain "&" (Simon & Garfunkel)
 * are split too; that is the price of catching the far more common "A & B" collaborations.
 */
private val SEPARATORS = Regex(
    """\s*(?:[,;/]|\s&\s|\s+(?:feat\.?|ft\.?|featuring|with|x|vs\.?)\s+)\s*""",
    RegexOption.IGNORE_CASE,
)

fun splitArtists(raw: String): List<String> {
    val parts = raw.split(SEPARATORS).map { it.trim().trim('(', ')', '[', ']').trim() }.filter { it.isNotEmpty() }
    return parts.distinctBy { it.lowercase() }.ifEmpty { listOf(raw.trim()) }
}
