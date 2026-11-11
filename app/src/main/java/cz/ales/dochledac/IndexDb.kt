package cz.ales.dochledac

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

class IndexDb(ctx: Context) : SQLiteOpenHelper(ctx, "index.db", null, 1) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE docs(id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT NOT NULL UNIQUE, " +
                "name TEXT NOT NULL, path TEXT NOT NULL, ext TEXT NOT NULL, size INTEGER NOT NULL, " +
                "mtime INTEGER NOT NULL, chars INTEGER NOT NULL DEFAULT 0, error TEXT)"
        )
        // Fulltextový index: name a body obsahují normalizovaný text (TextFold.norm)
        db.execSQL("CREATE VIRTUAL TABLE fts USING fts4(name, body)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS docs")
        db.execSQL("DROP TABLE IF EXISTS fts")
        onCreate(db)
    }
}

class Doc(
    val id: Long,
    val uri: String,
    val name: String,
    val path: String,
    val ext: String,
    val size: Long,
    val mtime: Long,
    val chars: Long,
    val error: String?,
)

object Repo {
    const val COLS = "id, uri, name, path, ext, size, mtime, chars, error"

    fun fromCursor(c: Cursor) = Doc(
        c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
        c.getLong(5), c.getLong(6), c.getLong(7), if (c.isNull(8)) null else c.getString(8),
    )

    fun textFile(ctx: Context, id: Long) = File(File(ctx.filesDir, "text"), "$id.txt")

    fun doc(app: App, id: Long): Doc? =
        app.db.readableDatabase.rawQuery("SELECT $COLS FROM docs WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) fromCursor(it) else null
        }

    fun loadText(ctx: Context, id: Long): String {
        val f = textFile(ctx, id)
        return if (f.exists()) f.readText() else ""
    }

    /** počet souborů, celkový počet znaků */
    fun stats(app: App): Pair<Int, Long> =
        app.db.readableDatabase.rawQuery("SELECT COUNT(*), IFNULL(SUM(chars), 0) FROM docs", null).use {
            it.moveToFirst()
            it.getInt(0) to it.getLong(1)
        }

    fun clear(app: App) {
        val db = app.db.writableDatabase
        db.execSQL("DELETE FROM docs")
        db.execSQL("DELETE FROM fts")
        File(app.filesDir, "text").deleteRecursively()
    }
}
