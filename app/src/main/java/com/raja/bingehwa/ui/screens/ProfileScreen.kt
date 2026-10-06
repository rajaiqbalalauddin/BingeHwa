package com.raja.bingehwa.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.raja.bingehwa.data.local.LibraryEntity
import com.raja.bingehwa.data.prefs.SettingsState
import com.raja.bingehwa.data.repo.Repo
import com.raja.bingehwa.domain.ReadingStats
import com.raja.bingehwa.domain.Recommender
import com.raja.bingehwa.ui.components.Card
import com.raja.bingehwa.ui.components.ScreenHeader
import com.raja.bingehwa.ui.components.SectionLabel
import com.raja.bingehwa.ui.components.SimpleDialog
import com.raja.bingehwa.ui.components.bingeFieldColors
import com.raja.bingehwa.ui.components.pressClick
import com.raja.bingehwa.ui.containerViewModel
import com.raja.bingehwa.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class ProfileViewModel(private val repo: Repo) : ViewModel() {
    val settings: StateFlow<SettingsState> = repo.settings.state
    val library: StateFlow<List<LibraryEntity>> =
        repo.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val doneCount: StateFlow<Int> =
        repo.observeDoneCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val weekStart: Long = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val week: StateFlow<List<Int>> =
        repo.observeProgressSince(weekStart)
            .map { list -> ReadingStats.weeklyCounts(list.filter { it.done }.map { it.updatedAt }, System.currentTimeMillis()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(7) { 0 })

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    val cloudAvailable: Boolean get() = repo.cloud.available

    fun setName(name: String) {
        repo.settings.update { it.copy(displayName = name.trim().take(30)) }
        viewModelScope.launch { runCatching { repo.cloud.setDisplayName(name) } }
    }

    fun importList(user: String) {
        viewModelScope.launch {
            _importing.value = true
            _importMessage.value = try {
                val r = repo.importFromAniList(user)
                "Added ${r.added} series." + if (r.missed.isNotEmpty()) " Could not find ${r.missed.size}: ${r.missed.take(3).joinToString(", ")}${if (r.missed.size > 3) "..." else ""}" else ""
            } catch (e: Exception) {
                "Import failed: ${e.message ?: "check your connection"}"
            } finally {
                _importing.value = false
            }
        }
    }

    fun clearImportMessage() { _importMessage.value = null }
}

@Composable
fun ProfileScreen(onAlerts: () -> Unit, onSources: () -> Unit) {
    val vm = containerViewModel { ProfileViewModel(it.repo) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val done by vm.doneCount.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val importMessage by vm.importMessage.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    var editName by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    val profile = Recommender.profile(library.map { Recommender.Owned(it.title, it.tagList) }, settings.tastePicks)
    val taste = ReadingStats.topTags(profile)
    val name = settings.displayName.ifBlank { "Reader" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Profile", onBack = null)
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.Accent), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.OnAccent)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
                Text("Edit display name", fontSize = 13.sp, color = Palette.AccentSoft, modifier = Modifier.pressClick { editName = true }.padding(vertical = 4.dp))
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Series", library.size.toString(), Modifier.weight(1f))
            StatCard("Chapters read", done.toString(), Modifier.weight(1f))
            StatCard("This week", week.sum().toString(), Modifier.weight(1f))
        }

        SectionLabel("Last 7 days", Modifier.padding(top = 20.dp))
        Card(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { WeekChart(week) }

        if (taste.isNotEmpty()) {
            SectionLabel("Your taste", Modifier.padding(top = 10.dp))
            Card(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    taste.forEach { (tag, frac) -> TasteBar(tag, frac) }
                }
            }
        }

        SectionLabel("Settings", Modifier.padding(top = 10.dp))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingRow("Alerts", "Chapter notifications and quiet hours", onAlerts)
            SettingRow("Sources and storage", "Language, downloads, offline space", onSources)
            SettingRow("Import from AniList", "Bring over your reading list by username") { showImport = true }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Community", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text(
                        if (vm.cloudAvailable) "Connected. Ratings, comments and alerts are on." else "Not set up in this build. Reading works fully offline-first; ratings, comments and alerts need Firebase.",
                        fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Text("BingeHwa 0.1.0 · No ads, no tracking", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }

    if (editName) NameDialog(initial = settings.displayName, onSave = { vm.setName(it); editName = false }, onDismiss = { editName = false })
    val message = importMessage
    if (showImport || message != null) {
        var user by remember { mutableStateOf("") }
        SimpleDialog(
            title = "Import from AniList",
            confirmLabel = if (message == null) "Import" else "Done",
            confirmEnabled = message != null || (user.isNotBlank() && !importing),
            onConfirm = { if (message != null) { vm.clearImportMessage(); showImport = false } else vm.importList(user) },
            onDismiss = { vm.clearImportMessage(); showImport = false },
        ) {
            Column {
                when {
                    message != null -> Text(message, color = Palette.Text, fontSize = 14.sp)
                    importing -> Text("Importing. This takes a few seconds per title.", color = Palette.Muted, fontSize = 14.sp)
                    else -> {
                        Text("Your AniList list must be public. Reading, repeating and planning titles are matched on MangaDex.", color = Palette.Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = user, onValueChange = { user = it.trim() }, singleLine = true, placeholder = { Text("AniList username") }, colors = bingeFieldColors())
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
            Text(label, fontSize = 12.sp, color = Palette.Muted)
        }
    }
}

@Composable
private fun WeekChart(week: List<Int>) {
    val max = (week.maxOrNull() ?: 0).coerceAtLeast(1)
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().height(120.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        week.forEachIndexed { i, count ->
            val target = count.toFloat() / max
            val h by animateFloatAsState(target, tween(600), label = "bar$i")
            val label = today.minusDays((6 - i).toLong()).dayOfWeek.name.take(1)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.weight(1f, fill = true), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(h.coerceAtLeast(0.04f)).clip(RoundedCornerShape(6.dp)).background(if (count > 0) Palette.Accent else Palette.Line))
                }
                Text(label, fontSize = 11.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun TasteBar(tag: String, fraction: Float) {
    val animated by animateFloatAsState(fraction, tween(700), label = "taste_$tag")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tag, fontSize = 14.sp, color = Palette.Text, modifier = Modifier.width(110.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceIn(0.02f, 1f)).background(Palette.Accent))
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().pressClick(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(subtitle, fontSize = 13.sp, color = Palette.Muted)
            }
            Text("Open", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.AccentSoft)
        }
    }
}
