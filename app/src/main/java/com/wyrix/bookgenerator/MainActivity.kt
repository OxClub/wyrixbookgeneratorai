@file:OptIn(ExperimentalMaterial3Api::class)
package com.wyrix.bookgenerator

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { PremiumTheme { App() } }
    }
}

fun startBook(ctx: Context, id: Long) {
    val req = OneTimeWorkRequestBuilder<BookWorker>()
        .setInputData(workDataOf("id" to id))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES).build()
    WorkManager.getInstance(ctx).enqueueUniqueWork("book-$id", ExistingWorkPolicy.KEEP, req)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var openId by remember { mutableStateOf(0L) }
    BackHandler(screen != "home") { screen = "home" }
    Screen {
        when (screen) {
            "new" -> NewBook { id -> startBook(ctx, id); openId = id; screen = "book" }
            "settings" -> SettingsScreen { screen = "home" }
            "book" -> BookScreen(openId) { screen = "home" }
            else -> Home({ screen = "new" }, { screen = "settings" }) { openId = it; screen = "book" }
        }
    }
}

@Composable
fun Home(onNew: () -> Unit, onSettings: () -> Unit, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember(ctx) { Db.get(ctx).dao() }
    val books by dao.books().collectAsState(emptyList())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            GradTitle("WYRIX", 30.sp)
            Text("BOOK GENERATOR AI", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).clickable(onClick = onSettings), contentAlignment = Alignment.Center) { Text("⚙️", fontSize = 20.sp) }
    }
    Spacer(Modifier.height(20.dp))
    GradButton("✨  Create new book", onClick = onNew)
    Spacer(Modifier.height(16.dp))
    if (books.isEmpty()) Glass { Text("📚  No books yet. Write one prompt and the app does the rest.", color = Muted) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(books) { b ->
            Glass(Modifier.clickable { onOpen(b.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(b.title.ifBlank { b.prompt }.trim().take(1).uppercase(), b.id)
                    Column(Modifier.weight(1f)) {
                        Text(b.title.ifBlank { b.prompt.take(40) }, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text("${b.pages} pages • ${b.lang}", color = Muted, fontSize = 13.sp)
                    }
                    StatusPill(b.status)
                }
            }
        }
    }
}

@Composable
fun NewBook(onDone: (Long) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("hi") }
    var pages by remember { mutableStateOf("100") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GradTitle("New book")
        Glass(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(prompt, { prompt = it }, label = { Text("Describe your book") }, colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().height(170.dp))
            Text("Language", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("hi" to "Hindi", "en" to "English", "both" to "Both").forEach { (k, l) -> Chip(l, lang == k) { lang = k } }
            }
            OutlinedTextField(pages, { pages = it.filter(Char::isDigit) }, label = { Text("Pages (10-600, approx.)") },
                singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        }
        GradButton("🚀  Generate book", enabled = prompt.isNotBlank()) {
            scope.launch {
                val id = Db.get(ctx).dao().addBook(Book(prompt = prompt.trim(), lang = lang, pages = (pages.toIntOrNull() ?: 100).coerceIn(10, 600)))
                onDone(id)
            }
        }
    }
}

@Composable
fun BookScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val dao = remember { Db.get(ctx).dao() }
    var b by remember { mutableStateOf<Book?>(null) }
    val done by dao.done(id).collectAsState(0)
    val total by dao.total(id).collectAsState(0)
    LaunchedEffect(done, total) { b = dao.book(id) }
    fun export(pdf: Boolean) = scope.launch { Toast.makeText(ctx, Export.run(ctx, id, pdf), Toast.LENGTH_LONG).show() }
    val pct = if (total > 0) done / total.toFloat() else 0f
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        b?.let { bk ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { GradTitle(bk.title.ifBlank { "Generating book..." }, 22.sp) }
                StatusPill(bk.status)
            }
            Glass {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp,
                            color = Cyan, trackColor = Color(0x22FFFFFF), strokeCap = StrokeCap.Round)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(pct * 100).toInt()}%", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            Text("complete", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Glass(Modifier.weight(1f)) { Text("Sections", color = Muted, fontSize = 12.sp); Text("$done / $total", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                Glass(Modifier.weight(1f)) { Text("Pages (~)", color = Muted, fontSize = 12.sp); Text("${done * 800 / 300}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold) }
            }
            if (bk.status != "DONE") {
                Text("Generation keeps running in the background even if you close the app. If a free limit runs out, it pauses and resumes automatically.", color = Muted, fontSize = 13.sp)
                GradButton("▶  Resume generation", brush = CoolBrush) { startBook(ctx, id) }
            }
            if (done > 0) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradButton("📄 PDF", Modifier.weight(1f)) { export(true) }
                GradButton("📖 EPUB", Modifier.weight(1f)) { export(false) }
            }
        }
        GhostButton("←  Back", onClick = onBack)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val p = ctx.getSharedPreferences("keys", 0)
    var tick by remember { mutableStateOf(0) }
    val total = remember(tick) { Providers.totalCached(ctx) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GradTitle("API keys")
        Text("The more provider keys you add, the more free models connect automatically.", color = Muted, fontSize = 13.sp)
        Glass { Text("Total models", color = Muted, fontSize = 12.sp); Text("$total", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Gold) }
        Providers.provs.forEach { pr ->
            var v by remember { mutableStateOf(p.getString(pr.id, "").orEmpty()) }
            var cnt by remember { mutableStateOf(Providers.cachedCount(ctx, pr)) }
            Glass(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pr.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("$cnt models", color = if (cnt > 0) Mint else Muted, fontSize = 12.sp)
                }
                OutlinedTextField(v, { v = it; p.edit().putString(pr.id, it.trim()).apply() }, label = { Text("API key") }, singleLine = true,
                    colors = fieldColors(), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                TextButton({ scope.launch { cnt = Providers.refresh(ctx, pr, v.trim()); tick++ } }, enabled = v.isNotBlank()) { Text("Fetch models", color = Cyan) }
            }
        }
        Glass(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Custom (any OpenAI-compatible API)", fontWeight = FontWeight.Bold)
            listOf("custom_url" to "Base URL (e.g. https://api.deepseek.com/v1)", "custom_key" to "Key", "custom_model" to "Model name").forEach { (k, l) ->
                var v by remember { mutableStateOf(p.getString(k, "").orEmpty()) }
                OutlinedTextField(v, { v = it; p.edit().putString(k, it.trim()).apply(); tick++ }, label = { Text(l) }, singleLine = true,
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            }
        }
        GhostButton("←  Back", onClick = onBack)
    }
}
