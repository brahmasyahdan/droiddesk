package com.orailnoor.droiddesk.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Runs the generated shell with the host's /bin/sh and a fake xfconf-query. */
class LowMemoryProfileTest {
    @get:Rule val temp = TemporaryFolder()

    private val home by lazy { temp.newFolder("home with 'quote") }
    private val script get() = File(home, ".config/droiddesk/low-memory.sh")
    private val autostart get() = File(home, ".config/autostart/droiddesk-low-memory.desktop")
    private val marker get() = File(home, ".config/droiddesk/low-memory-applied")
    private val xfconfLog get() = File(temp.root, "xfconf.log")
    private val powerOverride get() = File(home, ".config/autostart/xfce4-power-manager.desktop")

    private fun sh(command: String, vararg args: String) {
        val bin = temp.root.resolve("bin").apply { mkdirs() }
        bin.resolve("xfconf-query").apply {
            writeText("#!/bin/sh\necho \"\$*\" >> '${xfconfLog.absolutePath}'\n")
            setExecutable(true)
        }
        val process = ProcessBuilder("/bin/sh", "-c", command, "sh", *args)
            .redirectErrorStream(true)
            .apply { environment()["PATH"] = "${bin.absolutePath}:/usr/bin:/bin" }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals("shell failed: $output", 0, process.waitFor())
    }

    private fun prepare(enabled: Boolean) =
        sh(LowMemoryProfile.prepareCommand(home.absolutePath, home.absolutePath, enabled))

    private fun runAutostart() {
        val exec = autostart.readLines().single { it.startsWith("Exec=") }
        assertEquals("Exec=sh ${script.absolutePath}", exec)
        sh("sh \"\$1\"", script.absolutePath)
    }

    @Test
    fun enablingAppliesSettingsInTheSession() {
        prepare(enabled = true)
        assertTrue(script.canExecute())
        assertTrue(script.readText().startsWith("#!/bin/sh\n"))

        runAutostart()
        val calls = xfconfLog.readText()
        assertTrue(calls, calls.contains("-c xfwm4 -p /general/use_compositing -n -t bool -s false"))
        assertTrue(calls, calls.contains("THUNAR_THUMBNAIL_MODE_NEVER"))
        assertTrue(marker.exists())
        assertTrue(powerOverride.readLines().contains("Hidden=true"))
    }

    @Test
    fun keepsAPowerManagerOverrideTheUserWrote() {
        powerOverride.parentFile!!.mkdirs()
        powerOverride.writeText("[Desktop Entry]\nHidden=false\n")
        prepare(enabled = true)
        prepare(enabled = false)
        assertEquals("[Desktop Entry]\nHidden=false\n", powerOverride.readText())
    }

    @Test
    fun disablingRevertsOnceAndCleansUp() {
        prepare(enabled = true)
        runAutostart()

        prepare(enabled = false)
        assertFalse(powerOverride.exists())
        runAutostart()
        assertTrue(xfconfLog.readText().contains("-c xfwm4 -p /general/use_compositing -r"))
        assertFalse(marker.exists())
        assertFalse(script.exists())
        assertFalse(autostart.exists())

        // Nothing left to revert: later sessions leave the user's XFCE settings alone.
        prepare(enabled = false)
        assertFalse(autostart.exists())
    }

    @Test
    fun disablingWithoutPriorApplyTouchesNothing() {
        prepare(enabled = false)
        assertFalse(script.exists())
        assertFalse(autostart.exists())
    }
}
