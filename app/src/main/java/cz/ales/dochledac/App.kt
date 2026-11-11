package cz.ales.dochledac

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    val db: IndexDb by lazy { IndexDb(this) }
    val prefs: Prefs by lazy { Prefs(this) }

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        PDFBoxResourceLoader.init(applicationContext)
    }
}
