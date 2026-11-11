package cz.ales.dochledac

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan

object Highlight {
    const val HIT_COLOR = 0x88FFC107.toInt()
    const val CURRENT_LINE = 0x2242A5F5

    /**
     * Úsek textu [ls, le) se zvýrazněnými výskyty. Dlouhé řádky se zkrátí kolem prvního výskytu.
     * hits = [absolutní pozice, délka]
     */
    fun build(text: String, ls: Int, le: Int, hits: List<IntArray>, maxLen: Int): CharSequence {
        var ws = ls
        var we = le
        if (le - ls > maxLen) {
            val first = hits.firstOrNull()?.get(0) ?: ls
            ws = maxOf(ls, first - maxLen / 4)
            we = minOf(le, ws + maxLen)
        }
        val sb = SpannableStringBuilder()
        if (ws > ls) sb.append('…')
        val off = sb.length - ws
        sb.append(text, ws, we)
        if (we < le) sb.append('…')
        for (h in hits) {
            val s = maxOf(h[0], ws)
            val e = minOf(h[0] + h[1], we)
            if (s < e) {
                sb.setSpan(BackgroundColorSpan(HIT_COLOR), s + off, e + off, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), s + off, e + off, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }
}
