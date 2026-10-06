package com.raja.bingehwa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.raja.bingehwa.data.model.Series
import com.raja.bingehwa.data.model.UiState
import com.raja.bingehwa.data.repo.DiscoverData
import com.raja.bingehwa.data.repo.Repo
import com.raja.bingehwa.ui.components.CoverImage
import com.raja.bingehwa.ui.components.LoadingView
import com.raja.bingehwa.ui.components.MessageView
import com.raja.bingehwa.ui.components.SectionLabel
import com.raja.bingehwa.ui.components.StateHost
import com.raja.bingehwa.ui.components.pressClick
import com.raja.bingehwa.ui.containerViewModel
import com.raja.bingehwa.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiscoverViewModel(private val repo: Repo) : ViewModel() {
    private val _home = MutableStateFlow<UiState<DiscoverData>>(UiState.Loading)
    val home: StateFlow<UiState<DiscoverData>> = _home.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<Series>>?>(null)
    val results: StateFlow<UiState<List<Series>>?> = _results.asStateFlow()

    private val _surprising = MutableStateFlow(false)
    val surprising: StateFlow<Boolean> = _surprising.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _home.value = UiState.Loading
            _home.value = try {
                UiState.Data(repo.discover())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Could not load picks. Check your connection.")
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) { _results.value = null; return }
        viewModelScope.launch {
            _results.value = UiState.Loading
            _results.value = try {
                UiState.Data(repo.search(query.trim()))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Search failed. Check your connection.")
            }
        }
    }

    fun surprise(onPick: (String) -> Unit) {
        if (_surprising.value) return
        viewModelScope.launch {
            _surprising.value = true
            try {
                repo.surprise()?.let { onPick(it.id) }
            } catch (e: Exception) {
                // Leave the screen as it is; the button simply becomes available again.
            } finally {
                _surprising.value = false
            }
        }
    }
}

@Composable
fun DiscoverScreen(onOpenSeries: (String) -> Unit) {
    val vm = containerViewModel { DiscoverViewModel(it.repo) }
    val home by vm.home.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val surprising by vm.surprising.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (vm.home.value is UiState.Loading) vm.load() }

    Column(Modifier.fillMaxSize()) {
        Text("Discover", style = MaterialTheme.typography.headlineLarge, color = Palette.Text, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; if (it.isBlank()) vm.search("") },
            singleLine = true,
            placeholder = { Text("Search titles", color = Palette.Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.Muted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Accent,
                unfocusedBorderColor = Palette.Line,
                focusedContainerColor = Palette.Surface,
                unfocusedContainerColor = Palette.Surface,
                focusedTextColor = Palette.Text,
                unfocusedTextColor = Palette.Text,
                cursorColor = Palette.Accent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))

        val r = results
        if (query.isNotBlank() && r != null) {
            StateHost(r, onRetry = { vm.search(query) }) { list ->
                if (list.isEmpty()) MessageView("No matches", "Try another spelling or a shorter title.")
                else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { s -> SearchRow(s) { onOpenSeries(s.id) } }
                }
            }
        } else {
            StateHost(home, onRetry = vm::load) { data -> HomeSections(data, surprising, onOpenSeries, onSurprise = { vm.surprise(onOpenSeries) }) }
        }
    }
}

@Composable
private fun HomeSections(data: DiscoverData, surprising: Boolean, onOpen: (String) -> Unit, onSurprise: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Palette.Accent,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().pressClick(enabled = !surprising, onClick = onSurprise),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(if (surprising) "Finding one..." else "Surprise me", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.OnAccent)
                    Text("A random popular series you have not followed", fontSize = 13.sp, color = Palette.OnAccent)
                }
            }
        }
        if (data.matches.isNotEmpty()) {
            item { SectionLabel("Picked for you", Modifier.padding(top = 12.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.matches, key = { it.series.id }) { m ->
                        Column(Modifier.width(132.dp).pressClick { onOpen(m.series.id) }) {
                            Box {
                                CoverImage(m.series.coverUrl, m.series.title, Modifier.fillMaxWidth().aspectRatio(0.7f))
                                Surface(shape = RoundedCornerShape(8.dp), color = Palette.Background.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                                    Text("${m.percent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Mint, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                            Text(m.series.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(m.reason, fontSize = 11.sp, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (data.trending.isNotEmpty()) {
            item { SectionLabel("Trending", Modifier.padding(top = 12.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.trending, key = { it.id }) { s ->
                        Column(Modifier.width(110.dp).pressClick { onOpen(s.id) }) {
                            CoverImage(s.coverUrl, s.title, Modifier.fillMaxWidth().aspectRatio(0.7f))
                            Text(s.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        if (data.topRated.isNotEmpty()) {
            item { SectionLabel("Top rated", Modifier.padding(top = 12.dp)) }
            items(data.topRated, key = { it.first.id }) { pair ->
                val s = pair.first
                Box(Modifier.padding(horizontal = 20.dp)) {
                    SearchRow(s, trailing = pair.second?.rating?.let { String.format("%.1f", it) }) { onOpen(s.id) }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(s: Series, trailing: String? = null, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth().pressClick(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(s.coverUrl, s.title, Modifier.width(52.dp).height(72.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s.tags.take(3).joinToString(" · "), fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) Text(trailing, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Gold)
        }
    }
}
