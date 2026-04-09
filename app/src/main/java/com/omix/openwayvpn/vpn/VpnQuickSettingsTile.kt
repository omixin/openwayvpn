package com.omix.openwayvpn.vpn

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile для быстрого вкл/выкл VPN из шторки.
 * Доступна на Android 7.0+ (API 24+)
 */
@RequiresApi(Build.VERSION_CODES.N)
class VpnQuickSettingsTile : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onStartListening() {
        super.onStartListening()
        // Start polling to keep the tile state up to date
        startStatePolling()
    }

    private var stateJob: kotlinx.coroutines.Job? = null

    private fun startStatePolling() {
        stateJob?.cancel()
        stateJob = scope.launch {
            while (isActive) {
                updateTileState()
                delay(1000)
            }
        }
    }

    override fun onClick() {
        super.onClick()
        toggleVpn()
    }

    private fun toggleVpn() {
        val currentState = qsTile.state
        if (currentState == Tile.STATE_ACTIVE) {
            scope.launch {
                sendVpnCommandIntent(MyVpnService.ACTION_DISCONNECT)
                delay(300)
                updateTileState()
            }
        } else {
            scope.launch {
                sendVpnCommandIntent(MyVpnService.ACTION_CONNECT)
                delay(300)
                updateTileState()
            }
        }
    }

    private fun sendVpnCommandIntent(action: String) {
        val intent = Intent(this, MyVpnService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }
    }

    private fun updateTileState() {
        val state = MyVpnService.state.value.lowercase()
        val isRunning = state.contains("running") ||
                        state.contains("работает") ||
                        state == "vpn running" ||
                        state == "vpn работает"

        qsTile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.label = "OpenWay VPN"
        qsTile.updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        stateJob?.cancel()
        stateJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stateJob?.cancel()
        scope.cancel()
    }
}
