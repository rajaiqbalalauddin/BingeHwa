package com.raja.bingehwa.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raja.bingehwa.data.cloud.RatingInput
import com.raja.bingehwa.ui.components.Chip
import com.raja.bingehwa.ui.components.PrimaryButton
import com.raja.bingehwa.ui.components.ToggleRow
import com.raja.bingehwa.ui.components.bingeFieldColors
import com.raja.bingehwa.ui.theme.Palette

private val Words = listOf("", "Not for me", "It was fine", "Good", "Great", "Loved it")
private val QuickTags = listOf("Great art", "Slow burn", "Strong plot", "Dark mood", "Funny", "Well paced")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RateSheet(
    seriesTitle: String,
    initial: RatingInput?,
    submitting: Boolean,
    cloudAvailable: Boolean,
    onSubmit: (RatingInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var stars by remember { mutableIntStateOf(initial?.stars ?: 0) }
    var tags by remember { mutableStateOf(initial?.tags?.toSet() ?: emptySet()) }
    var review by remember { mutableStateOf(initial?.review ?: "") }
    var isPublic by remember { mutableStateOf(initial?.isPublic ?: true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Rate this series", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
            Text(seriesTitle, fontSize = 14.sp, color = Palette.Muted, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (n in 1..5) {
                    IconButton(onClick = { stars = n }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "$n stars",
                            tint = if (n <= stars) Palette.Gold else Palette.Line,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            Text(Words[stars], fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Gold, modifier = Modifier.height(24.dp))
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTags.forEach { t -> Chip(t, t in tags, onClick = { tags = if (t in tags) tags - t else tags + t }) }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = review,
                onValueChange = { if (it.length <= 400) review = it },
                placeholder = { Text("Add a short review (optional)") },
                supportingText = { Text("${review.length} / 400", color = Palette.Muted) },
                colors = bingeFieldColors(),
                minLines = 3,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow("Show my rating publicly", "Others see your stars and review", isPublic, { isPublic = it })
            if (!cloudAvailable) {
                Text("Ratings need the community backend, which is not set up in this build.", fontSize = 13.sp, color = Palette.Muted)
                Spacer(Modifier.height(8.dp))
            }
            PrimaryButton(
                "Submit rating",
                onClick = { onSubmit(RatingInput(stars, tags.toList(), review.trim(), isPublic)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = stars > 0 && cloudAvailable,
                loading = submitting,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
