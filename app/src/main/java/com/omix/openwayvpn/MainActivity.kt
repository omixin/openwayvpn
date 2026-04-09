package com.omix.openwayvpn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.omix.openwayvpn.ui.AppRoot
import com.omix.openwayvpn.ui.theme.OpenwayvpnTheme
import com.omix.openwayvpn.vpn.ProfileImportHelper

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val context = LanguageManager.applyLocale(newBase)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importVlessFromIntent(intent)
        importVlessFromClipboard()
        enableEdgeToEdge()
        setContent {
            OpenwayvpnTheme {
                AppRoot(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importVlessFromIntent(intent)
        importVlessFromClipboard()
    }

    private fun importVlessFromIntent(intent: Intent?) {
        ProfileImportHelper.importFromIntent(this, intent)
    }

    private fun importVlessFromClipboard() {
        ProfileImportHelper.importFromClipboard(this)
    }
}
