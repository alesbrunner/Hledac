package cz.ales.dochledac

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class IndexState(
    val running: Boolean = false,
    val message: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val version: Int = 0,
)

/** Inkrementální indexace: znovu se zpracují jen nové a změněné soubory (podle velikosti a data změny). */
object Indexer {
    private class FileEntry(val uri: String, val name: String, val path: String, val ext: String, val size: Long, val mtime: Long)
    private class Existing(val id: Long, val size: Long, val mtime: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(IndexState())
    val state: StateFlow<IndexState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var again = false

    private val PROJ = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    @Synchronized
    fun start(ctx: Context) {
        if (job?.isActive == true) {
            again = true // po doběhnutí proběhne ještě jednou (změnilo se nastavení)
            return
        }
        val app = ctx.applicationContext as App
        job = scope.launch {
            var msg: String
            do {
                again = false
                msg = try {
                    runOnce(app)
                } catch (e: CancellationException) {
                    "Indexace přerušena."
                } catch (e: Throwable) {
                    "Chyba indexace: ${e.message ?: e.javaClass.simpleName}"
                }
            } while (again && isActive)
            _state.value = IndexState(false, msg, version = _state.value.version + 1)
        }
    }

    fun clearIndex(app: App) {
        scope.launch {
            job?.cancelAndJoin()
            Repo.clear(app)
            _state.value = IndexState(false, "Index byl smazán.", version = _state.value.version + 1)
        }
    }

    private fun progress(msg: String, done: Int, total: Int) {
        _state.value = IndexState(true, msg, done, total, _state.value.version)
    }

    private suspend fun runOnce(app: App): String {
        val folders = app.prefs.folders
        val exts = app.prefs.exts
        val db = app.db.writableDatabase

        progress("Procházím složky…", 0, 0)
        val found = ArrayList<FileEntry>()
        for (f in folders) scan(app.contentResolver, Uri.parse(f), exts, found)

        val existing = HashMap<String, Existing>()
        db.rawQuery("SELECT id, uri, size, mtime FROM docs", null).use { c ->
            while (c.moveToNext()) existing[c.getString(1)] = Existing(c.getLong(0), c.getLong(2), c.getLong(3))
        }

        val keep = HashSet<Long>()
        val todo = ArrayList<Pair<FileEntry, Long?>>()
        val seenUris = HashSet<String>()
        for (f in found) {
            if (!seenUris.add(f.uri)) continue
            val e = existing[f.uri]
            if (e != null) keep.add(e.id)
            if (e == null || e.size != f.size || e.mtime != f.mtime) todo.add(f to e?.id)
        }

        // smazané soubory / odebrané složky / vypnuté formáty
        val removed = existing.values.filter { it.id !in keep }
        if (removed.isNotEmpty()) {
            db.beginTransaction()
            try {
                for (e in removed) deleteDoc(app, db, e.id)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        var done = 0
        db.beginTransaction()
        try {
            var batchStart = SystemClock.elapsedRealtime()
            for ((f, oldId) in todo) {
                currentCoroutineContext().ensureActive()
                progress("Indexuji ${done + 1}/${todo.size}: ${f.name}", done, todo.size)
                var text = ""
                var err: String? = null
                try {
                    text = Extractors.extract(app, Uri.parse(f.uri), f.ext, f.size)
                } catch (e: Throwable) {
                    err = e.message ?: e.javaClass.simpleName
                }
                if (oldId != null) deleteDoc(app, db, oldId)
                insertDoc(app, db, f, text, err)
                done++
                val now = SystemClock.elapsedRealtime()
                if (done % 30 == 0 || now - batchStart > 1500) {
                    // průběžné uložení – přerušená indexace nepřijde nazmar
                    db.setTransactionSuccessful()
                    db.endTransaction()
                    db.beginTransaction()
                    batchStart = now
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val total = DatabaseUtils.queryNumEntries(db, "docs").toInt()
        return "Index aktuální: ${plural(total, "soubor", "soubory", "souborů")}" +
            (if (done > 0) " · nově zpracováno $done" else "") +
            (if (removed.isNotEmpty()) " · odebráno ${removed.size}" else "")
    }

    private suspend fun scan(cr: ContentResolver, tree: Uri, exts: Set<String>, out: ArrayList<FileEntry>) {
        val rootId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: Exception) {
            return
        }
        val rootLabel = Paths.label(tree)
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(rootId to "")
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (docId, rel) = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val c = (try {
                cr.query(childrenUri, PROJ, null, null, null)
            } catch (e: Exception) {
                null
            }) ?: continue
            try {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.addLast(id to (if (rel.isEmpty()) name else "$rel/$name"))
                        continue
                    }
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (Formats.key(ext) !in exts) continue
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val mtime = if (c.isNull(4)) 0L else c.getLong(4)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                    val path = if (rel.isEmpty()) rootLabel else "$rootLabel/$rel"
                    out.add(FileEntry(uri, name, path, ext, size, mtime))
                    if (out.size % 250 == 0) progress("Procházím složky… nalezeno ${out.size}", 0, 0)
                }
            } finally {
                c.close()
            }
        }
    }

    private fun deleteDoc(app: App, db: SQLiteDatabase, id: Long) {
        val args = arrayOf(id.toString())
        db.delete("docs", "id = ?", args)
        db.delete("fts", "docid = ?", args)
        Repo.textFile(app, id).delete()
    }

    private fun insertDoc(app: App, db: SQLiteDatabase, f: FileEntry, text: String, err: String?) {
        val cv = ContentValues().apply {
            put("uri", f.uri)
            put("name", f.name)
            put("path", f.path)
            put("ext", f.ext)
            put("size", f.size)
            put("mtime", f.mtime)
            put("chars", text.length.toLong())
            if (err != null) put("error", err) else putNull("error")
        }
        val id = db.insertOrThrow("docs", null, cv)
        val fv = ContentValues().apply {
            put("docid", id)
            put("name", TextFold.norm(f.name))
            put("body", TextFold.norm(text))
        }
        db.insertOrThrow("fts", null, fv)
        if (text.isNotEmpty()) {
            val file = Repo.textFile(app, id)
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
    }
}
