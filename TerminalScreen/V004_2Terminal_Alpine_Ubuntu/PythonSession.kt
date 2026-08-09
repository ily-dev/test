// core/main/src/main/java/com/rk/terminal/ui/screens/terminal/PythonSession.kt
package com.rk.terminal.ui.screens.terminal

import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir
import com.rk.libcommons.localLibDir
import com.rk.libcommons.pendingCommand
import com.rk.settings.Settings
import com.rk.terminal.App.Companion.getTempDir
import com.rk.terminal.BuildConfig
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.meinname.ssh.Globals
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.rk.libcommons.ubuntuHomeDir
import java.io.File

object PythonSession {
    
    // ============================================================
    // ★ LOKALE SHOWLOG FUNKTION
    // ============================================================
    private fun showLog(title: String, message: String) {
        MainActivity.showLog("PythonSession", "[$title] $message")
    }

    // ============================================================
    // ★ PYTHON-SESSION ERSTELLEN (FÜR UBUNTU)
    // ============================================================
    @JvmStatic
    fun createPythonSession(
        activity: MainActivity,
        sessionClient: TerminalSessionClient,
        session_id: String = "python_log"
    ): TerminalSession {
        
        showLog("Info", "🐍 Erstelle Python-Session (Ubuntu): $session_id")
        
        with(activity) {
            // ============================================================
            // ★ ENVIRONMENT VARIABLES
            // ============================================================
            val env: MutableList<String> = mutableListOf(
                "PATH=/system/bin:/system/xbin:${System.getenv("PATH")}:/sbin:${localBinDir().absolutePath}",
                "HOME=/root",  // ★ ★ ★ Ubuntu: HOME auf /root ★ ★ ★
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "BIN=${localBinDir()}",
                "DEBUG=${BuildConfig.DEBUG}",
                "PREFIX=${filesDir.parentFile!!.path}",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}",
                "NATIVE_LIB_DIR=${applicationInfo.nativeLibraryDir}",
                "PKG=${packageName}",
                "RISH_APPLICATION_ID=${packageName}",
                "PKG_PATH=${applicationInfo.sourceDir}",
                "PROOT_TMP_DIR=${getTempDir().child(session_id).also { if (!it.exists()) it.mkdirs() }}",
                "TMPDIR=${getTempDir().absolutePath}",
                "SSHD_PORT=${Globals.SSHD_PORT}",
                "SSHD_ENABLED=${Globals.SSHD_ENABLED}",
                "FTP_PORT=${Globals.FTP_PORT}",
                "FTP_ENABLED=${Globals.FTP_ENABLED}"
            )

            showLog("Debug", "🔧 ${env.size} Environment-Variablen gesetzt")

            // ============================================================
            // ★ PROOT LOADER (für Ubuntu)
            // ============================================================
            if (!Globals.isAndroid()) {
                if (File(applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()) {
                    env.add("PROOT_LOADER32=${applicationInfo.nativeLibraryDir}/libproot-loader32.so")
                    showLog("Debug", "🔧 PROOT_LOADER32 gesetzt")
                }

                if (File(applicationInfo.nativeLibraryDir).child("libproot-loader.so").exists()) {
                    env.add("PROOT_LOADER=${applicationInfo.nativeLibraryDir}/libproot-loader.so")
                    showLog("Debug", "🔧 PROOT_LOADER gesetzt")
                }

                if (Settings.seccomp) {
                    env.add("SECCOMP=1")
                    showLog("Debug", "🔧 SECCOMP aktiviert")
                }
            } else {
                showLog("Debug", "📟 Android-Modus: Keine Proot-Loader benötigt")
            }

            // ============================================================
            // ★ WORKING DIRECTORY (Ubuntu Home)
            // ============================================================
            val workingDir = ubuntuHomeDir().path  // ★ ★ ★ Ubuntu Home ★ ★ ★
            showLog("Debug", "📁 WorkingDir: $workingDir")

            // ============================================================
            // ★ INIT-HOST FILE (Ubuntu)
            // ============================================================
            val initFileUbuntu: File = localBinDir().child("init-host-ubuntu")
            
            // ============================================================
            // ★ SHELL & ARGS (UBUNTU)
            // ============================================================
            val shell = "/system/bin/sh"
            val args: Array<String> = arrayOf("-c", initFileUbuntu.absolutePath)

            showLog("Info", "🐧 Starte Ubuntu Linux mit init-host")
            showLog("Info", "✅ Python-Session (Ubuntu) erstellt: shell=$shell")
            showLog("Debug", "📋 Args: ${args.joinToString(" ")}")

            // ============================================================
            // ★ TERMINAL SESSION ERSTELLEN
            // ============================================================
            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            )
        }
    }
}