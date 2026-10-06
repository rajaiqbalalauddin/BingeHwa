package com.raja.bingehwa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.raja.bingehwa.R
import com.raja.bingehwa.data.model.Chapter
import com.raja.bingehwa.data.model.PageSource
import com.raja.bingehwa.data.model.UiState
import com.raja.bingehwa.data.prefs.SettingsState
import com.raja.bingehwa.data.repo.Repo
import com.raja.bingehwa.domain.ChapterUtil
import com.raja.bingehwa.ui.components.Chip
import com.raja.bingehwa.ui.components.IconAction
import com.raja.bingehwa.ui.components.PrimaryButton
import com.raja.bingehwa.ui.components.StateHost
import com.raja.bingehwa.ui.components.ToggleRow
import com.raja.bingehwa.ui.components.pressClick
import com.raja.bingehwa.ui.containerViewModel
import com.raja.bingehwa.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

class ReaderViewModel(private val repo: Repo, val chapterId: String, val seriesId: String) : ViewModel() {

    data class Loaded(
        val chapter: Chapter,
        val pages: List<PageSource>,
        val previous: Chapter?,
        val next: Chapter?,
        val seriesTitle: String,
        val startPage: Int,
    )

    private val _state = MutableStateFlow<UiState<Loaded>>(UiState.Loading)
    val state: StateFlow<UiState<Loaded>> = _state.asStateFlow()
    val settings: StateFlow<SettingsState> = repo.settings.state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                val chapters = repo.chapters(seriesId)
                val chapter = chapters.firstOrNull { it.id == chapterId } ?: error("This chapter is no longer available.")
                if (chapter.externalUrl != null) error("This chapter is hosted on the publisher's site.")
                val pages = repo.pages(chapter)
                if (pages.isEmpty()) error("This chapter has no pages.")
                val saved = repo.progressFor(chapterId)
                val title = runCatching { repo.series(seriesId).title }.getOrDefault("")
                UiState.Data(
                    Loaded(
                        chapter, pages,
                        previous = ChapterUtil.previous(chapters, chapterId)?.takeIf { it.readableInApp },
                        next = ChapterUtil.next(chapters, chapterId)?.takeIf { it.readableInApp },
                        seriesTitle = title,
                        startPage = if (saved != null && !saved.done) saved.page.coerceIn(0, pages.size - 1) else 0,
                    ),
                )
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Could not open this chapter. Check your connection.")
            }
        }
    }

    fun onPage(loaded: Loaded, page: Int) {
        viewModelScope.launch { repo.saveProgress(seriesId, loaded.chapter, page, loaded.pages.size) }
    }

    fun update(change: (SettingsState) -> SettingsState) = repo.settings.update(change)
}

@Composable
fun ReaderScreen(
    chapterId: String,
    seriesId: String,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenComments: (chapterId: String) -> Unit,
) {
    val vm = containerViewModel(key = "reader_$chapterId") { ReaderViewModel(it.repo, chapterId, seriesId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var barsVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        StateHost(state, onRetry = vm::load) { loaded ->
            val total = loaded.pages.size
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { barsVisible = !barsVisible }) },
            ) {
                if (settings.readerMode == "paged") {
                    val pager = rememberPagerState(initialPage = loaded.startPage) { total }
                    LaunchedEffect(pager) {
                        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { currentPage = it; vm.onPage(loaded, it) }
                    }
                    HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                        PageImage(loaded.pages[i], fill = true)
                    }
                } else {
                    val list = rememberLazyListState()
                    LaunchedEffect(loaded) { if (loaded.startPage > 0) list.scrollToItem(loaded.startPage) }
                    LaunchedEffect(list) {
                        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect {
                            val page = it.coerceAtMost(total - 1)
                            currentPage = page
                            vm.onPage(loaded, page)
                        }
                    }
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                        items(total) { i -> PageImage(loaded.pages[i], fill = false) }
                        item { EndOfChapter(loaded.next, onNext = { loaded.next?.let { onOpenChapter(it.id) } }) }
                    }
                }
            }

            AnimatedVisibility(
                visible = barsVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Palette.Background.copy(alpha = 0.92f)).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("Back", color = Palette.Text, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) {
                        Text("Chapter ${loaded.chapter.number}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1)
                        if (loaded.seriesTitle.isNotBlank()) Text(loaded.seriesTitle, fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            AnimatedVisibility(
                visible = barsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Palette.Background.copy(alpha = 0.92f)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { loaded.previous?.let { onOpenChapter(it.id) } }, enabled = loaded.previous != null) {
                        Text("Prev", color = if (loaded.previous != null) Palette.Text else Palette.Muted)
                    }
                    Text("${currentPage + 1} / $total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = { loaded.next?.let { onOpenChapter(it.id) } }, enabled = loaded.next != null) {
                        Text("Next", color = if (loaded.next != null) Palette.Text else Palette.Muted)
                    }
                    IconAction(onClick = { onOpenComments(chapterId) }) {
                        Icon(painterResource(R.drawable.ic_comment), contentDescription = "Comments", tint = Palette.Text)
                    }
                    IconAction(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Reader settings", tint = Palette.Text)
                    }
                }
            }
        }
    }

    if (showSettings) ReaderSettingsSheet(settings, vm::update, onDismiss = { showSettings = false })
}

@Composable
private fun EndOfChapter(next: Chapter?, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding().padding(bottom = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (next != null) {
            Text("End of chapter", color = Palette.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Next: Chapter ${next.number}", onNext, Modifier.fillMaxWidth())
        } else {
            Text("You are all caught up", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Turn on alerts on the series page to hear about the next chapter.", color = Palette.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** One page. Vertical mode lets height follow the image; paged mode fits it to the screen. */
@Composable
private fun PageImage(source: PageSource, fill: Boolean) {
    val context = LocalContext.current
    var attempt by remember(source) { mutableIntStateOf(0) }
    val data: Any = when (source) {
        is PageSource.Remote -> source.url
        is PageSource.Local -> File(source.path)
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(data).setParameter("attempt", attempt).crossfade(true).build(),
        contentDescription = null,
        contentScale = if (fill) ContentScale.Fit else ContentScale.FillWidth,
        modifier = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        loading = {
            Box(Modifier.fillMaxWidth().height(if (fill) 600.dp else 480.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent)
            }
        },
        error = {
            Box(Modifier.fillMaxWidth().height(240.dp).clickable { attempt++ }, contentAlignment = Alignment.Center) {
                Text("This page did not load. Tap to retry.", color = Palette.Muted, fontSize = 14.sp)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(settings: SettingsState, update: ((SettingsState) -> SettingsState) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Reader settings", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
            Spacer(Modifier.height(14.dp))
            Text("READING MODE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Palette.Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Webtoon scroll", settings.readerMode == "vertical", onClick = { update { it.copy(readerMode = "vertical") } })
                Chip("Paged", settings.readerMode == "paged", onClick = { update { it.copy(readerMode = "paged") } })
            }
            ToggleRow("Keep screen on", "Stops the screen from sleeping while you read", settings.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } })
            ToggleRow("Data saver", "Smaller images. Applies the next time a chapter opens", settings.dataSaver, { v -> update { it.copy(dataSaver = v) } })
            Spacer(Modifier.height(20.dp))
        }
    }
}
