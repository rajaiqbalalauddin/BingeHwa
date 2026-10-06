package com.raja.bingehwa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raja.bingehwa.data.model.Series
import com.raja.bingehwa.ui.components.CoverImage
import com.raja.bingehwa.ui.components.PrimaryButton
import com.raja.bingehwa.ui.components.SecondaryButton
import com.raja.bingehwa.ui.components.ToggleRow
import com.raja.bingehwa.ui.components.bingeFieldColors
import com.raja.bingehwa.ui.theme.Palette

/**
 * Builds the text that goes out. The link is the MangaDex page because it opens for anyone,
 * with or without this app; the bingehwa:// link is added for friends who have it installed.
 */
fun buildShareText(series: Series, note: String, myStars: Int?): String = buildString {
    if (note.isNotBlank()) append(note.trim()).append("\n\n")
    append(series.title).append(" - recommended on BingeHwa")
    if (myStars != null) append("\nI rated it $myStars of 5 stars")
    append("\nhttps://mangadex.org/title/").append(series.id)
    append("\nOpen in BingeHwa: bingehwa://series/").append(series.id)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(series: Series, myStars: Int?, onDismiss: () -> Unit, onCopied: () -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var includeRating by remember { mutableStateOf(myStars != null) }
    val text = buildShareText(series, note, if (includeRating) myStars else null)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Share", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = Palette.Background, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(series.coverUrl, series.title, Modifier.width(60.dp).height(84.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("RECOMMENDED ON BINGEHWA", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Palette.AccentSoft)
                        Text(series.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (includeRating && myStars != null) Text("I rated it $myStars stars", fontSize = 13.sp, color = Palette.Gold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                placeholder = { Text("Add a note for your friend (optional)") },
                colors = bingeFieldColors(),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow("Include my rating", if (myStars == null) "Rate this series first to include it" else null, includeRating, { includeRating = it }, enabled = myStars != null)
            Spacer(Modifier.height(8.dp))
            Row {
                SecondaryButton("Copy link", onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("BingeHwa", text))
                    onCopied()
                }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                PrimaryButton("Send to...", onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Share ${series.title}"))
                }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
