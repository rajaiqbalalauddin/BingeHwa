package com.raja.bingehwa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raja.bingehwa.BingeApp
import com.raja.bingehwa.ui.components.Card
import com.raja.bingehwa.ui.components.Chip
import com.raja.bingehwa.ui.components.IconAction
import com.raja.bingehwa.ui.components.ScreenHeader
import com.raja.bingehwa.ui.components.SecondaryButton
import com.raja.bingehwa.ui.components.SectionLabel
import com.raja.bingehwa.ui.components.SimpleDialog
import com.raja.bingehwa.ui.components.ToggleRow
import com.raja.bingehwa.ui.theme.Palette
import kotlinx.coroutines.launch

private val Languages = listOf("en" to "English", "id" to "Indonesian", "ms" to "Bahasa Melayu")

@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BingeApp).container
    val repo = container.repo
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val library by repo.observeLibrary().collectAsStateWithLifecycle(initialValue = emptyList())
    val downloads by repo.observeDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val bytes by repo.observeDownloadBytes().collectAsStateWithLifecycle(initialValue = 0L)
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sources and storage", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionLabel("Sources", Modifier.padding(top = 12.dp))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                SourceCard("MangaDex", "Chapters are read inside the app. Some series are removed by publishers, so not every title has chapters.", "On")
                Spacer(Modifier.height(10.dp))
                val official = library.count { it.officialUrl != null }
                SourceCard("Official links", "For licensed series the app opens the publisher's own page.", "$official in library")
            }
            SectionLabel("Chapter language")
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Languages.forEach { (code, name) ->
                    Chip(name, settings.chapterLang == code, onClick = { container.settings.update { it.copy(chapterLang = code) } })
                }
            }
            SectionLabel("Offline storage", Modifier.padding(top = 8.dp))
            Card(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(formatBytes(bytes) + " used", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text("${downloads.count { it.state == "DONE" }} chapters saved on this phone", fontSize = 13.sp, color = Palette.Muted)
                    ToggleRow("Download on Wi-Fi only", "New downloads wait for Wi-Fi", settings.wifiOnlyDownloads, { v -> container.settings.update { it.copy(wifiOnlyDownloads = v) } })
                    ToggleRow("Delete chapters after reading", "Frees space as you go", settings.deleteAfterRead, { v -> container.settings.update { it.copy(deleteAfterRead = v) } })
                    if (downloads.isNotEmpty()) SecondaryButton("Delete all downloads", onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (downloads.isNotEmpty()) {
                SectionLabel("Downloads")
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    downloads.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${d.seriesTitle}, ch. ${d.number}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1)
                                val status = when (d.state) {
                                    "DONE" -> formatBytes(d.bytes)
                                    "RUNNING" -> "${d.downloaded} of ${d.pageCount} pages"
                                    "QUEUED" -> if (settings.wifiOnlyDownloads) "Waiting for Wi-Fi" else "Waiting for network"
                                    else -> "Failed. Open the series to retry."
                                }
                                Text(status, fontSize = 13.sp, color = if (d.state == "FAILED") Palette.AccentSoft else Palette.Muted)
                                if (d.state == "RUNNING" && d.pageCount > 0) {
                                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line)) {
                                        Box(Modifier.fillMaxWidth(d.downloaded.toFloat() / d.pageCount).height(5.dp).background(Palette.Accent))
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            IconAction(onClick = { scope.launch { repo.deleteDownload(d.chapterId) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove download", tint = Palette.Muted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        SimpleDialog("Delete all downloads?", "Delete", onConfirm = { confirmClear = false; scope.launch { repo.deleteAllDownloads() } }, onDismiss = { confirmClear = false }) {
            Text("Chapters you saved will be removed from this phone. You can download them again later.", color = Palette.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SourceCard(name: String, body: String, badge: String) {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(body, fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Mint)
        }
    }
}

/** 1536 -> "1.5 KB". Plain helper so storage numbers read like a human wrote them. */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
