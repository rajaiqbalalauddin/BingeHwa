package com.raja.bingehwa.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raja.bingehwa.ui.components.Chip
import com.raja.bingehwa.ui.components.PrimaryButton
import com.raja.bingehwa.ui.components.ScreenHeader
import com.raja.bingehwa.ui.theme.Palette

/** Names match MangaDex's own genre and theme tags, so picks can be used directly as filters. */
val TasteGenres = listOf(
    "Romance", "Fantasy", "Action", "Drama", "Comedy", "Horror", "Mystery", "Slice of Life",
    "Thriller", "Supernatural", "Historical", "Psychological", "Sci-Fi", "Isekai", "Martial Arts",
    "School Life", "Reincarnation", "Crime", "Adventure", "Tragedy",
)

private const val MIN_PICKS = 3

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasteScreen(onDone: (Set<String>) -> Unit, onBack: () -> Unit) {
    var picks by remember { mutableStateOf(setOf<String>()) }
    // Asked here, at a moment the reader just chose alerts-friendly onboarding, not on first launch.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone(picks) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Pick your taste", onBack, subtitle = "Choose at least $MIN_PICKS. You can change this later.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TasteGenres.forEach { g ->
                    Chip(g, g in picks, onClick = { picks = if (g in picks) picks - g else picks + g })
                }
            }
        }
        Column(Modifier.padding(20.dp)) {
            Text("${picks.size} selected", fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(bottom = 8.dp))
            PrimaryButton(
                "Continue",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onDone(picks)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = picks.size >= MIN_PICKS,
            )
        }
    }
}
