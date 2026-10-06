package com.raja.bingehwa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.raja.bingehwa.data.cloud.CommentItem
import com.raja.bingehwa.data.cloud.CommentSort
import com.raja.bingehwa.data.model.UiState
import com.raja.bingehwa.data.model.dataOrNull
import com.raja.bingehwa.data.repo.Repo
import com.raja.bingehwa.domain.TimeAgo
import com.raja.bingehwa.ui.components.Chip
import com.raja.bingehwa.ui.components.IconAction
import com.raja.bingehwa.ui.components.MessageView
import com.raja.bingehwa.ui.components.ScreenHeader
import com.raja.bingehwa.ui.components.SimpleDialog
import com.raja.bingehwa.ui.components.StateHost
import com.raja.bingehwa.ui.components.bingeFieldColors
import com.raja.bingehwa.ui.components.pressClick
import com.raja.bingehwa.ui.containerViewModel
import com.raja.bingehwa.ui.theme.Palette
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommentsViewModel(private val repo: Repo, val seriesId: String, val chapterId: String?) : ViewModel() {
    /** 0 = this chapter's thread, 1 = whole series. Without a chapter there is only the series thread. */
    private val _tab = MutableStateFlow(if (chapterId != null) 0 else 1)
    val tab: StateFlow<Int> = _tab.asStateFlow()
    private val _sort = MutableStateFlow(CommentSort.TOP)
    val sort: StateFlow<CommentSort> = _sort.asStateFlow()
    private val _items = MutableStateFlow<UiState<List<CommentItem>>>(UiState.Loading)
    val items: StateFlow<UiState<List<CommentItem>>> = _items.asStateFlow()
    private val _replies = MutableStateFlow<Map<String, List<CommentItem>>>(emptyMap())
    val replies: StateFlow<Map<String, List<CommentItem>>> = _replies.asStateFlow()
    private val _replyTo = MutableStateFlow<CommentItem?>(null)
    val replyTo: StateFlow<CommentItem?> = _replyTo.asStateFlow()
    private val _posting = MutableStateFlow(false)
    val posting: StateFlow<Boolean> = _posting.asStateFlow()
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    val settings = repo.settings.state
    val cloudAvailable: Boolean get() = repo.cloud.available

    init {
        viewModelScope.launch { _title.value = runCatching { repo.series(seriesId).title }.getOrDefault("") }
        load()
    }

    private fun scopeChapter(): String? = if (_tab.value == 0) chapterId else null

    fun load() {
        if (!repo.cloud.available) return
        viewModelScope.launch {
            _items.value = UiState.Loading
            _items.value = try {
                UiState.Data(repo.cloud.comments(seriesId, scopeChapter(), _sort.value))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Could not load comments.")
            }
        }
    }

    fun selectTab(t: Int) { _tab.value = t; _replyTo.value = null; _replies.value = emptyMap(); load() }
    fun selectSort(s: CommentSort) { _sort.value = s; load() }
    fun replyTo(c: CommentItem?) { _replyTo.value = c }

    fun setName(name: String) {
        repo.settings.update { it.copy(displayName = name.trim().take(30)) }
        viewModelScope.launch { runCatching { repo.cloud.setDisplayName(name) } }
    }

    fun post(text: String, spoiler: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            _posting.value = true
            try {
                val parent = _replyTo.value
                repo.cloud.postComment(seriesId, scopeChapter(), parent?.id, text, spoiler)
                _replyTo.value = null
                onDone()
                if (parent != null) loadReplies(parent.id) 
                load()
            } catch (e: Exception) {
                _messages.tryEmit("Could not post: ${e.message ?: "check your connection"}")
            } finally {
                _posting.value = false
            }
        }
    }

    private suspend fun loadReplies(parentId: String) {
        val r = runCatching { repo.cloud.replies(parentId) }.getOrDefault(emptyList())
        _replies.value = _replies.value + (parentId to r)
    }

    fun toggleReplies(c: CommentItem) {
        if (_replies.value.containsKey(c.id)) { _replies.value = _replies.value - c.id; return }
        viewModelScope.launch { loadReplies(c.id) }
    }

    fun like(c: CommentItem) {
        val was = c.likedByMe
        fun patch(item: CommentItem) = if (item.id == c.id) item.copy(likedByMe = !was, likeCount = item.likeCount + if (was) -1 else 1) else item
        _items.value.dataOrNull()?.let { list -> _items.value = UiState.Data(list.map { patch(it) }) }
        _replies.value = _replies.value.mapValues { (_, l) -> l.map { patch(it) } }
        viewModelScope.launch {
            try {
                repo.cloud.toggleLike(c.id, was)
            } catch (e: Exception) {
                _messages.tryEmit("Could not like that: ${e.message ?: "check your connection"}")
                load()
            }
        }
    }

    fun delete(c: CommentItem) {
        viewModelScope.launch {
            try {
                repo.cloud.deleteComment(c, null)
                _messages.tryEmit("Comment deleted")
                load()
            } catch (e: Exception) {
                _messages.tryEmit("Could not delete: ${e.message ?: "check your connection"}")
            }
        }
    }

    fun report(c: CommentItem) {
        viewModelScope.launch {
            try {
                repo.cloud.report(c.id, "reported from app")
                _messages.tryEmit("Thanks, reported")
            } catch (e: Exception) {
                _messages.tryEmit("Could not report: ${e.message ?: "check your connection"}")
            }
        }
    }
}

