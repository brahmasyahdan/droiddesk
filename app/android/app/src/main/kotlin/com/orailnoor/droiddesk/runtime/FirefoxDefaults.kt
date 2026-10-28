package com.orailnoor.droiddesk.runtime

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Makes desktop Firefox fit a phone screen.
 *
 * Firefox will not make its window narrower than about 725 CSS pixels (a 500px window minimum
 * plus the toolbar). At the desktop's 140 DPI that is wider than a portrait phone's X screen, so
 * the window could neither fit nor be maximized. A 0.6 scale brings the minimum under the
 * portrait width while text stays legible, and pages see a desktop-sized viewport. It is a
 * default pref, so a value set in about:config still wins.
 */
object FirefoxDefaults {
    private const val TAG = "FirefoxDefaults"
    private const val PREFS_FILE = "droiddesk.js"

    val prefs = """
        // Managed by DroidDesk: lets the browser window fit a portrait phone screen.
        pref("layout.css.devPixelsPerPx", "0.6");
    """.trimIndent() + "\n"

    /** Writes the default prefs into an installed Firefox at [firefoxDir] (its lib directory). */
    fun installPrefs(firefoxDir: File) {
        if (!File(firefoxDir, "firefox").exists()) return
        runCatching {
            File(firefoxDir, "defaults/pref").apply { mkdirs() }
                .resolve(PREFS_FILE)
                .takeIf { !it.exists() || it.readText() != prefs }
                ?.writeText(prefs)
        }.onFailure { Log.w(TAG, "Could not install Firefox defaults", it) }
    }

    /**
     * Firefox reopens at the size it saved on exit, which also undoes the maximize DroidDesk
     * applies when a window opens. Saving "maximized" before the desktop starts makes every
     * profile open full screen; a size chosen later lasts until the next desktop start.
     */
    fun openProfilesMaximized(homeDir: File) {
        listOf(".config/mozilla/firefox", ".mozilla/firefox")
            .mapNotNull { File(homeDir, it).listFiles() }
            .flatMap { it.toList() }
            .map { File(it, "xulstore.json") }
            .filter { it.isFile }
            .forEach { store ->
                runCatching {
                    val json = JSONObject(store.readText())
                    val window = json.optJSONObject("chrome://browser/content/browser.xhtml")
                        ?.optJSONObject("main-window")
                        ?: return@runCatching
                    if (window.optString("sizemode") == "maximized") return@runCatching
                    window.put("sizemode", "maximized")
                    store.writeText(json.toString())
                }.onFailure { Log.w(TAG, "Could not update ${store.absolutePath}", it) }
            }
    }
}
