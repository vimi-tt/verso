package elovaire.music.droidbeauty.app.data.lyrics

/**
 * Offline deterministic romanization for Japanese (Hiragana/Katakana), Korean (Hangul),
 * and Cyrillic scripts so non-Latin lyrics can be sung along with word-by-word karaoke.
 */
internal object LyricsTransliterator {

    fun hasNonLatinScript(text: String): Boolean {
        for (ch in text) {
            if (isHiragana(ch) || isKatakana(ch) || isHangul(ch) || isCyrillic(ch)) {
                return true
            }
        }
        return false
    }

    fun romanizeOrNull(text: String): String? {
        if (text.isBlank() || !hasNonLatinScript(text)) return null
        val out = StringBuilder(text.length * 2)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                isHiragana(ch) || isKatakana(ch) -> {
                    val normalized = if (isKatakana(ch)) (ch.code - 0x60).toChar() else ch
                    // Handle small tsu (っ / ッ) gemination
                    if (normalized == 'っ' && i + 1 < text.length) {
                        val nextKana = toHiraganaIfKana(text[i + 1])
                        val nextRomaji = nextKana?.let { KANA_SINGLE[it] }
                        val firstConsonant = nextRomaji?.firstOrNull()?.takeIf { it.isLetter() && it !in VOWELS }
                        if (firstConsonant != null) {
                            out.append(firstConsonant)
                            i += 1
                            continue
                        }
                    }
                    // Handle prolonged sound mark (ー)
                    if (ch == 'ー') {
                        val lastVowel = out.lastOrNull { it in VOWELS }
                        if (lastVowel != null) out.append(lastVowel)
                        i += 1
                        continue
                    }
                    // Check 2-char digraphs (きゃ, しゃ, etc.)
                    if (i + 1 < text.length) {
                        val nextNorm = toHiraganaIfKana(text[i + 1])
                        if (nextNorm != null) {
                            val pair = "$normalized$nextNorm"
                            val digraph = KANA_DIGRAPHS[pair]
                            if (digraph != null) {
                                out.append(digraph)
                                i += 2
                                continue
                            }
                        }
                    }
                    out.append(KANA_SINGLE[normalized] ?: ch.toString())
                    i += 1
                }
                isHangul(ch) -> {
                    out.append(romanizeHangul(ch))
                    i += 1
                }
                isCyrillic(ch) -> {
                    out.append(CYRILLIC_MAP[ch] ?: ch.toString())
                    i += 1
                }
                else -> {
                    out.append(ch)
                    i += 1
                }
            }
        }
        return out.toString().trim().takeIf { it.isNotBlank() && it != text.trim() }
    }

    private fun romanizeHangul(ch: Char): String {
        val code = ch.code - 0xAC00
        if (code < 0 || code > 11171) return ch.toString()
        val initialIdx = code / (21 * 28)
        val medialIdx = (code % (21 * 28)) / 28
        val finalIdx = code % 28
        return HANGUL_INITIALS[initialIdx] + HANGUL_MEDIALS[medialIdx] + HANGUL_FINALS[finalIdx]
    }

    private fun toHiraganaIfKana(ch: Char): Char? = when {
        isHiragana(ch) -> ch
        isKatakana(ch) -> (ch.code - 0x60).toChar()
        else -> null
    }

    private fun isHiragana(ch: Char): Boolean = ch.code in 0x3041..0x3096
    private fun isKatakana(ch: Char): Boolean = ch.code in 0x30A1..0x30F6 || ch == 'ー'
    private fun isHangul(ch: Char): Boolean = ch.code in 0xAC00..0xD7A3
    private fun isCyrillic(ch: Char): Boolean = ch.code in 0x0400..0x04FF

    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')

    private val HANGUL_INITIALS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
        "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
    )
    private val HANGUL_MEDIALS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
    )
    private val HANGUL_FINALS = arrayOf(
        "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lk",
        "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t",
        "ss", "ng", "t", "t", "k", "t", "p", "t",
    )

    private val KANA_DIGRAPHS = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
    )

    private val KANA_SINGLE = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "o", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo",
    )

    private val CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya",
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D",
        'Е' to "E", 'Ё' to "Yo", 'Ж' to "Zh", 'З' to "Z", 'И' to "I",
        'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M", 'Н' to "N",
        'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T",
        'У' to "U", 'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts", 'Ч' to "Ch",
        'Ш' to "Sh", 'Щ' to "Shch", 'Ъ' to "", 'Ы' to "Y", 'Ь' to "",
        'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya",
    )
}
