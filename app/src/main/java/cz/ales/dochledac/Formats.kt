package cz.ales.dochledac

object Formats {
    class Format(val key: String, val label: String)

    val ALL = listOf(
        Format("txt", "TXT – prostý text"),
        Format("srt", "SRT – titulky"),
        Format("vtt", "VTT – titulky"),
        Format("ass", "ASS / SSA – titulky"),
        Format("sub", "SUB – titulky (textové)"),
        Format("md", "MD – Markdown"),
        Format("csv", "CSV – tabulky"),
        Format("log", "LOG – logy"),
        Format("json", "JSON"),
        Format("xml", "XML"),
        Format("ini", "INI / CFG – konfigurace"),
        Format("html", "HTML / HTM – webové stránky"),
        Format("pdf", "PDF"),
        Format("docx", "DOCX – Word"),
        Format("odt", "ODT – LibreOffice / OpenOffice"),
    )

    /** Sjednocení přípon se stejným zpracováním. */
    fun key(ext: String): String = when (ext) {
        "htm", "xhtml" -> "html"
        "ssa" -> "ass"
        "markdown" -> "md"
        "cfg", "conf" -> "ini"
        else -> ext
    }

    fun mime(ext: String): String = when (key(ext)) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "html" -> "text/html"
        else -> "text/plain"
    }
}
