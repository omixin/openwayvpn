package com.omix.openwayvpn.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import hev.sockstun.TProxyService
import java.io.File

class Tun2SocksRuntime(
    private val context: Context
) {
    companion object {
        private const val DEBUG_TAG = "openwayDEBUG"
    }

    private var workerThread: Thread? = null
    private var dupTunFd: ParcelFileDescriptor? = null
    @Volatile
    private var running = false
    var lastError: String? = null
        private set

    fun start(
        tunFd: ParcelFileDescriptor,
        mtu: Int,
        socksAddress: String,
        socksPort: Int
    ): Boolean {
        if (running) return true

        return try {
            lastError = null
            val configPath = writeConfig(mtu = mtu, socksAddress = socksAddress, socksPort = socksPort)
            val localDupFd = ParcelFileDescriptor.dup(tunFd.fileDescriptor)
            dupTunFd = localDupFd
            val fdValue = localDupFd.fd
            Log.d(DEBUG_TAG, "Tun2SocksRuntime.start config=$configPath fd=$fdValue")
            running = true

            val thread = Thread(
                {
                    try {
                        TProxyService.start(configPath, fdValue)
                        Log.d(DEBUG_TAG, "Tun2SocksRuntime worker returned from start()")
                    } catch (t: Throwable) {
                        lastError = "${t.javaClass.simpleName}: ${t.message}"
                        Log.e(DEBUG_TAG, "Tun2SocksRuntime worker exception", t)
                        running = false
                    }
                },
                "openway-tun2socks"
            )

            workerThread = thread
            thread.start()
            
            // Запускаем статистику в отдельном потоке с обработкой ошибок
            try {
                Thread {
                    try {
                        Thread.sleep(1200)
                        val stats = TProxyService.getStats()
                        Log.d(DEBUG_TAG, "Tun2SocksRuntime.stats=${stats.joinToString(",")}")
                    } catch (e: Exception) {
                        Log.w(DEBUG_TAG, "Tun2SocksRuntime.stats unavailable: ${e.message}")
                    }
                }.start()
            } catch (e: Exception) {
                Log.w(DEBUG_TAG, "Failed to start stats thread: ${e.message}")
            }
            
            true
        } catch (t: Throwable) {
            lastError = "${t.javaClass.simpleName}: ${t.message}"
            Log.e(DEBUG_TAG, "Tun2SocksRuntime.start failed", t)
            running = false
            false
        }
    }

    fun stop() {
        Log.d(DEBUG_TAG, "Tun2SocksRuntime.stop")
        try {
            val stopper = Thread({
                runCatching { TProxyService.stop() }
            }, "openway-tun2socks-stop")
            stopper.isDaemon = true
            stopper.start()
            stopper.join(500)
            if (stopper.isAlive) {
                Log.w(DEBUG_TAG, "Tun2SocksRuntime.stop timeout")
            }

            workerThread?.join(500)
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error during Tun2SocksRuntime.stop: ${e.message}")
        } finally {
            workerThread = null
            runCatching { dupTunFd?.close() }
            dupTunFd = null
            running = false
        }
    }

    fun getStatsSnapshot(): LongArray? {
        return runCatching { TProxyService.getStats() }
            .onFailure { Log.w(DEBUG_TAG, "Tun2SocksRuntime.getStats failed: ${it.message}") }
            .getOrNull()
    }

    private fun writeConfig(mtu: Int, socksAddress: String, socksPort: Int): String {
        val file = File(context.filesDir, "xray_runtime/tproxy.yml").apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }
        val content = """
            misc:
              task-stack-size: 24576
            socks5:
              mtu: $mtu
              address: '$socksAddress'
              port: $socksPort
              udp: udp
        """.trimIndent()
        file.writeText(content, Charsets.UTF_8)
        return file.absolutePath
    }
}
