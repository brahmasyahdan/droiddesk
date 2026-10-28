package com.orailnoor.droiddesk.runtime

import android.app.ActivityManager
import android.content.Context

/**
 * Lighter desktop defaults for phones with 4 GB of RAM or less.
 *
 * Android's low memory killer ends the whole Linux session at once, so on small phones it pays
 * to give up desktop effects: the xfwm4 compositor keeps an off-screen copy of every window and
 * Thunar's thumbnailer decodes every image in a folder, and the power manager has nothing to
 * manage on Android. The power manager is kept out of the session by an autostart override; the
 * other XFCE changes are applied by an
 * autostart script inside the running session, where xfconfd is available, and undone the same
 * way once when the mode is switched off, so settings the user changed by hand are kept.
 */
object LowMemoryProfile {
    private const val PREFS = "droiddesk_performance"
    private const val KEY_ENABLED = "low_memory_mode"

    // Phones sold as 4 GB report roughly 3.5–3.8 GB of total memory.
    private const val RECOMMENDED_BELOW_MB = 4608L

    private const val SCRIPT_DIR = ".config/droiddesk"
    private const val SCRIPT = "$SCRIPT_DIR/low-memory.sh"
    private const val APPLIED_MARKER = "$SCRIPT_DIR/low-memory-applied"
    private const val AUTOSTART_DIR = ".config/autostart"
    private const val AUTOSTART = "$AUTOSTART_DIR/droiddesk-low-memory.desktop"

    // xfce4-power-manager has nothing to manage on Android, and the upowerd it starts polls a
    // battery helper at Termux's original path every 5 seconds, failing each time.
    private const val POWER_MANAGER_OVERRIDE = "$AUTOSTART_DIR/xfce4-power-manager.desktop"
    private const val OVERRIDE_TAG = "# Managed by DroidDesk low memory mode"

    fun totalRamMb(context: Context): Long {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    fun isRecommended(context: Context): Boolean =
        totalRamMb(context) in 1 until RECOMMENDED_BELOW_MB

    /** On by default for phones with little RAM, until the user chooses otherwise. */
    fun isEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        return if (prefs.contains(KEY_ENABLED)) {
            prefs.getBoolean(KEY_ENABLED, false)
        } else {
            isRecommended(context)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Every clipboard sync tick starts an xclip process; poll less often when memory is tight. */
    fun clipboardIntervalMs(context: Context): Long = if (isEnabled(context)) 1_500L else 800L

    /**
     * Shell command that prepares the XFCE changes for the next session start.
     *
     * [hostHome] is the desktop user's home as the caller sees it (inside the rootfs for chroot
     * sessions), [sessionHome] is the same directory as the desktop session sees it.
     */
    fun prepareCommand(hostHome: String, sessionHome: String, enabled: Boolean): String {
        val script = shellQuote("$hostHome/$SCRIPT")
        val autostart = shellQuote("$hostHome/$AUTOSTART")
        val appliedMarker = shellQuote("$hostHome/$APPLIED_MARKER")
        val powerOverride = shellQuote("$hostHome/$POWER_MANAGER_OVERRIDE")
        // Built line by line: heredoc terminators must start at column 0.
        fun writeFiles(body: String) = listOf(
            "mkdir -p ${shellQuote("$hostHome/$SCRIPT_DIR")} ${shellQuote("$hostHome/$AUTOSTART_DIR")}",
            "cat > $script <<'DROIDDESK_EOF'",
            body,
            "DROIDDESK_EOF",
            "chmod 0755 $script",
            "cat > $autostart <<'DROIDDESK_EOF'",
            autostartEntry("$sessionHome/$SCRIPT"),
            "DROIDDESK_EOF",
        ).joinToString("\n")

        return if (enabled) {
            listOf(
                writeFiles(applyScript("$sessionHome/$APPLIED_MARKER")),
                // Takes effect for this session start; never replaces the user's own override.
                "if [ ! -e $powerOverride ] || grep -q ${shellQuote(OVERRIDE_TAG)} $powerOverride; then",
                "printf '%s\\n' '[Desktop Entry]' ${shellQuote(OVERRIDE_TAG)} 'Hidden=true' > $powerOverride",
                "fi",
            ).joinToString("\n")
        } else {
            listOf(
                "if [ -e $appliedMarker ]; then",
                writeFiles(revertScript("$sessionHome/$APPLIED_MARKER", "$sessionHome/$AUTOSTART")),
                "else",
                "rm -f $script $autostart",
                "fi",
                "if grep -q ${shellQuote(OVERRIDE_TAG)} $powerOverride 2>/dev/null; then rm -f $powerOverride; fi",
            ).joinToString("\n")
        }
    }

    private fun applyScript(appliedMarker: String): String = """
        #!/bin/sh
        # Managed by DroidDesk: Settings > Low memory mode.
        set_prop() { xfconf-query "${'$'}@" >/dev/null 2>&1 || true; }
        # The compositor keeps an off-screen copy of every window.
        set_prop -c xfwm4 -p /general/use_compositing -n -t bool -s false
        # Thumbnails decode every image in a folder.
        set_prop -c thunar -p /misc-thumbnail-mode -n -t string -s THUNAR_THUMBNAIL_MODE_NEVER
        pkill -x tumblerd >/dev/null 2>&1 || true
        touch ${shellQuote(appliedMarker)}
    """.trimIndent()

    private fun revertScript(appliedMarker: String, autostart: String): String = """
        #!/bin/sh
        # Managed by DroidDesk: restores the XFCE defaults once, then removes itself.
        xfconf-query -c xfwm4 -p /general/use_compositing -r >/dev/null 2>&1 || true
        xfconf-query -c thunar -p /misc-thumbnail-mode -r >/dev/null 2>&1 || true
        rm -f ${shellQuote(appliedMarker)} ${shellQuote(autostart)} "${'$'}0"
    """.trimIndent()

    private fun autostartEntry(sessionScript: String): String = """
        [Desktop Entry]
        Type=Application
        Name=DroidDesk low memory mode
        Exec=sh $sessionScript
        OnlyShowIn=XFCE;
        NoDisplay=true
    """.trimIndent()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun shellQuote(input: String): String = "'" + input.replace("'", "'\"'\"'") + "'"
}
