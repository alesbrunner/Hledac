package cz.ales.dochledac

import java.text.Normalizer

/**
 * Normalizace textu pro hledání: malá písmena, bez diakritiky, vše kromě písmen a číslic -> mezera.
 * Každý znak se mapuje přesně na jeden znak, takže pozice v normalizovaném textu
 * odpovídají pozicím v původním textu (díky tomu jde přesně zvýraznit nalezené místo).
 */
object TextFold {
    private val table: CharArray by lazy {
        val t = CharArray(65536)
        for (i in 0 until 65536) t[i] = foldChar(i.toChar())
        t
    }

    private fun foldChar(c: Char): Char {
        if (c.isSurrogate()) return ' '
        if (!c.isLetterOrDigit()) return ' '
        val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        val base = if (d.isNotEmpty() && d[0].isLetterOrDigit()) d[0] else c
        return base.lowercaseChar()
    }

    fun norm(s: String): String {
        val t = table
        val out = CharArray(s.length)
        for (i in s.indices) out[i] = t[s[i].code]
        return String(out)
    }
}

/** Dotaz rozložený na slova (normalizovaná). Soubor musí obsahovat všechna slova. */
class Matcher(query: String, val anywhere: Boolean) {
    val tokens: List<String> = TextFold.norm(query)
        .split(' ')
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedByDescending { it.length }

    /** Vrací seřazené výskyty jako [pozice, délka]. */
    fun findAll(norm: String, limit: Int = 50_000): List<IntArray> {
        val res = ArrayList<IntArray>()
        for (t in tokens) {
            var idx = norm.indexOf(t)
            while (idx >= 0 && res.size < limit) {
                if (anywhere || idx == 0 || norm[idx - 1] == ' ') res.add(intArrayOf(idx, t.length))
                idx = norm.indexOf(t, idx + t.length)
            }
        }
        res.sortBy { it[0] }
        return res
    }
}

/** Text dokumentu rozdělený na řádky (a u PDF na stránky – oddělovač \u000C). */
class DocText(val text: String) {
    val norm: String = TextFold.norm(text)
    private val starts: IntArray
    private val pages: IntArray?

    init {
        var n = 1
        for (ch in text) if (ch == '\n' || ch == '\u000C') n++
        val s = IntArray(n)
        val p = if (text.indexOf('\u000C') >= 0) IntArray(n) else null
        var k = 1
        var page = 1
        if (p != null) p[0] = 1
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '\n' || ch == '\u000C') {
                if (ch == '\u000C') page++
                s[k] = i + 1
                if (p != null) p[k] = page
                k++
            }
        }
        starts = s
        pages = p
    }

    val lineCount: Int get() = starts.size
    val hasPages: Boolean get() = pages != null

    fun lineStart(l: Int): Int = starts[l]

    fun nextLineStart(l: Int): Int = if (l + 1 < starts.size) starts[l + 1] else Int.MAX_VALUE

    fun lineEnd(l: Int): Int {
        var e = if (l + 1 < starts.size) starts[l + 1] - 1 else text.length
        if (e > starts[l] && text[e - 1] == '\r') e--
        return e
    }

    fun lineOf(pos: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= pos) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun pageOf(l: Int): Int = pages?.get(l) ?: 0
}
