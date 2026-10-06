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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.raja.bingehwa.data.model.UiState
import com.raja.bingehwa.data.repo.FeedItem
import com.raja.bingehwa.data.repo.Repo
import com.raja.bingehwa.domain.TimeAgo
import com.raja.bingehwa.ui.components.CoverImage
import com.raja.bingehwa.ui.components.IconAction
import com.raja.bingehwa.ui.components.MessageView
import com.raja.bingehwa.ui.components.ScreenHeader
import com.raja.bingehwa.ui.components.StateHost
import com.raja.bingehwa.ui.components.pressClick
import com.raja.bingehwa.ui.containerViewModel
import com.raja.bingehwa.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdatesViewModel(private val repo: Repo) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<FeedItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<FeedItem>>> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Data(repo.feed())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Could not load updates. Check your connection.")
            }
        }
    }
}

@Composable
fun UpdatesScreen(onOpenChapter: (chapterId: String, seriesId: String) -> Unit, onOpenSeries: (String) -> Unit) {
    val vm = containerViewModel { UpdatesViewModel(it.repo) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Updates", onBack = null, actions = {
            IconAction(onClick = vm::load) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Palette.Text) }
        })
        StateHost(state, onRetry = vm::load) { feed ->
            if (feed.isEmpty()) {
                MessageView("Nothing new yet", "Follow a series and its latest chapters show up here.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                    items(feed, key = { it.chapter.id }) { item ->
                        FeedRow(
                            item,
                            onClick = {
                                val ext = item.chapter.externalUrl
                                if (ext != null) uri.openUri(ext) else onOpenChapter(item.chapter.id, item.series.seriesId)
                            },
                            onOpenSeries = { onOpenSeries(item.series.seriesId) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem, onClick: () -> Unit, onOpenSeries: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(item.series.coverUrl, item.series.title, Modifier.width(52.dp).height(72.dp).pressClick(onClick = onOpenSeries))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.unread) Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Accent))
                    if (item.unread) Spacer(Modifier.width(6.dp))
                    Text(item.series.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val label = "Chapter ${item.chapter.number}" + (item.chapter.title?.let { " · $it" } ?: "")
                Text(label, fontSize = 13.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(TimeAgo.format(item.chapter.publishAt) + " ago", fontSize = 12.sp, color = Palette.Muted)
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = Palette.Accent, modifier = Modifier.height(40.dp).pressClick(onClick = onClick)) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(if (item.chapter.externalUrl != null) "Official" else "Read", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.OnAccent)
                }
            }
        }
    }
}
