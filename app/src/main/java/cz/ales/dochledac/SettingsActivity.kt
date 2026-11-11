package cz.ales.dochledac

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private val app get() = application as App
    private lateinit var foldersBox: LinearLayout
    private lateinit var stats: TextView
    private lateinit var indexStatus: TextView
    private lateinit var progress: ProgressBar
    private var formatsChanged = false
    private var lastVersion = -1

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) onFolderPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Nastavení"

        foldersBox = findViewById(R.id.folders)
        stats = findViewById(R.id.stats)
        indexStatus = findViewById(R.id.indexStatus)
        progress = findViewById(R.id.progress)

        findViewById<Button>(R.id.addFolder).setOnClickListener { pickFolder.launch(null) }

        val formatsBox = findViewById<LinearLayout>(R.id.formats)
        val selected = app.prefs.exts.toMutableSet()
        for (f in Formats.ALL) {
            val cb = MaterialCheckBox(this)
            cb.text = f.label
            cb.isChecked = f.key in selected
            cb.setOnCheckedChangeListener { _, on ->
                if (on) selected.add(f.key) else selected.remove(f.key)
                app.prefs.exts = selected
                formatsChanged = true
            }
            formatsBox.addView(cb)
        }

        val auto = findViewById<MaterialSwitch>(R.id.auto)
        auto.isChecked = app.prefs.autoUpdate
        auto.setOnCheckedChangeListener { _, on -> app.prefs.autoUpdate = on }

        findViewById<Button>(R.id.reindex).setOnClickListener {
            formatsChanged = false
            Indexer.start(this)
        }
        findViewById<Button>(R.id.clear).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Smazat index?")
                .setMessage("Smaže se jen index v aplikaci, tvoje soubory zůstanou beze změny. Index se pak dá vytvořit znovu.")
                .setPositiveButton("Smazat") { _, _ -> Indexer.clearIndex(app) }
                .setNegativeButton("Zrušit", null)
                .show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Indexer.state.collect { s ->
                    progress.visibility = if (s.running) View.VISIBLE else View.GONE
                    if (s.running) {
                        if (s.total > 0) {
                            progress.isIndeterminate = false
                            progress.max = s.total
                            progress.progress = s.done
                        } else {
                            progress.isIndeterminate = true
                        }
                    }
                    indexStatus.text = s.message
                    if (s.version != lastVersion) {
                        lastVersion = s.version
                        refreshStats()
                    }
                }
            }
        }
        renderFolders()
    }

    override fun onStop() {
        super.onStop()
        if (formatsChanged) {
            formatsChanged = false
            Indexer.start(this)
        }
    }

    private fun refreshStats() {
        lifecycleScope.launch {
            val (n, chars) = withContext(Dispatchers.IO) { Repo.stats(app) }
            stats.text = "V indexu: ${plural(n, "soubor", "soubory", "souborů")} · ${formatChars(chars)} znaků"
        }
    }

    private fun onFolderPicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // některé správce souborů trvalé oprávnění nenabízí
        }
        val folders = app.prefs.folders
        if (uri.toString() !in folders) app.prefs.folders = folders + uri.toString()
        renderFolders()
        Indexer.start(this)
    }

    private fun removeFolder(f: String) {
        try {
            contentResolver.releasePersistableUriPermission(Uri.parse(f), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
        }
        app.prefs.folders = app.prefs.folders - f
        renderFolders()
        Indexer.start(this)
    }

    private fun renderFolders() {
        foldersBox.removeAllViews()
        val folders = app.prefs.folders
        val granted = contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        if (folders.isEmpty()) {
            foldersBox.addView(TextView(this).apply {
                text = "Zatím žádná složka."
                alpha = 0.7f
                setPadding(0, 8, 0, 8)
            })
        }
        val dp = resources.displayMetrics.density
        for (f in folders) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val tv = TextView(this).apply {
                text = Paths.label(Uri.parse(f)) + (if (f !in granted) "  (chybí oprávnění – přidej znovu)" else "")
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val rm = TextView(this).apply {
                text = "Odebrat"
                textSize = 14f
                setTextColor(0xFFD32F2F.toInt())
                val p = (12 * dp).toInt()
                setPadding(p, p, p, p)
                setOnClickListener { removeFolder(f) }
            }
            row.addView(tv)
            row.addView(rm)
            foldersBox.addView(row)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
