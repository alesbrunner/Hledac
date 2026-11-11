package cz.ales.dochledac

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val app get() = application as App
    private lateinit var search: TextInputEditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var pickFirst: Button
    private lateinit var adapter: ResultsAdapter
    private var anywhere = false
    private var searchJob: Job? = null
    private var lastVersion = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        search = findViewById(R.id.search)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        pickFirst = findViewById(R.id.pickFirst)

        val list = findViewById<RecyclerView>(R.id.list)
        adapter = ResultsAdapter(
            onOpen = { doc, line -> openViewer(doc, line) },
            onExternal = { openExternal(this, it) },
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })

        search.doAfterTextChanged { runSearch(180) }
        search.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            runSearch(0)
            true
        }
        findViewById<ChipGroup>(R.id.modeGroup).setOnCheckedStateChangeListener { _, ids ->
            anywhere = ids.contains(R.id.chipAnywhere)
            runSearch(0)
        }
        pickFirst.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Indexer.state.collect { renderIndex(it) }
            }
        }

        if (savedInstanceState == null && app.prefs.autoUpdate && app.prefs.folders.isNotEmpty()) {
            Indexer.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        pickFirst.visibility = if (app.prefs.folders.isEmpty()) View.VISIBLE else View.GONE
        if (search.text.isNullOrBlank()) showIdle()
    }

    private fun renderIndex(s: IndexState) {
        if (s.running) {
            progress.visibility = View.VISIBLE
            if (s.total > 0) {
                progress.isIndeterminate = false
                progress.max = s.total
                progress.progress = s.done
            } else {
                progress.isIndeterminate = true
            }
            if (search.text.isNullOrBlank()) status.text = s.message
        } else {
            progress.visibility = View.GONE
            if (s.version != lastVersion) {
                lastVersion = s.version
                runSearch(0) // nový obsah indexu -> obnovit výsledky
            }
        }
    }

    private fun showIdle() {
        val st = Indexer.state.value
        if (st.running) {
            status.text = st.message
            return
        }
        lifecycleScope.launch {
            val (n, chars) = withContext(Dispatchers.IO) { Repo.stats(app) }
            if (!search.text.isNullOrBlank()) return@launch
            status.text = when {
                app.prefs.folders.isEmpty() -> "Nejdřív vyber složku, kterou chceš prohledávat."
                else -> "V indexu: ${plural(n, "soubor", "soubory", "souborů")} · ${formatChars(chars)} znaků" +
                    (if (st.message.isNotEmpty()) "\n${st.message}" else "")
            }
        }
    }

    private fun runSearch(delayMs: Long) {
        searchJob?.cancel()
        val q = search.text?.toString().orEmpty().trim()
        val any = anywhere
        searchJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            if (q.isEmpty()) {
                adapter.submit(emptyList())
                showIdle()
                return@launch
            }
            val t0 = SystemClock.elapsedRealtime()
            val res = try {
                withContext(Dispatchers.Default) { Searcher.search(app, q, any) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = "Chyba hledání: ${e.message}"
                return@launch
            }
            val ms = SystemClock.elapsedRealtime() - t0
            adapter.submit(res.files)
            status.text = if (res.files.isEmpty()) {
                "Nic nenalezeno · $ms ms"
            } else {
                plural(res.files.size, "soubor", "soubory", "souborů") + (if (res.truncated) "+" else "") +
                    " · " + plural(res.totalHits, "výskyt", "výskyty", "výskytů") + " · $ms ms"
            }
        }
    }

    private fun openViewer(doc: Doc, line: Int) {
        hideKeyboard()
        startActivity(
            Intent(this, ViewerActivity::class.java)
                .putExtra(ViewerActivity.EXTRA_DOC, doc.id)
                .putExtra(ViewerActivity.EXTRA_QUERY, search.text?.toString().orEmpty())
                .putExtra(ViewerActivity.EXTRA_ANYWHERE, anywhere)
                .putExtra(ViewerActivity.EXTRA_LINE, line)
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            if (app.prefs.folders.isEmpty()) startActivity(Intent(this, SettingsActivity::class.java))
            else Indexer.start(this)
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
