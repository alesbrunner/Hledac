package cz.ales.dochledac

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Xml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/** Vytažení čistého textu ze souborů. */
object Extractors {
    private const val MAX_BYTES = 80L * 1024 * 1024

    fun extract(ctx: Context, uri: Uri, ext: String, size: Long): String {
        if (size > MAX_BYTES) throw IOException("soubor je příliš velký (${size / 1048576} MB)")
        val cr = ctx.contentResolver
        fun open(): InputStream = cr.openInputStream(uri) ?: throw IOException("soubor nelze otevřít")
        return when (Formats.key(ext)) {
            "pdf" -> open().use { pdf(it) }
            "docx" -> open().use { input -> zipXml(input, "word/document.xml") { p -> docx(p) } }
            "odt" -> open().use { input -> zipXml(input, "content.xml") { p -> odt(p) } }
            "html" -> html(decode(open().use { it.readBytes() }))
            else -> decode(open().use { it.readBytes() })
        }
    }

    /** Automatické rozpoznání kódování: BOM, UTF-16, UTF-8, jinak windows-1250 (čeština). */
    fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte())
            return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte())
            return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        // UTF-16 bez BOM: hodně nulových bajtů na sudých/lichých pozicích
        val probe = minOf(b.size, 4000)
        if (probe >= 4) {
            var zeroEven = 0
            var zeroOdd = 0
            for (i in 0 until probe) if (b[i] == 0.toByte()) { if (i % 2 == 0) zeroEven++ else zeroOdd++ }
            if (zeroOdd > probe / 4 && zeroEven < probe / 50) return String(b, Charsets.UTF_16LE)
            if (zeroEven > probe / 4 && zeroOdd < probe / 50) return String(b, Charsets.UTF_16BE)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b))
                .toString()
        } catch (e: CharacterCodingException) {
            String(b, Charset.forName("windows-1250"))
        }
    }

    private fun pdf(input: InputStream): String = PDDocument.load(input).use { doc ->
        val st = PDFTextStripper()
        val sb = StringBuilder()
        for (p in 1..doc.numberOfPages) {
            st.startPage = p
            st.endPage = p
            sb.append(st.getText(doc))
            sb.append('\u000C') // konec stránky
        }
        sb.toString()
    }

    private fun zipXml(input: InputStream, entryName: String, parse: (XmlPullParser) -> String): String {
        val zis = ZipInputStream(BufferedInputStream(input))
        while (true) {
            val e = zis.nextEntry ?: break
            if (e.name == entryName) {
                val p = Xml.newPullParser()
                p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                p.setInput(zis, "UTF-8")
                return parse(p)
            }
        }
        throw IOException("v souboru chybí $entryName")
    }

    private fun docx(p: XmlPullParser): String {
        val sb = StringBuilder()
        var inText = false
        var inTabs = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "w:t" -> inText = true
                    "w:tabs" -> inTabs = true
                    "w:tab" -> if (!inTabs) sb.append('\t')
                    "w:br", "w:cr" -> sb.append('\n')
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "w:t" -> inText = false
                    "w:tabs" -> inTabs = false
                    "w:p" -> sb.append('\n')
                }
                XmlPullParser.TEXT -> if (inText) sb.append(p.text)
            }
            ev = p.next()
        }
        return sb.toString()
    }

    private fun odt(p: XmlPullParser): String {
        val sb = StringBuilder()
        var inBody = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "office:body" -> inBody = true
                    "text:s" -> {
                        val c = p.getAttributeValue(null, "text:c")?.toIntOrNull() ?: 1
                        repeat(c.coerceIn(1, 100)) { sb.append(' ') }
                    }
                    "text:tab" -> sb.append('\t')
                    "text:line-break" -> sb.append('\n')
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "office:body" -> inBody = false
                    "text:p", "text:h" -> sb.append('\n')
                }
                XmlPullParser.TEXT -> if (inBody) sb.append(p.text)
            }
            ev = p.next()
        }
        return sb.toString()
    }

    private fun html(s: String): String {
        val cleaned = s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        return Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
    }
}
