package com.raja.bingehwa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.raja.bingehwa.R
import com.raja.bingehwa.data.cloud.RatingInput
import com.raja.bingehwa.data.cloud.RatingSummary
import com.raja.bingehwa.data.local.DownloadEntity
import com.raja.bingehwa.data.local.LibraryEntity
import com.raja.bingehwa.data.local.ProgressEntity
import com.raja.bingehwa.data.model.Chapter
import com.raja.bingehwa.data.model.Series
import com.raja.bingehwa.data.model.SeriesStats
import com.raja.bingehwa.data.model.UiState
import com.raja.bingehwa.data.model.dataOrNull
import com.raja.bingehwa.data.repo.Repo
import com.raja.bingehwa.domain.ChapterUtil
import com.raja.bingehwa.domain.TimeAgo
import com.raja.bingehwa.ui.components.Card
import com.raja.bingehwa.ui.components.CoverImage
import com.raja.bingehwa.ui.components.IconAction
import com.raja.bingehwa.ui.components.PrimaryButton
import com.raja.bingehwa.ui.components.ScreenHeader
import com.raja.bingehwa.ui.components.SecondaryButton
import com.raja.bingehwa.ui.components.SectionLabel
import com.raja.bingehwa.ui.components.StateHost
import com.raja.bingehwa.ui.components.pressClick
import com.raja.bingehwa.ui.containerViewModel
import com.raja.bingehwa.ui.theme.Palette
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val repo: Repo, val seriesId: String) : ViewModel() {
    private val _series = MutableStateFlow<UiState<Series>>(UiState.Loading)
    val series: StateFlow<UiState<Series>> = _series.asStateFlow()

    private val _chapters = MutableStateFlow<UiState<List<Chapter>>>(UiState.Loading)
    val chapters: StateFlow<UiState<List<Chapter>>> = _chapters.asStateFlow()

    val entry: StateFlow<LibraryEntity?> =
        repo.observeLibraryEntry(seriesId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val progress: StateFlow<Map<String, ProgressEntity>> =
        repo.observeProgress(seriesId).map { l -> l.associateBy { it.chapterId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val downloads: StateFlow<Map<String, DownloadEntity>> =
        repo.observeDownloadsFor(seriesId).map { l -> l.associateBy { it.chapterId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _community = MutableStateFlow<RatingSummary?>(null)
    val community: StateFlow<RatingSummary?> = _community.asStateFlow()
    private val _stats = MutableStateFlow<SeriesStats?>(null)
    val stats: StateFlow<SeriesStats?> = _stats.asStateFlow()
    private val _myRating = MutableStateFlow<RatingInput?>(null)
    val myRating: StateFlow<RatingInput?> = _myRating.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val cloudAvailable: Boolean get() = repo.cloud.available

    init { load() }

    fun load() {
        viewModelScope.launch {
            _series.value = UiState.Loading
            _series.value = try {
                UiState.Data(repo.series(seriesId))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Could not load this series.")
            }
        }
        loadChapters()
        viewModelScope.launch { _stats.value = repo.stats(listOf(seriesId))[seriesId] }
        refreshCommunity()
    }

    fun loadChapters() {
        viewModelScope.launch {
            _chapters.value = UiState.Loading
            _chapters.value = try {
                UiState.Data(repo.chapters(seriesId, force = true))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Could not load chapters.")
            }
        }
    }

    private fun refreshCommunity() {
        if (!repo.cloud.available) return
        viewModelScope.launch {
            runCatching { repo.cloud.ratingSummary(seriesId) }.onSuccess { _community.value = it }
            runCatching { repo.cloud.myRating(seriesId) }.onSuccess { _myRating.value = it }
        }
    }

    private fun say(text: String) { _messages.tryEmit(text) }

    fun toggleFollow() {
        val s = _series.value.dataOrNull() ?: return
        viewModelScope.launch {
            if (entry.value == null) { repo.follow(s); say("Added to your library") }
            else { repo.unfollow(seriesId); say("Removed from your library") }
        }
    }

    fun toggleAlerts() {
        val s = _series.value.dataOrNull() ?: return
        if (!repo.cloud.available) { say("Alerts need the Firebase backend, which is not set up in this build."); return }
        viewModelScope.launch {
            try {
                repo.follow(s)
                val turnOn = entry.value?.alertsOn != true
                repo.setAlerts(seriesId, turnOn)
                say(if (turnOn) "You will get an alert for new chapters" else "Alerts turned off")
            } catch (e: Exception) {
                say("Could not change alerts: ${e.message ?: "check your connection"}")
            }
        }
    }

    fun download(chapter: Chapter) {
        val title = _series.value.dataOrNull()?.title ?: entry.value?.title ?: return
        viewModelScope.launch { repo.download(title, chapter); say("Download queued") }
    }

    fun deleteDownload(chapterId: String) { viewModelScope.launch { repo.deleteDownload(chapterId) } }

    fun submitRating(input: RatingInput, onDone: () -> Unit) {
        viewModelScope.launch {
            _submitting.value = true
            try {
                repo.cloud.submitRating(seriesId, input)
                _myRating.value = input
                _community.value = repo.cloud.ratingSummary(seriesId)
                say("Thanks, rating saved")
                onDone()
            } catch (e: Exception) {
                say("Could not save rating: ${e.message ?: "check your connection"}")
            } finally {
                _submitting.value = false
            }
        }
    }

    fun said(text: String) = say(text)
}

private enum class Sheet { RATE, SHARE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    seriesId: String,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String, seriesId: String) -> Unit,
    onOpenComments: () -> Unit,
) {
    val vm = containerViewModel(key = "detail_$seriesId") { DetailViewModel(it.repo, seriesId) }
    val seriesState by vm.series.collectAsStateWithLifecycle()
    val chaptersState by vm.chapters.collectAsStateWithLifecycle()
    val entry by vm.entry.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val community by vm.community.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val myRating by vm.myRating.collectAsStateWithLifecycle()
    val submitting by vm.submitting.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val uri = LocalUriHandler.current

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = seriesState.dataOrNull()?.title ?: "Series",
                onBack = onBack,
                actions = {
                    IconAction(onClick = vm::toggleAlerts) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = "Toggle new chapter alerts",
                            tint = if (entry?.alertsOn == true) Palette.Accent else Palette.Muted,
                        )
                    }
                    IconAction(onClick = { sheet = Sheet.SHARE }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Palette.Text)
                    }
                },
            )
            StateHost(seriesState, onRetry = vm::load) { series ->
                val chapters = chaptersState.dataOrNull().orEmpty()
                val resume = resumeTarget(chapters, entry, progress)
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
                            CoverImage(series.coverUrl, series.title, Modifier.width(110.dp).height(154.dp), radius = 14.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(series.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(series.status.replaceFirstChar { it.uppercase() }, series.year?.toString()).joinToString(" · "),
                                    fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    item {
                        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val ext = resume?.externalUrl
                            PrimaryButton(
                                label = when {
                                    resume == null -> "No chapters"
                                    ext != null -> "Read on official site"
                                    entry?.lastReadChapterId != null || progress.isNotEmpty() -> "Continue ch. ${resume?.number}"
                                    else -> "Start reading"
                                },
                                onClick = {
                                    if (resume != null) {
                                        if (ext != null) uri.openUri(ext) else onOpenChapter(resume.id, seriesId)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = resume != null,
                            )
                            SecondaryButton(if (entry == null) "Add" else "In library", onClick = vm::toggleFollow)
                        }
                    }
                    if (series.officialUrl != null) {
                        item {
                            Text(
                                "Official English release available",
                                fontSize = 13.sp, color = Palette.AccentSoft, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 20.dp).pressClick { uri.openUri(series.officialUrl) },
                            )
                        }
                    }
                    if (series.tags.isNotEmpty()) {
                        item {
                            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                series.tags.take(8).forEach { t ->
                                    Surface(shape = RoundedCornerShape(10.dp), color = Palette.Raised) {
                                        Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (series.description.isNotBlank()) {
                        item { Description(series.description) }
                    }
                    item {
                        RatingCard(community, stats, myRating, onRate = { sheet = Sheet.RATE })
                    }
                    item {
                        Card(Modifier.padding(horizontal = 20.dp)) {
                            Row(Modifier.fillMaxWidth().pressClick(onClick = onOpenComments).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_comment), contentDescription = null, tint = Palette.AccentSoft)
                                Spacer(Modifier.width(12.dp))
                                Text("Comments", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.weight(1f))
                                Text("Open", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.AccentSoft)
                            }
                        }
                    }
                    item { SectionLabel("Chapters" + if (chapters.isNotEmpty()) " (${chapters.size})" else "") }
                    when (val cs = chaptersState) {
                        is UiState.Loading -> item { Text("Loading chapters...", color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp)) }
                        is UiState.Error -> item {
                            Column(Modifier.padding(horizontal = 20.dp)) {
                                Text(cs.message, color = Palette.Muted, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                SecondaryButton("Try again", vm::loadChapters)
                            }
                        }
                        is UiState.Data -> items(cs.value, key = { it.id }) { c ->
                            ChapterRow(
                                c, progress[c.id], downloads[c.id],
                                onOpen = { if (c.externalUrl != null) uri.openUri(c.externalUrl) else onOpenChapter(c.id, seriesId) },
                                onDownload = { vm.download(c) },
                                onDelete = { vm.deleteDownload(c.id) },
                            )
                        }
                    }
                }

                when (sheet) {
                    Sheet.RATE -> RateSheet(series.title, myRating, submitting, vm.cloudAvailable, onSubmit = { input -> vm.submitRating(input) { sheet = null } }, onDismiss = { sheet = null })
                    Sheet.SHARE -> ShareSheet(series, myRating?.stars, onDismiss = { sheet = null }, onCopied = { vm.said("Link copied") })
                    null -> Unit
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

/** Resume an unfinished chapter first; otherwise the chapter after the last finished one. */
private fun resumeTarget(chapters: List<Chapter>, entry: LibraryEntity?, progress: Map<String, ProgressEntity>): Chapter? {
    if (chapters.isEmpty()) return null
    val unfinished = progress.values.filter { !it.done }.maxByOrNull { it.updatedAt }
    val match = unfinished?.let { u -> chapters.firstOrNull { it.id == u.chapterId } }
    if (match != null) return match
    return ChapterUtil.continueTarget(chapters, entry?.lastReadChapterId)
}

@Composable
private fun Description(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            text, fontSize = 14.sp, lineHeight = 21.sp, color = Palette.Text.copy(alpha = 0.85f),
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (expanded) "Show less" else "Show more",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.AccentSoft,
            modifier = Modifier.padding(top = 4.dp).pressClick { expanded = !expanded },
        )
    }
}

@Composable
private fun RatingCard(community: RatingSummary?, stats: SeriesStats?, mine: RatingInput?, onRate: () -> Unit) {
    Card(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().pressClick(onClick = onRate).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val line = when {
                    community != null && community.count > 0 -> String.format("%.1f from %d BingeHwa ratings", community.average ?: 0.0, community.count)
                    stats?.rating != null -> String.format("%.1f of 5 on MangaDex", (stats?.rating ?: 0.0) / 2.0)
                    else -> "No ratings yet"
                }
                Text(line, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(if (mine != null) "You rated it ${mine.stars} of 5. Tap to change." else "Tap to rate", fontSize = 13.sp, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun ChapterRow(c: Chapter, progress: ProgressEntity?, download: DownloadEntity?, onOpen: () -> Unit, onDownload: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressClick(onClick = onOpen).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Chapter ${c.number}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (progress?.done == true) Palette.Muted else Palette.Text,
                )
                if (progress?.done == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Check, contentDescription = "Read", tint = Palette.Mint, modifier = Modifier.size(16.dp))
                }
            }
            val sub = buildList {
                c.title?.let { add(it) }
                if (c.publishAt.isNotBlank()) add(TimeAgo.format(c.publishAt) + " ago")
                if (progress != null && !progress.done) add("page ${progress.page + 1}/${progress.pageCount}")
                if (c.externalUrl != null) add("official site")
            }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (c.readableInApp) {
            when (download?.state) {
                "DONE" -> IconAction(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete download", tint = Palette.Muted) }
                "RUNNING", "QUEUED" -> Text(
                    if (download?.state == "QUEUED") "Waiting" else "${download?.downloaded}/${download?.pageCount}",
                    fontSize = 12.sp, color = Palette.AccentSoft,
                )
                else -> IconAction(onClick = onDownload) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = "Download chapter", tint = Palette.Muted)
                }
            }
        }
    }
}
