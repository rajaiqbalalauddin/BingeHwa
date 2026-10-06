package com.raja.bingehwa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raja.bingehwa.ui.components.PrimaryButton
import com.raja.bingehwa.ui.theme.Palette

/** First screen: says what the app is in three lines, then hands off to taste picking. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(top = 56.dp)) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("B", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Palette.OnAccent)
            }
            Spacer(Modifier.height(28.dp))
            Text("BingeHwa", style = MaterialTheme.typography.headlineLarge, color = Palette.Text, fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text("Your manhwa, always up to date. No ads, ever.", fontSize = 18.sp, color = Palette.Muted)
            Spacer(Modifier.height(32.dp))
            listOf(
                "Alerts the moment a new chapter drops",
                "Download chapters and read offline",
                "Picks that match your taste",
            ).forEach {
                Text(it, fontSize = 16.sp, color = Palette.Text, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        PrimaryButton("Get started", onStart, Modifier.fillMaxWidth())
    }
}
