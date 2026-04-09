package com.omix.openwayvpn.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import com.omix.openwayvpn.ui.theme.OpenwayvpnTheme
import com.omix.openwayvpn.vpn.MyVpnService

internal const val DEBUG_TAG = "openwayDEBUG"

enum class AppScreen {
    VPN,
    PROFILES,
    // TODO/TEST: AppScreen.TEST - Interactive background animation feature (disabled for now)
    // TEST
    SETTINGS,
    ABOUT
}

enum class VpnUiStatus {
    OFFLINE,
    CONNECTING,
    ONLINE
}

data class StatusWordUi(
    val text: String,
    val color: Color
)

fun sendVpnCommand(context: Context, action: String) {
    Log.d(DEBUG_TAG, "sendVpnCommand action=$action")
    val serviceIntent = Intent(context, MyVpnService::class.java).apply {
        this.action = action
    }
    if (action == MyVpnService.ACTION_CONNECT) {
        ContextCompat.startForegroundService(context, serviceIntent)
    } else {
        context.startService(serviceIntent)
    }
}

@Preview(showBackground = true)
@Composable
fun VpnScreenPreview() {
    OpenwayvpnTheme {
        AppRoot(modifier = androidx.compose.ui.Modifier.fillMaxSize())
    }
}
