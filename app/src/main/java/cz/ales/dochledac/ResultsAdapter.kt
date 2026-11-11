package cz.ales.dochledac

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ResultsAdapter(
    private val onOpen: (Doc, Int) -> Unit,
    private val onExternal: (Doc) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        class Header(val r: FileResult) : Row()
        class Line(val r: FileResult, val h: LineHit) : Row()
        class More(val r: FileResult, val more: Int) : Row()
    }

    private class VH(v: View) : RecyclerView.ViewHolder(v)

    private var rows: List<Row> = emptyList()

    fun submit(results: List<FileResult>) {
        val list = ArrayList<Row>()
        for (r in results) {
            list.add(Row.Header(r))
            for (h in r.previews) list.add(Row.Line(r, h))
            val more = r.lineCount - r.previews.size
            if (more > 0) list.add(Row.More(r, more))
        }
        rows = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> 0
        is Row.Line -> 1
        is Row.More -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = when (viewType) {
            0 -> R.layout.item_header
            1 -> R.layout.item_line
            else -> R.layout.item_more
        }
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val v = holder.itemView
        when (val row = rows[position]) {
            is Row.Header -> {
                val d = row.r.doc
                val badge = v.findViewById<TextView>(R.id.ext)
                badge.text = d.ext.uppercase()
                badge.backgroundTintList = ColorStateList.valueOf(colorFor(d.ext))
                v.findViewById<TextView>(R.id.name).text = d.name
                v.findViewById<TextView>(R.id.path).text = d.path
                v.findViewById<TextView>(R.id.count).text = when {
                    d.error != null -> "chyba čtení"
                    row.r.total == 0 -> "v názvu"
                    else -> "${row.r.total}×"
                }
                v.setOnClickListener { onOpen(d, row.r.previews.firstOrNull()?.line ?: 0) }
                v.setOnLongClickListener { onExternal(d); true }
            }
            is Row.Line -> {
                v.findViewById<TextView>(R.id.loc).text =
                    if (row.h.page > 0) "s.${row.h.page}" else "${row.h.line + 1}"
                v.findViewById<TextView>(R.id.text).text = row.h.display
                v.setOnClickListener { onOpen(row.r.doc, row.h.line) }
                v.setOnLongClickListener(null)
            }
            is Row.More -> {
                v.findViewById<TextView>(R.id.text).text =
                    "+ další ${plural(row.more, "řádek", "řádky", "řádků")} s výskytem – otevřít"
                v.setOnClickListener { onOpen(row.r.doc, row.r.previews.lastOrNull()?.line ?: 0) }
            }
        }
    }

    private fun colorFor(ext: String): Int = when (Formats.key(ext)) {
        "pdf" -> 0xFFD32F2F.toInt()
        "docx" -> 0xFF1565C0.toInt()
        "odt" -> 0xFF0277BD.toInt()
        "srt", "vtt", "ass", "sub" -> 0xFF6A1B9A.toInt()
        "html" -> 0xFFEF6C00.toInt()
        else -> 0xFF546E7A.toInt()
    }
}
