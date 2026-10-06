package com.raja.bingehwa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.navigation.compose.rememberNavController
import com.raja.bingehwa.ui.nav.BingeHwaApp
import com.raja.bingehwa.ui.theme.BingeHwaTheme

/** Single activity: all screens are Compose destinations, which keeps transitions consistent. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BingeHwaTheme {
                val nav = rememberNavController()
                // A notification tapped while the app is already open arrives here, not in onCreate.
                DisposableEffect(nav) {
                    val listener = Consumer<Intent> { nav.handleDeepLink(it) }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }
                BingeHwaApp(nav)
            }
        }
    }
}
