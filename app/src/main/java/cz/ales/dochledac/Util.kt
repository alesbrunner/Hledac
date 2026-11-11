package cz.ales.dochledac

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

fun plural(n: Int, one: String, few: String, many: String): String {
    val w = when {
        n == 1 -> one
        n in 2..4 -> few
        else -> many
    }
    return "$n $w"
}

fun formatChars(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1f mil.", n / 1_000_000.0)
    n >= 1_000 -> "${n / 1000} tis."
    else -> n.toString()
}

fun openExternal(ctx: Context, doc: Doc) {
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(doc.uri), Formats.mime(doc.ext))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent.createChooser(i, "Otevřít v…"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Žádná aplikace neumí tento soubor otevřít", Toast.LENGTH_SHORT).show()
    }
}

fun Activity.hideKeyboard() {
    val v = currentFocus ?: return
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
}

object Paths {
    /** "primary:Documents/Knihy" -> "Interní/Documents/Knihy" */
    fun label(tree: Uri): String = try {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val vol = id.substringBefore(':')
        val rest = id.substringAfter(':', "")
        val v = if (vol == "primary") "Interní" else "SD $vol"
        if (rest.isEmpty()) v else "$v/$rest"
    } catch (e: Exception) {
        tree.toString()
    }
}
