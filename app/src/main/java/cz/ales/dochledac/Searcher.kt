package cz.ales.dochledac

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class LineHit(val line: Int, val page: Int, val display: CharSequence)

class FileResult(
    val doc: Doc,
    val total: Int,          // počet výskytů
    val lineCount: Int,      // počet řádků s výskytem
    val previews: List<LineHit>,
)

class SearchResult(val files: List<FileResult>, val totalHits: Int, val truncated: Boolean)

object Searcher {
    private const val MAX_FILES = 300
    private const val PREVIEW_LINES = 3

    suspend fun search(app: App, query: String, anywhere: Boolean): SearchResult {
        val m = Matcher(query, anywhere)
        if (m.tokens.isEmpty()) return SearchResult(emptyList(), 0, false)

        val sql: String
        val args: Array<String>
        if (!anywhere) {
            // rychlé hledání ve fulltextovém indexu: slova začínající zadaným textem
            sql = "SELECT ${Repo.COLS} FROM docs WHERE id IN (SELECT docid FROM fts WHERE fts MATCH ?) LIMIT ${MAX_FILES + 1}"
            args = arrayOf(m.tokens.joinToString(" ") { "$it*" })
        } else {
            // kdekoli v textu (i uprostřed slova) – prochází celý index, je pomalejší
            val cond = m.tokens.joinToString(" AND ") { "(instr(body, ?) > 0 OR instr(name, ?) > 0)" }
            sql = "SELECT ${Repo.COLS} FROM docs WHERE id IN (SELECT docid FROM fts WHERE $cond) LIMIT ${MAX_FILES + 1}"
            args = m.tokens.flatMap { listOf(it, it) }.toTypedArray()
        }

        val docs = ArrayList<Doc>()
        app.db.readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) docs.add(Repo.fromCursor(c))
        }
        val truncated = docs.size > MAX_FILES
        if (truncated) docs.removeAt(docs.size - 1)

        val out = ArrayList<FileResult>(docs.size)
        for (d in docs) {
            currentCoroutineContext().ensureActive()
            out.add(analyze(app, d, m))
        }
        out.sortWith(compareByDescending<FileResult> { it.total }.thenBy { it.doc.name.lowercase() })
        return SearchResult(out, out.sumOf { it.total }, truncated)
    }

    private fun analyze(app: App, d: Doc, m: Matcher): FileResult {
        if (d.chars == 0L) return FileResult(d, 0, 0, emptyList())
        val text = Repo.loadText(app, d.id)
        if (text.isEmpty()) return FileResult(d, 0, 0, emptyList())
        val dt = DocText(text)
        val hits = m.findAll(dt.norm)
        val previews = ArrayList<LineHit>()
        var lineCount = 0
        var i = 0
        while (i < hits.size) {
            val ln = dt.lineOf(hits[i][0])
            val next = dt.nextLineStart(ln)
            var j = i
            while (j < hits.size && hits[j][0] < next) j++
            lineCount++
            if (previews.size < PREVIEW_LINES) {
                previews.add(
                    LineHit(ln, dt.pageOf(ln), Highlight.build(text, dt.lineStart(ln), dt.lineEnd(ln), hits.subList(i, j), 160))
                )
            }
            i = j
        }
        return FileResult(d, hits.size, lineCount, previews)
    }
}
