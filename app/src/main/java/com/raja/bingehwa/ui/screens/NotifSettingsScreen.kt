package com.raja.bingehwa.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raja.bingehwa.BingeApp
import com.raja.bingehwa.ui.components.Card
import com.raja.bingehwa.ui.components.PrimaryButton
import com.raja.bingehwa.ui.components.ScreenHeader
import com.raja.bingehwa.ui.components.SectionLabel
import com.raja.bingehwa.ui.components.ToggleRow
import com.raja.bingehwa.ui.theme.Palette

@Composable
fun NotifSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as BingeApp).container
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var granted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Alerts", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (!granted) {
                Card(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Notifications are blocked", fontSize = 16.sp, color = Palette.Text)
                        Text("Allow them so new chapters can reach you.", fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                        PrimaryButton("Allow notifications", onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("New chapters")
            Column(Modifier.padding(horizontal = 20.dp)) {
                ToggleRow("Chapter alerts", "Master switch for every series", settings.notificationsOn, { v -> container.settings.update { it.copy(notificationsOn = v) } })
                ToggleRow("Quiet hours", "No alerts from 23:00 to 07:00", settings.quietHours, { v -> container.settings.update { it.copy(quietHours = v) } }, enabled = settings.notificationsOn)
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("How it works")
            Text(
                "Turn on the bell on a series page to get alerts for it. A server checks MangaDex about every 15 minutes, so an alert can arrive a little after the chapter is posted.",
                fontSize = 14.sp, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (!container.cloud.available) {
                Text(
                    "Alerts are off in this build because the Firebase backend is not connected.",
                    fontSize = 14.sp, color = Palette.AccentSoft, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}
