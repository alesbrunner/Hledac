package cz.ales.dochledac

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var folders: List<String>
        get() = (sp.getStringSet("folders", null) ?: emptySet()).sorted()
        set(v) = sp.edit().putStringSet("folders", HashSet(v)).apply()

    var exts: Set<String>
        get() = sp.getStringSet("exts", null)?.toSet() ?: DEFAULT_EXTS
        set(v) = sp.edit().putStringSet("exts", HashSet(v)).apply()

    var autoUpdate: Boolean
        get() = sp.getBoolean("auto", true)
        set(v) = sp.edit().putBoolean("auto", v).apply()

    companion object {
        val DEFAULT_EXTS = setOf("txt", "srt", "md", "pdf", "docx")
    }
}
