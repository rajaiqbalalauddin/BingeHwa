package com.raja.bingehwa.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.raja.bingehwa.data.model.UiState
import com.raja.bingehwa.ui.theme.Palette

/** Press-down scale from the mockup: buttons shrink slightly while held. */
fun Modifier.pressClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Palette.Surface,
                border = BorderStroke(1.dp, Palette.Line),
                modifier = Modifier.size(44.dp).pressClick(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Palette.Text)
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 20.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Palette.Muted,
    )
}

/** Cover art with a flat fallback block so a missing image never leaves a hole in the grid. */
@Composable
fun CoverImage(url: String?, title: String, modifier: Modifier = Modifier, radius: Dp = 12.dp) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(Palette.Raised), contentAlignment = Alignment.Center) {
        Text(title.take(1).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Muted)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Accent)
    }
}

@Composable
fun MessageView(title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 14.sp, color = Palette.Muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageView("Something went wrong", message, modifier, "Try again", onRetry)

/** Renders loading, error or content from one [UiState], so every screen handles all three. */
@Composable
fun <T> StateHost(state: UiState<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    when (state) {
        is UiState.Loading -> LoadingView(modifier)
        is UiState.Error -> ErrorView(state.message, onRetry, modifier)
        is UiState.Data -> content(state.value)
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) Palette.Accent else Palette.Raised,
        modifier = modifier.height(52.dp).pressClick(enabled = enabled && !loading, onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Palette.OnAccent, strokeWidth = 2.dp)
            } else {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (enabled) Palette.OnAccent else Palette.Muted)
            }
        }
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.Line),
        modifier = modifier.height(52.dp).pressClick(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (enabled) Palette.Text else Palette.Muted)
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Palette.Accent.copy(alpha = 0.18f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) Palette.Accent else Palette.Line),
        modifier = modifier.height(40.dp).pressClick(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Palette.AccentSoft else Palette.Text)
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Palette.Text else Palette.Muted)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnAccent,
                checkedTrackColor = Palette.Accent,
                uncheckedThumbColor = Palette.Text,
                uncheckedTrackColor = Color(0xFF3A3A45),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** A rounded surface card used across Profile, Sources and Detail. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.Line),
        modifier = modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
fun IconAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { content() }
}

/** One text-field look for every input in the app. */
@Composable
fun bingeFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.Line,
    focusedContainerColor = Palette.Background,
    unfocusedContainerColor = Palette.Background,
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
    cursorColor = Palette.Accent,
    focusedPlaceholderColor = Palette.Muted,
    unfocusedPlaceholderColor = Palette.Muted,
)

/** Tiny confirm/cancel dialog used for names, imports and destructive actions. */
@Composable
fun SimpleDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title, color = Palette.Text, fontWeight = FontWeight.Bold) },
        text = { content() },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmLabel, color = if (confirmEnabled) Palette.AccentSoft else Palette.Muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.Muted) }
        },
    )
}
