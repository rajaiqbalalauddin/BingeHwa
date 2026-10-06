package com.raja.bingehwa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.raja.bingehwa.data.local.LibraryEntity
import com.raja.bingehwa.data.local.ProgressEntity
import com.raja.bingehwa.data.repo.Repo
import com.raja.bingehwa.ui.components.CoverImage
import com.raja.bingehwa.ui.components.MessageView
import com.raja.bingehwa.ui.components.pressClick
import com.raja.bingehwa.ui.containerViewModel
import com.raja.bingehwa.ui.theme.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Library state comes straight from the local database, so the shelf opens instantly and offline. */
class LibraryViewModel(repo: Repo) : ViewModel() {
    val library: StateFlow<List<LibraryEntity>?> =
        repo.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val latest: StateFlow<ProgressEntity?> =
        repo.observeLatestProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun LibraryScreen(onOpenSeries: (String) -> Unit, onOpenChapter: (chapterId: String, seriesId: String) -> Unit, onFindSomething: () -> Unit) {
    val vm = containerViewModel { LibraryViewModel(it.repo) }
    val library by vm.library.collectAsStateWithLifecycle()
    val latest by vm.latest.collectAsStateWithLifecycle()
    val shelf = library

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, color = Palette.Text, modifier = Modifier.weight(1f))
            if (shelf != null) Text("${shelf.size} series", fontSize = 14.sp, color = Palette.Muted)
        }
        if (shelf != null && shelf.isEmpty()) {
            MessageView(
                "Your shelf is empty",
                "Follow a series to see it here, with alerts when new chapters arrive.",
                actionLabel = "Find something to read",
                onAction = onFindSomething,
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val resume = latest
            val resumeSeries = shelf?.firstOrNull { it.seriesId == resume?.seriesId }
            if (resume != null && resumeSeries != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueCard(resumeSeries, resume) {
                        // Unfinished chapter: jump back in. Finished: the series page offers the next one.
                        if (resume.done) onOpenSeries(resumeSeries.seriesId) else onOpenChapter(resume.chapterId, resume.seriesId)
                    }
                }
            }
            items(shelf.orEmpty(), key = { it.seriesId }) { s -> ShelfItem(s) { onOpenSeries(s.seriesId) } }
        }
    }
}

@Composable
private fun ContinueCard(series: LibraryEntity, progress: ProgressEntity, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Palette.Surface,
        modifier = Modifier.fillMaxWidth().pressClick(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(series.coverUrl, series.title, Modifier.width(56.dp).height(78.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("CONTINUE READING", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Palette.AccentSoft)
                Text(series.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val where = if (progress.done) "Finished chapter ${progress.number}" else "Chapter ${progress.number}, page ${progress.page + 1} of ${progress.pageCount}"
                Text(where, fontSize = 13.sp, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun ShelfItem(s: LibraryEntity, onClick: () -> Unit) {
    Column(Modifier.pressClick(onClick = onClick)) {
        CoverImage(s.coverUrl, s.title, Modifier.fillMaxWidth().aspectRatio(0.7f))
        Text(s.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(
            s.lastReadNumber?.let { "Read to ch. $it" } ?: "Not started",
            fontSize = 12.sp,
            color = Palette.Muted,
        )
    }
}