private val AvatarColors = listOf(Color(0xFFFF7A59), Color(0xFF5ED1A8), Color(0xFFF5C451), Color(0xFFA89BFF))

@Composable
fun CommentsScreen(seriesId: String, chapterId: String?, onBack: () -> Unit) {
    val vm = containerViewModel(key = "comments_${seriesId}_$chapterId") { CommentsViewModel(it.repo, seriesId, chapterId) }
    val tab by vm.tab.collectAsStateWithLifecycle()
    val sortMode by vm.sort.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val replies by vm.replies.collectAsStateWithLifecycle()
    val replyTo by vm.replyTo.collectAsStateWithLifecycle()
    val posting by vm.posting.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var hideSpoilers by remember { mutableStateOf(true) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var draft by remember { mutableStateOf("") }
    var spoiler by remember { mutableStateOf(false) }
    var askName by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenHeader("Comments", onBack, subtitle = title.ifBlank { null })
            if (!vm.cloudAvailable) {
                MessageView("Community is not set up", "Comments need the Firebase backend. Add google-services.json and rebuild to turn them on.")
                return@Column
            }
            if (chapterId != null) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("This chapter", tab == 0, onClick = { vm.selectTab(0) })
                    Chip("Whole series", tab == 1, onClick = { vm.selectTab(1) })
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Top", sortMode == CommentSort.TOP, onClick = { vm.selectSort(CommentSort.TOP) })
                Chip("Newest", sortMode == CommentSort.NEWEST, onClick = { vm.selectSort(CommentSort.NEWEST) })
                Spacer(Modifier.weight(1f))
                Text("Hide spoilers", fontSize = 13.sp, color = Palette.Muted)
                Switch(
                    checked = hideSpoilers, onCheckedChange = { hideSpoilers = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Palette.OnAccent, checkedTrackColor = Palette.Accent, uncheckedThumbColor = Palette.Text, uncheckedTrackColor = Color(0xFF3A3A45), uncheckedBorderColor = Color.Transparent, checkedBorderColor = Color.Transparent),
                )
            }
            Box(Modifier.weight(1f)) {
                StateHost(items, onRetry = vm::load) { list ->
                    if (list.isEmpty()) {
                        MessageView("No comments yet", "Be the first to say something.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
                            items(list, key = { it.id }) { c ->
                                CommentCell(
                                    c, hide = hideSpoilers && c.spoiler && c.id !in revealed,
                                    onReveal = { revealed = revealed + c.id },
                                    onLike = { vm.like(c) }, onReply = { vm.replyTo(c) },
                                    onToggleReplies = { vm.toggleReplies(c) },
                                    onReport = { vm.report(c) }, onDelete = { vm.delete(c) },
                                    avatarColor = AvatarColors[(c.name.length) % AvatarColors.size],
                                )
                                replies[c.id]?.forEach { r ->
                                    Box(Modifier.padding(start = 40.dp)) {
                                        CommentCell(
                                            r, hide = hideSpoilers && r.spoiler && r.id !in revealed,
                                            onReveal = { revealed = revealed + r.id },
                                            onLike = { vm.like(r) }, onReply = { vm.replyTo(c) },
                                            onToggleReplies = null,
                                            onReport = { vm.report(r) }, onDelete = { vm.delete(r) },
                                            avatarColor = AvatarColors[(r.name.length) % AvatarColors.size],
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.background(Palette.Background).navigationBarsPadding().padding(12.dp)) {
                if (replyTo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        Text("Replying to ${replyTo?.name}", fontSize = 13.sp, color = Palette.AccentSoft, modifier = Modifier.weight(1f))
                        Text("Cancel", fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.pressClick { vm.replyTo(null) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft, onValueChange = { if (it.length <= 1000) draft = it },
                        placeholder = { Text("Add a comment") }, colors = bingeFieldColors(),
                        maxLines = 3, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                    )
                    Chip("Spoiler", spoiler, onClick = { spoiler = !spoiler })
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = if (draft.isBlank() || posting) Palette.Raised else Palette.Accent,
                        modifier = Modifier.size(48.dp).pressClick(enabled = draft.isNotBlank() && !posting) {
                            if (settings.displayName.isBlank()) askName = true
                            else vm.post(draft, spoiler) { draft = ""; spoiler = false }
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send comment", tint = if (draft.isBlank() || posting) Palette.Muted else Palette.OnAccent)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp, start = 16.dp, end = 16.dp))
    }

    if (askName) NameDialog(initial = "", onSave = { vm.setName(it); askName = false }, onDismiss = { askName = false })
}

@Composable
fun NameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    SimpleDialog("Choose a display name", "Save", onConfirm = { onSave(name) }, onDismiss = onDismiss, confirmEnabled = name.trim().length >= 2) {
        Column {
            Text("This is what friends see next to your ratings and comments.", color = Palette.Muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 30) name = it }, singleLine = true, colors = bingeFieldColors())
        }
    }
}

@Composable
private fun CommentCell(
    c: CommentItem,
    hide: Boolean,
    onReveal: () -> Unit,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onToggleReplies: (() -> Unit)?,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    avatarColor: Color,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(avatarColor), contentAlignment = Alignment.Center) {
            Text(c.name.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Palette.OnAccent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Spacer(Modifier.width(8.dp))
                Text(TimeAgo.formatMillis(c.createdAtMs), fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.weight(1f))
                Box {
                    IconAction(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Palette.Muted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (c.mine) DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                        else DropdownMenuItem(text = { Text("Report") }, onClick = { menu = false; onReport() })
                    }
                }
            }
            if (hide) {
                Surface(
                    shape = RoundedCornerShape(10.dp), color = Palette.Surface,
                    modifier = Modifier.fillMaxWidth().height(40.dp).pressClick(onClick = onReveal),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("Spoiler hidden. Tap to show.", fontSize = 13.sp, color = Palette.Text) }
                }
            } else {
                Text(c.text, fontSize = 14.sp, lineHeight = 20.sp, color = Palette.Text.copy(alpha = 0.9f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 4.dp)) {
                Row(Modifier.pressClick(onClick = onLike).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (c.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like", modifier = Modifier.size(18.dp),
                        tint = if (c.likedByMe) Palette.AccentSoft else Palette.Muted,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${c.likeCount}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (c.likedByMe) Palette.AccentSoft else Palette.Muted)
                }
                if (onToggleReplies != null && c.replyCount > 0) {
                    Text("${c.replyCount} replies", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted, modifier = Modifier.pressClick(onClick = onToggleReplies).padding(vertical = 6.dp))
                }
                Text("Reply", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.AccentSoft, modifier = Modifier.pressClick(onClick = onReply).padding(vertical = 6.dp))
            }
        }
    }
}
