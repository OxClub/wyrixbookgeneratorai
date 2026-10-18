package com.wyrix.bookgenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Violet = Color(0xFF8B5CF6); val Pink = Color(0xFFEC4899); val Cyan = Color(0xFF22D3EE)
val Gold = Color(0xFFFBBF24); val Mint = Color(0xFF34D399)
val Ink = Color(0xFF0B0B1A); val Deep = Color(0xFF1B1140); val Muted = Color(0xFFB4B4D0)
val BrandBrush = Brush.horizontalGradient(listOf(Violet, Pink))
val CoolBrush = Brush.horizontalGradient(listOf(Cyan, Violet))
private val Edge = Color(0x22FFFFFF)

@Composable
fun PremiumTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Violet, secondary = Cyan, tertiary = Pink, background = Ink, surface = Ink,
        onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Muted),
    content = content
)

@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Deep, Ink)))) {
        Surface(color = Color.Transparent, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp), content = content)
        }
    }
}

@Composable
fun Glass(modifier: Modifier = Modifier, verticalArrangement: Arrangement.Vertical = Arrangement.Top, content: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(sh).background(Color(0x14FFFFFF)).border(1.dp, Edge, sh).padding(16.dp), verticalArrangement = verticalArrangement, content = content)
}

@Composable
fun GradButton(text: String, modifier: Modifier = Modifier, brush: Brush = BrandBrush, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (enabled) brush else Brush.horizontalGradient(listOf(Color(0xFF3A3A55), Color(0xFF3A3A55)))
    Box(modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sh = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().height(48.dp).clip(sh).border(1.dp, Color(0x44FFFFFF), sh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontSize = 15.sp) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val sh = RoundedCornerShape(50)
    val m = if (selected) Modifier.background(BrandBrush, sh) else Modifier.background(Color(0x14FFFFFF), sh).border(1.dp, Edge, sh)
    Box(Modifier.clip(sh).then(m).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp)) { Text(text, color = Color.White, fontSize = 14.sp) }
}

@Composable
fun StatusPill(status: String) {
    val c = when (status) { "DONE" -> Mint; "RUNNING" -> Cyan; else -> Color(0xFF9CA3AF) }
    Text(status, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun GradTitle(text: String, size: TextUnit = 26.sp) =
    Text(text, style = TextStyle(brush = Brush.linearGradient(listOf(Cyan, Violet, Pink)), fontSize = size, fontWeight = FontWeight.Bold))

@Composable
fun Cover(letter: String, seed: Long) {
    val sets = listOf(listOf(Violet, Pink), listOf(Cyan, Violet), listOf(Gold, Pink))
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(sets[(seed % 3).toInt()])), contentAlignment = Alignment.Center) {
        Text(letter, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Violet, unfocusedBorderColor = Color(0x33FFFFFF), focusedLabelColor = Violet, unfocusedLabelColor = Muted,
    cursorColor = Pink, focusedContainerColor = Color(0x0FFFFFFF), unfocusedContainerColor = Color(0x0AFFFFFF),
    focusedTextColor = Color.White, unfocusedTextColor = Color.White
)
