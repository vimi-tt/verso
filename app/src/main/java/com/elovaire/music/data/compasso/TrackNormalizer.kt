package elovaire.music.droidbeauty.app.data.compasso

import java.text.Normalizer

/**
 * Generates canonical keys combining song title and primary artist while stripping
 * common YouTube Music noise tags (Official Audio/Video, Live, Remaster, Lyrics, HD/4K, feat/ft).
 */
internal object TrackNormalizer {
    private val NOISE_TOKENS = listOf(
        "official music video",
        "official lyric video",
        "official audio",
        "official video",
        "music video",
        "lyric video",
        "lyrics",
        "remastered",
        "remaster",
        "ao vivo",
        "live",
        "hd",
        "4k",
        "feat\\.",
        "ft\\.",
        "featuring",
        "explicit",
        "audio",
        "video",
        "visualizer",
    )

    private val BRACKETED_NOISE_REGEX = Regex(
        pattern = """[\(\[\{][^\)\]\}]*\b(?:${NOISE_TOKENS.joinToString("|")})\b[^\)\]\}]*[\)\]\}]""",
        option = RegexOption.IGNORE_CASE,
    )

    private val TRAILING_FEAT_REGEX = Regex(
        pattern = """\b(?:feat\.?|ft\.?|featuring)\b.*$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val LOOSE_NOISE_REGEX = Regex(
        pattern = """(?:\s*[-–—|•:]\s+)\b(?:${NOISE_TOKENS.joinToString("|")})\b(?:\s+\d{4})?""",
        option = RegexOption.IGNORE_CASE,
    )

    private val TRAILING_QUALITY_REGEX = Regex(
        pattern = """\s+\b(?:hd|4k)\b\s*$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val ARTIST_SPLIT_REGEX = Regex(
        pattern = """\s*(?:\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|;|·|•)\s*""",
        option = RegexOption.IGNORE_CASE,
    )

    private val DIACRITICS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
    private val NON_ALNUM_REGEX = Regex("""[^\p{L}\p{N}\p{M}\s]""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    fun normalizeTitle(rawTitle: String): String {
        if (rawTitle.isBlank()) return ""
        var cleaned = stripAccents(rawTitle).lowercase()
        cleaned = BRACKETED_NOISE_REGEX.replace(cleaned, " ")
        cleaned = TRAILING_FEAT_REGEX.replace(cleaned, " ")
        cleaned = LOOSE_NOISE_REGEX.replace(cleaned, " ")
        cleaned = TRAILING_QUALITY_REGEX.replace(cleaned, " ")
        cleaned = NON_ALNUM_REGEX.replace(cleaned, " ")
        return MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
    }

    fun normalizePrimaryArtist(
        rawArtist: String,
        structuredArtists: List<String> = emptyList(),
        artistId: String? = null,
    ): String {
        val structuredPrimary = structuredArtists.firstOrNull { it.isNotBlank() }
        val candidate = structuredPrimary ?: rawArtist
        if (candidate.isBlank()) {
            return artistId?.trim()?.takeIf { it.isNotBlank() }?.lowercase().orEmpty()
        }
        val first = if (structuredPrimary != null) {
            structuredPrimary.trim()
        } else {
            ARTIST_SPLIT_REGEX.split(candidate.trim()).firstOrNull().orEmpty()
        }
        val stripped = stripAccents(first).lowercase()
        val alnum = NON_ALNUM_REGEX.replace(stripped, " ")
        return MULTI_SPACE_REGEX.replace(alnum, " ").trim()
    }

    fun normalizeKey(
        title: String,
        artist: String,
        structuredArtists: List<String> = emptyList(),
        artistId: String? = null,
    ): String {
        val normTitle = normalizeTitle(title)
        val normArtist = normalizePrimaryArtist(artist, structuredArtists, artistId)
        return "$normTitle|$normArtist"
    }

    private fun stripAccents(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val withoutLatinDiacritics = DIACRITICS_REGEX.replace(decomposed, "")
        return Normalizer.normalize(withoutLatinDiacritics, Normalizer.Form.NFC)
    }
}
