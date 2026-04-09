package com.omix.openwayvpn.vpn

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.ArrayDeque

class XrayRuntime(
    private val context: Context
) {
    companion object {
        private const val DEBUG_TAG = "openwayDEBUG"
    }

    private var process: Process? = null
    private var outputThread: Thread? = null
    private val recentLogs = ArrayDeque<String>()
    var lastError: String? = null
        private set

    fun start(vlessUri: String?): Boolean {
        return try {
            if (process?.isAlive == true) return true

            lastError = null
            val runtimeDir = File(context.filesDir, "xray_runtime").apply { mkdirs() }
            val xrayBin = resolveExecutable() ?: run {
                Log.e(DEBUG_TAG, "XrayRuntime.start aborted: $lastError")
                return false
            }
            
            try {
                copyAsset("xray/geoip.dat", File(runtimeDir, "geoip.dat"), executable = false)
                copyAsset("xray/geosite.dat", File(runtimeDir, "geosite.dat"), executable = false)
            } catch (e: Exception) {
                lastError = "Failed to copy assets: ${e.message}"
                Log.e(DEBUG_TAG, "XrayRuntime.start asset copy failed", e)
                return false
            }

            val profile = parseProfile(vlessUri)
            if (!vlessUri.isNullOrBlank() && profile == null) {
                Log.e(DEBUG_TAG, "XrayRuntime.start invalid profile: $lastError")
                return false
            }
            
            val configFile = File(runtimeDir, "config.json")
            try {
                configFile.writeText(XrayConfigFactory.build(profile), Charsets.UTF_8)
            } catch (e: Exception) {
                lastError = "Failed to write config: ${e.message}"
                Log.e(DEBUG_TAG, "XrayRuntime.start config write failed", e)
                return false
            }
            
            Log.d(DEBUG_TAG, "XrayRuntime.start executable=${xrayBin.absolutePath}")
            Log.d(DEBUG_TAG, "XrayRuntime.start config=${configFile.absolutePath}")
            Log.d(DEBUG_TAG, "XrayRuntime.start profile=${profile?.name ?: profile?.host ?: "bootstrap"}")

            process = ProcessBuilder(
                xrayBin.absolutePath,
                "run",
                "-config",
                configFile.absolutePath
            ).redirectErrorStream(true)
                .directory(runtimeDir)
                .start()
            
            try {
                startOutputReader(process!!)
            } catch (e: Exception) {
                lastError = "Failed to start output reader: ${e.message}"
                Log.e(DEBUG_TAG, "XrayRuntime.start output reader failed", e)
                process?.destroy()
                process = null
                return false
            }

            if (process?.isAlive != true) {
                process?.destroy()
                process = null
                lastError = "process exited immediately"
                Log.e(DEBUG_TAG, "XrayRuntime.start failed: $lastError")
                return false
            }
            Log.d(DEBUG_TAG, "XrayRuntime.start success")
            true
        } catch (t: Throwable) {
            try {
                process?.destroy()
            } catch (e: Exception) {
                Log.w(DEBUG_TAG, "Error destroying process: ${e.message}")
            }
            process = null
            lastError = "${t.javaClass.simpleName}: ${t.message}"
            Log.e(DEBUG_TAG, "XrayRuntime.start exception", t)
            false
        }
    }

    fun stop() {
        Log.d(DEBUG_TAG, "XrayRuntime.stop")
        try {
            process?.destroy()
            runCatching { process?.waitFor() }
            if (process?.isAlive == true) {
                process?.destroyForcibly()
            }
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error during XrayRuntime.stop: ${e.message}")
        } finally {
            process = null
            outputThread = null
        }
    }

    private fun copyAsset(assetPath: String, target: File, executable: Boolean): File {
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (executable) {
            target.setExecutable(true, true)
        }
        return target
    }

    private fun resolveExecutable(): File? {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        Log.d(DEBUG_TAG, "nativeLibraryDir=${nativeLibDir.absolutePath}")
        val libs = nativeLibDir.list()?.joinToString(",").orEmpty()
        Log.d(DEBUG_TAG, "nativeLibraryDir files=$libs")

        val nativeLibXray = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        if (nativeLibXray.exists()) {
            Log.d(DEBUG_TAG, "XrayRuntime executable source=nativeLibraryDir")
            return nativeLibXray
        }
        lastError = "libxray.so not found in nativeLibraryDir"
        return null
    }

    private fun parseProfile(vlessUri: String?): VlessProfile? {
        if (vlessUri.isNullOrBlank()) return null
        val parsed = VlessUriParser.parse(vlessUri)
        return if (parsed.isSuccess) {
            parsed.getOrNull()
        } else {
            lastError = "Invalid vless URI: ${parsed.exceptionOrNull()?.message}"
            null
        }
    }

    fun isAlive(): Boolean = process?.isAlive == true

    fun dumpRecentLogs(): String {
        synchronized(recentLogs) {
            return recentLogs.joinToString(" | ")
        }
    }

    private fun startOutputReader(process: Process) {
        outputThread = Thread(
            {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            pushLogLine(line)
                            Log.d(DEBUG_TAG, "xray> $line")
                        }
                    }
                } catch (t: Throwable) {
                    pushLogLine("reader exception: ${t.message}")
                } finally {
                    val exitCode = runCatching { process.exitValue() }.getOrNull()
                    pushLogLine("process_exit=$exitCode")
                }
            },
            "openway-xray-output"
        ).also { it.start() }
    }

    private fun pushLogLine(line: String) {
        synchronized(recentLogs) {
            if (recentLogs.size >= 60) {
                recentLogs.removeFirst()
            }
            recentLogs.addLast(line)
        }
    }
}
