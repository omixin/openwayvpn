package com.omix.openwayvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omix.openwayvpn.MainActivity
import com.omix.openwayvpn.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class MyVpnService : VpnService() {

    companion object {
        private const val DEBUG_TAG = "openwayDEBUG"
        const val ACTION_CONNECT = "com.omix.openwayvpn.action.CONNECT"
        const val ACTION_DISCONNECT = "com.omix.openwayvpn.action.DISCONNECT"
        const val ACTION_RESTART = "com.omix.openwayvpn.action.RESTART"

        private const val NOTIFICATION_CHANNEL_ID = "openway_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _state = MutableStateFlow("VPN stopped")
        val state: StateFlow<String> = _state.asStateFlow()

        private val _connectionQuality = MutableStateFlow<ConnectionQuality>(ConnectionQuality.Unknown)
        val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

        // Connection uptime tracking
        private var _connectionStartTime = MutableStateFlow<Long>(0L)
        val connectionStartTime: StateFlow<Long> = _connectionStartTime.asStateFlow()

        fun setPermissionDeniedState(context: Context) {
            _state.value = context.getString(R.string.vpn_state_permission_denied)
        }

        /**
         * Formats elapsed time to human-readable string.
         * e.g., "00:05:32" for 5 min 32 sec, "01:23:45" for 1 hour
         */
        fun formatConnectionDuration(startTimeMs: Long): String {
            if (startTimeMs == 0L) return "00:00:00"
            val elapsed = System.currentTimeMillis() - startTimeMs
            val seconds = (elapsed / 1000) % 60
            val minutes = (elapsed / (1000 * 60)) % 60
            val hours = elapsed / (1000 * 60 * 60)
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    enum class ConnectionQuality {
        Unknown,
        Poor,
        Fair,
        Good
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private lateinit var xrayRuntime: XrayRuntime
    private lateinit var tun2SocksRuntime: Tun2SocksRuntime
    
    // Background scope for VPN operations (IO dispatcher)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var connectJob: Job? = null
    
    @Volatile
    private var diagnosticsRunning = false
    private var diagnosticsThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(DEBUG_TAG, "MyVpnService.onCreate()")
        xrayRuntime = XrayRuntime(this)
        tun2SocksRuntime = Tun2SocksRuntime(this)
        if (_state.value.isBlank()) {
            _state.value = getString(R.string.vpn_state_stopped)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(DEBUG_TAG, "onStartCommand action=${intent?.action} startId=$startId")
        when (intent?.action) {
            ACTION_CONNECT -> {
                // Launch connect in background to avoid blocking UI
                connectJob = serviceScope.launch {
                    connect()
                }
            }
            ACTION_DISCONNECT -> disconnect("user_or_ui_request")
            ACTION_RESTART -> {
                Log.d(DEBUG_TAG, "restart requested from notification/UI")
                disconnect("restart_requested", setStoppedState = false, stopService = false)
                serviceScope.launch {
                    delay(180)
                    connect()
                }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.w(DEBUG_TAG, "onRevoke() called by system")
        disconnect("system_revoke")
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.d(DEBUG_TAG, "onDestroy()")
        serviceScope.cancel()
        connectJob?.cancel()
        stopDiagnostics()
        tun2SocksRuntime.stop()
        xrayRuntime.stop()
        tunInterface?.close()
        tunInterface = null
        super.onDestroy()
    }

    private suspend fun connect() {
        Log.d(DEBUG_TAG, "connect() begin")
        
        try {
            if (tunInterface != null) {
                Log.d(DEBUG_TAG, "connect() skipped: tun already established")
                _state.value = getString(R.string.vpn_state_running)
                return
            }

            _state.value = getString(R.string.vpn_state_connecting)

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.10.0.2", 24)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)

            // Important: exclude our own app so xray sockets do not loop back into this VPN.
            try {
                builder.addDisallowedApplication(packageName)
                Log.d(DEBUG_TAG, "connect() disallowed own package=$packageName")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(DEBUG_TAG, "connect() addDisallowedApplication failed", e)
            }

            val vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(DEBUG_TAG, "connect() failed: Builder.establish() returned null")
                _state.value = getString(R.string.vpn_state_error)
                stopSelf()
                return
            }

            Log.d(DEBUG_TAG, "connect() tun established")
            tunInterface = vpnInterface
            
            try {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.d(DEBUG_TAG, "connect() foreground started")
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "connect() failed to start foreground service: ${e.message}", e)
                _state.value = getString(R.string.vpn_state_error)
                disconnect("notification_start_failed", setStoppedState = false)
                return
            }

            val storedVlessUri = VlessProfileStore.getActiveUri(this)
            Log.d(DEBUG_TAG, "connect() vlessUriPresent=${!storedVlessUri.isNullOrBlank()}")

            if (!xrayRuntime.start(storedVlessUri)) {
                Log.e(DEBUG_TAG, "connect() xray failed: ${xrayRuntime.lastError ?: "unknown"}")
                _state.value = getString(R.string.vpn_state_error)
                disconnect("xray_start_failed", setStoppedState = false)
                return
            }

            val socksReady = waitForSocksReady(host = "127.0.0.1", port = 10808, timeoutMs = 2500)
            val xrayAlive = xrayRuntime.isAlive()
            Log.d(
                DEBUG_TAG,
                "connect() socksReady=$socksReady xrayAlive=$xrayAlive " +
                    "xrayLogs=${xrayRuntime.dumpRecentLogs()}"
            )
            if (!socksReady || !xrayAlive) {
                Log.e(
                    DEBUG_TAG,
                    "connect() abort: xray not ready (socksReady=$socksReady xrayAlive=$xrayAlive)"
                )
                _state.value = getString(R.string.vpn_state_error)
                disconnect("xray_not_ready", setStoppedState = false)
                return
            }

            val tunFd = tunInterface
            if (tunFd == null) {
                Log.e(DEBUG_TAG, "connect() tun fd null before tun2socks")
                _state.value = getString(R.string.vpn_state_error)
                disconnect("tun_fd_missing", setStoppedState = false)
                return
            }

            var tunStarted = false
            for (attempt in 1..4) {
                try {
                    if (tun2SocksRuntime.start(
                            tunFd = tunFd,
                            mtu = 1500,
                            socksAddress = "127.0.0.1",
                            socksPort = 10808
                        )
                    ) {
                        tunStarted = true
                        Log.d(DEBUG_TAG, "connect() tun2socks started on attempt=$attempt")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(DEBUG_TAG, "connect() tun2socks attempt=$attempt exception: ${e.message}")
                }
                Log.w(
                    DEBUG_TAG,
                    "connect() tun2socks attempt=$attempt failed: ${tun2SocksRuntime.lastError ?: "unknown"}"
                )
                delay(300)
            }

            if (!tunStarted) {
                Log.e(DEBUG_TAG, "connect() tun2socks failed after retries")
                _state.value = getString(R.string.vpn_state_error)
                disconnect("tun2socks_start_failed", setStoppedState = false)
                return
            }

            Log.d(DEBUG_TAG, "connect() xray+tun2socks started, VPN running")
            _connectionStartTime.value = System.currentTimeMillis()
            _state.value = getString(R.string.vpn_state_running)
            startDiagnostics()
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "connect() unexpected exception: ${e.message}", e)
            _state.value = getString(R.string.vpn_state_error)
            disconnect("unexpected_error", setStoppedState = false)
        }
    }

    private fun disconnect(
        reason: String,
        setStoppedState: Boolean = true,
        stopService: Boolean = true
    ) {
        _connectionQuality.value = ConnectionQuality.Unknown
        _connectionStartTime.value = 0L

        Log.d(DEBUG_TAG, "disconnect() reason=$reason setStoppedState=$setStoppedState stopService=$stopService")
        
        try {
            stopDiagnostics()
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error stopping diagnostics: ${e.message}")
        }
        
        try {
            tun2SocksRuntime.stop()
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error stopping tun2SocksRuntime: ${e.message}")
        }
        
        try {
            xrayRuntime.stop()
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error stopping xrayRuntime: ${e.message}")
        }
        
        try {
            tunInterface?.close()
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error closing tunInterface: ${e.message}")
        }
        tunInterface = null
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(DEBUG_TAG, "Error stopping foreground service: ${e.message}")
        }
        
        if (setStoppedState) {
            _state.value = getString(R.string.vpn_state_stopped)
        }
        if (stopService) {
            try {
                stopSelf()
            } catch (e: Exception) {
                Log.w(DEBUG_TAG, "Error stopping service: ${e.message}")
            }
        }
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, MyVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val restartIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, MyVpnService::class.java).setAction(ACTION_RESTART),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_rotate,
                getString(R.string.vpn_notification_restart),
                restartIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.vpn_notification_stop),
                stopIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.vpn_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        Log.d(DEBUG_TAG, "notification channel created id=$NOTIFICATION_CHANNEL_ID")
    }

    private suspend fun waitForSocksReady(host: String, port: Int, timeoutMs: Long): Boolean {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < timeoutMs) {
            if (probeTcpPort(host, port, 250) ||
                probeTcpPort("localhost", port, 250)
            ) {
                return true
            }
            delay(120)
        }
        return false
    }

    private fun probeTcpPort(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun probeTcpPortOffMainThread(host: String, port: Int, timeoutMs: Int): Boolean {
        val connected = AtomicBoolean(false)
        val probe = Thread(
            {
                val result = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), timeoutMs)
                        true
                    }
                } catch (_: Throwable) {
                    false
                }
                connected.set(result)
            },
            "openway-socks-probe"
        )
        probe.isDaemon = true
        probe.start()
        probe.join((timeoutMs + 120).toLong())
        return connected.get()
    }

    private fun startDiagnostics() {
        stopDiagnostics()
        diagnosticsRunning = true
        diagnosticsThread = Thread(
            {
                var tick = 0
                try {
                    while (diagnosticsRunning && tick < 20) {
                        tick++
                        val socksOpen = try {
                            isTcpPortOpen("127.0.0.1", 10808, 800)
                        } catch (e: Exception) {
                            Log.w(DEBUG_TAG, "diag isTcpPortOpen failed: ${e.message}")
                            false
                        }
                        
                        val socksConnectOk = if (socksOpen) {
                            try {
                                testSocksConnect(
                                    host = "127.0.0.1",
                                    port = 10808,
                                    targetHost = "connectivitycheck.gstatic.com",
                                    targetPort = 80,
                                    timeoutMs = 1800
                                )
                            } catch (e: Exception) {
                                Log.w(DEBUG_TAG, "diag testSocksConnect failed: ${e.message}")
                                false
                            }
                        } else false
                        
                        val proxyHttp = if (socksConnectOk) {
                            try {
                                testHttpViaSocks("127.0.0.1", 10808)
                            } catch (e: Exception) {
                                Log.w(DEBUG_TAG, "diag testHttpViaSocks failed: ${e.message}")
                                false
                            }
                        } else false
                        
                        val quality = when {
                            socksConnectOk && proxyHttp -> ConnectionQuality.Good
                            socksConnectOk -> ConnectionQuality.Fair
                            socksOpen -> ConnectionQuality.Poor
                            else -> ConnectionQuality.Unknown
                        }
                        _connectionQuality.value = quality

                        val stats = try {
                            tun2SocksRuntime.getStatsSnapshot()?.joinToString(",") ?: "n/a"
                        } catch (e: Exception) {
                            Log.w(DEBUG_TAG, "diag getStatsSnapshot failed: ${e.message}")
                            "error"
                        }
                        
                        Log.d(
                            DEBUG_TAG,
                            "diag tick=$tick socksOpen=$socksOpen socksConnectOk=$socksConnectOk proxyHttp=$proxyHttp tunStats=$stats"
                        )
                        Thread.sleep(1500)
                    }
                } catch (e: Exception) {
                    Log.e(DEBUG_TAG, "Diagnostics thread exception: ${e.message}", e)
                } finally {
                    diagnosticsRunning = false
                }
            },
            "openway-diagnostics"
        ).also { it.start() }
    }

    private fun stopDiagnostics() {
        diagnosticsRunning = false
        diagnosticsThread?.join(300)
        diagnosticsThread = null
    }

    private fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun testSocksConnect(
        host: String,
        port: Int,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                // Greeting: SOCKS5, one method, no auth.
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val response = ByteArray(2)
                val read = input.read(response)
                if (!(read == 2 && response[0] == 0x05.toByte() && response[1] == 0x00.toByte())) {
                    return false
                }

                val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
                val req = ByteArray(7 + hostBytes.size)
                req[0] = 0x05 // VER
                req[1] = 0x01 // CMD CONNECT
                req[2] = 0x00 // RSV
                req[3] = 0x03 // ATYP DOMAIN
                req[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
                req[5 + hostBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
                req[6 + hostBytes.size] = (targetPort and 0xFF).toByte()
                out.write(req)
                out.flush()

                val head = ByteArray(4)
                if (input.read(head) != 4) return false
                if (head[0] != 0x05.toByte() || head[1] != 0x00.toByte()) return false

                val atyp = head[3].toInt() and 0xFF
                val addrLen = when (atyp) {
                    0x01 -> 4
                    0x04 -> 16
                    0x03 -> input.read().let { len -> if (len < 0) return false else len }
                    else -> return false
                }
                var skip = addrLen + 2 // BND.ADDR + BND.PORT
                while (skip > 0) {
                    val skipped = input.skip(skip.toLong()).toInt()
                    if (skipped <= 0) {
                        // fallback read if skip returns 0
                        val b = input.read()
                        if (b < 0) return false
                        skip -= 1
                    } else {
                        skip -= skipped
                    }
                }
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun testHttpViaSocks(host: String, port: Int): Boolean {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
            val conn = URL("http://connectivitycheck.gstatic.com/generate_204")
                .openConnection(proxy)
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.getInputStream().use { _ -> }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
