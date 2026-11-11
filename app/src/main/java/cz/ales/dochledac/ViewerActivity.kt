package cz.ales.dochledac

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Náhled celého textu souboru se zvýrazněnými výskyty a skákáním mezi nimi. */
class ViewerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DOC = "doc"
        const val EXTRA_QUERY = "q"
        const val EXTRA_ANYWHERE = "any"
        const val EXTRA_LINE = "line"
    }

    private class Loaded(val doc: Doc, val dt: DocText, val hits: Map<Int, List<IntArray>>, val hitLines: IntArray)

    private var doc: Doc? = null
    private var loaded: Loaded? = null
    private var adapter: LinesAdapter? = null
    private var idx = -1
    private lateinit var list: RecyclerView
    private lateinit var counter: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = findViewById(R.id.lines)
        counter = findViewById(R.id.counter)
        list.layoutManager = LinearLayoutManager(this)

        val id = intent.getLongExtra(EXTRA_DOC, -1)
        val q = intent.getStringExtra(EXTRA_QUERY).orEmpty()
        val any = intent.getBooleanExtra(EXTRA_ANYWHERE, false)
        val startLine = savedInstanceState?.getInt("line") ?: intent.getIntExtra(EXTRA_LINE, 0)

        findViewById<Button>(R.id.prev).setOnClickListener { step(-1) }
        findViewById<Button>(R.id.next).setOnClickListener { step(1) }

        val app = application as App
        lifecycleScope.launch {
            val l = withContext(Dispatchers.Default) {
                val d = Repo.doc(app, id) ?: return@withContext null
                val dt = DocText(Repo.loadText(app, id))
                val map = HashMap<Int, MutableList<IntArray>>()
                for (h in Matcher(q, any).findAll(dt.norm)) {
                    map.getOrPut(dt.lineOf(h[0])) { ArrayList() }.add(h)
                }
                Loaded(d, dt, map, map.keys.sorted().toIntArray())
            }
            if (l == null) {
                finish()
                return@launch
            }
            show(l, startLine)
        }
    }

    private fun show(l: Loaded, startLine: Int) {
        loaded = l
        doc = l.doc
        supportActionBar?.title = l.doc.name
        supportActionBar?.subtitle = l.doc.path
        if (l.dt.text.isEmpty()) {
            val msg = findViewById<TextView>(R.id.message)
            msg.visibility = View.VISIBLE
            msg.text = if (l.doc.error != null) "Text souboru se nepodařilo přečíst:\n${l.doc.error}\n\nZkus ho otevřít v jiné aplikaci (menu ⋮)."
            else "Soubor neobsahuje žádný text (např. naskenované PDF bez textové vrstvy)."
            findViewById<View>(R.id.bar).visibility = View.GONE
            return
        }
        val a = LinesAdapter(l.dt, l.hits)
        adapter = a
        list.adapter = a
        if (l.hitLines.isEmpty()) {
            idx = -1
            counter.text = "bez výskytů"
            list.scrollToPosition(startLine.coerceIn(0, l.dt.lineCount - 1))
        } else {
            val i = l.hitLines.indexOfFirst { it >= startLine }
            idx = if (i < 0) l.hitLines.size - 1 else i
            goTo()
        }
    }

    private fun step(d: Int) {
        val l = loaded ?: return
        if (l.hitLines.isEmpty()) return
        idx = (idx + d + l.hitLines.size) % l.hitLines.size
        goTo()
    }

    private fun goTo() {
        val l = loaded ?: return
        val a = adapter ?: return
        val line = l.hitLines[idx]
        val old = a.current
        a.current = line
        if (old >= 0) a.notifyItemChanged(old)
        a.notifyItemChanged(line)
        list.post {
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(line, list.height / 3)
        }
        val page = l.dt.pageOf(line)
        counter.text = "${idx + 1} / ${l.hitLines.size}" + (if (page > 0) " · str. $page" else " · ř. ${line + 1}")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val l = loaded
        if (l != null && idx >= 0 && idx < l.hitLines.size) outState.putInt("line", l.hitLines[idx])
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.action_open -> {
            doc?.let { openExternal(this, it) }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private class LinesAdapter(private val dt: DocText, private val hits: Map<Int, List<IntArray>>) :
        RecyclerView.Adapter<LinesAdapter.VH>() {
        var current = -1

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val num: TextView = v.findViewById(R.id.num)
            val text: TextView = v.findViewById(R.id.text)
        }

        override fun getItemCount(): Int = dt.lineCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_viewer_line, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.num.text = (position + 1).toString()
            val s = dt.lineStart(position)
            val e = dt.lineEnd(position)
            val h = hits[position]
            holder.text.text = if (h == null) dt.text.substring(s, e) else Highlight.build(dt.text, s, e, h, Int.MAX_VALUE)
            holder.itemView.setBackgroundColor(if (position == current) Highlight.CURRENT_LINE else Color.TRANSPARENT)
        }
    }
}
