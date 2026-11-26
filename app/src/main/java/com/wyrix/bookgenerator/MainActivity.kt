@file:OptIn(ExperimentalMaterial3Api::class)
package com.wyrix.bookgenerator

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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

/** Play Console me privacy policy URL yahi dalna hai. privacy_policy.html ko apni site/GitHub Pages par host karke yahan URL badal dein. */
const val PRIVACY_URL = "https://YOUR-USERNAME.github.io/wyrix-privacy/privacy_policy.html"
/** Play AI-content policy: users ko offensive/galat content report karne ka tareeka chahiye. Apna support email yahan dalein. */
const val SUPPORT_EMAIL = "support@yourdomain.com"
val GENRES = listOf("Story", "Novel", "Self-help", "Kids", "Educational", "Biography")

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        enableEdgeToEdge()
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

fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: ActivityNotFoundException) { Toast.makeText(ctx, "No browser found", Toast.LENGTH_SHORT).show() }
}

fun reportContent(ctx: Context) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
            .putExtra(Intent.EXTRA_SUBJECT, "WYRIX - report AI content / feedback").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) { Toast.makeText(ctx, "No email app found. Write to $SUPPORT_EMAIL", Toast.LENGTH_LONG).show() }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var openId by remember { mutableStateOf(0L) }
    BackHandler(screen != "home") { screen = if (screen == "read") "book" else "home" }
    Screen {
        when (screen) {
            "new" -> NewBook({ screen = "settings" }) { id -> startBook(ctx, id); openId = id; screen = "book" }
            "settings" -> SettingsScreen { screen = "home" }
            "about" -> AboutScreen { screen = "home" }
            "read" -> ReaderScreen(openId) { screen = "book" }
            "book" -> BookScreen(openId, { screen = "read" }) { screen = "home" }
            else -> Home({ screen = "new" }, { screen = "settings" }, { screen = "about" }) { openId = it; screen = "book" }
        }
    }
}

@Composable
fun Home(onNew: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember(ctx) { Db.get(ctx).dao() }
    val books by dao.books().collectAsState(emptyList())
    val hasKeys = remember { Providers.hasKeys(ctx) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            GradTitle("WYRIX", 30.sp)
            Text("AI BOOK GENERATOR & STORY WRITER", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).clickable(onClick = onAbout), contentAlignment = Alignment.Center) { Text("ℹ️", fontSize = 18.sp) }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).clickable(onClick = onSettings), contentAlignment = Alignment.Center) { Text("⚙️", fontSize = 20.sp) }
    }
    Spacer(Modifier.height(20.dp))
    if (!hasKeys) {
        Glass(Modifier.clickable(onClick = onSettings), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🔑  Add a free API key to start", fontWeight = FontWeight.Bold, color = Gold)
            Text("Open Settings - every provider has step-by-step instructions to get its key.", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
    GradButton("✨  Create new book", onClick = onNew)
    Spacer(Modifier.height(16.dp))
    if (books.isEmpty()) Glass { Text("📚  No books yet. Write one prompt and the app does the rest.", color = Muted) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(books, key = { it.id }) { b ->
            Glass(Modifier.clickable { onOpen(b.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(b.title.ifBlank { b.prompt }.trim().take(1).uppercase(), b.id)
                    Column(Modifier.weight(1f)) {
                        Text(b.title.ifBlank { b.prompt.take(40) }, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text("${b.genre} • ${b.pages} pages • ${b.lang}", color = Muted, fontSize = 13.sp)
                    }
                    StatusPill(b.status)
                }
            }
        }
    }
}

@Composable
fun NewBook(onNeedKeys: () -> Unit, onDone: (Long) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("hi") }
    var genre by remember { mutableStateOf("Story") }
    var pages by remember { mutableStateOf("100") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GradTitle("New book")
        Glass(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(prompt, { prompt = it.take(3000) }, label = { Text("Describe your book or story") }, colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().height(170.dp))
            Text("Type", color = Muted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GENRES.forEach { g -> Chip(g, genre == g) { genre = g } }
            }
            Text("Language", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("hi" to "Hindi", "en" to "English", "both" to "Both").forEach { (k, l) -> Chip(l, lang == k) { lang = k } }
            }
            OutlinedTextField(pages, { pages = it.filter(Char::isDigit).take(3) }, label = { Text("Pages (10-600, approx.)") },
                singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            Text("AI-written text can contain mistakes. Please review before publishing.", color = Muted, fontSize = 12.sp)
        }
        GradButton("🚀  Generate book", enabled = prompt.isNotBlank()) {
            if (!Providers.hasKeys(ctx)) {
                Toast.makeText(ctx, "Add at least one API key first", Toast.LENGTH_LONG).show(); onNeedKeys()
            } else scope.launch {
                val id = Db.get(ctx).dao().addBook(Book(prompt = prompt.trim(), lang = lang, genre = genre, pages = (pages.toIntOrNull() ?: 100).coerceIn(10, 600)))
                onDone(id)
            }
        }
    }
}

@Composable
fun BookScreen(id: Long, onRead: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val dao = remember { Db.get(ctx).dao() }
    val b by dao.bookFlow(id).collectAsState(null)
    val done by dao.done(id).collectAsState(0)
    val total by dao.total(id).collectAsState(0)
    var exported by remember { mutableStateOf<Export.Result?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    fun export(pdf: Boolean) = scope.launch {
        exporting = true
        try {
            val r = Export.run(ctx, id, pdf)
            if (r.uris.isEmpty()) Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show() else exported = r
        } catch (e: Exception) {
            Toast.makeText(ctx, "Export failed: ${e.message?.take(80)}", Toast.LENGTH_LONG).show()
        }
        exporting = false
    }
    val pct = if (total > 0) done / total.toFloat() else 0f

    exported?.let { r ->
        AlertDialog(
            onDismissRequest = { exported = null },
            title = { Text("Saved") }, text = { Text(r.message) },
            confirmButton = {
                TextButton({
                    val i = if (r.uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, r.uris[0])
                    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(r.uris))
                    i.type = r.mime; i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(i, "Share book")); exported = null
                }) { Text("Share") }
            },
            dismissButton = {
                Row {
                    TextButton({
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(r.uris[0], r.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch (e: ActivityNotFoundException) { Toast.makeText(ctx, "No app found to open this file", Toast.LENGTH_SHORT).show() }
                        exported = null
                    }) { Text("Open") }
                    TextButton({ exported = null }) { Text("Close") }
                }
            }
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this book?") }, text = { Text("The book and all generated text will be removed from this device. Exported files stay in Downloads.") },
        confirmButton = {
            TextButton({
                confirmDelete = false
                scope.launch {
                    WorkManager.getInstance(ctx).cancelUniqueWork("book-$id")
                    dao.deleteSections(id); dao.deleteBook(id); onBack()
                }
            }) { Text("Delete", color = Pink) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
    )

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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradButton("▶  Resume", Modifier.weight(1f), brush = CoolBrush) {
                        scope.launch { dao.setStatus(id, "RUNNING") }; startBook(ctx, id)
                    }
                    if (bk.status == "RUNNING") GhostButton("⏸  Pause", Modifier.weight(1f)) {
                        WorkManager.getInstance(ctx).cancelUniqueWork("book-$id"); scope.launch { dao.setStatus(id, "PAUSED") }
                    }
                }
            }
            if (done > 0) {
                GradButton("📖  Read in app", brush = CoolBrush, onClick = onRead)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradButton(if (exporting) "Saving..." else "📄 PDF", Modifier.weight(1f), enabled = !exporting) { export(true) }
                    GradButton(if (exporting) "Saving..." else "📕 EPUB", Modifier.weight(1f), enabled = !exporting) { export(false) }
                }
            }
            GhostButton("🗑  Delete book") { confirmDelete = true }
        }
        GhostButton("←  Back", onClick = onBack)
    }
}

@Composable
fun ColumnScope.ReaderScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { Db.get(ctx).dao() }
    var book by remember { mutableStateOf<Book?>(null) }
    var rows by remember { mutableStateOf<List<Pair<Boolean, Pair<String, String>>>>(emptyList()) }   // (isHeading, (hi/main text, en text))
    var second by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(16) }
    LaunchedEffect(id) {
        book = dao.book(id)
        val list = ArrayList<Pair<Boolean, Pair<String, String>>>()
        var last = -1
        dao.sections(id).filter { it.status == "DONE" || it.text.isNotBlank() }.forEach { s ->
            if (s.chIdx != last) { list.add(true to (s.chTitle to s.chTitle)); last = s.chIdx }
            list.add(false to (s.text to s.text2))
        }
        rows = list
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { GradTitle(book?.title?.ifBlank { "Reader" } ?: "Reader", 20.sp) }
        if (book?.lang == "both") Chip(if (second) "EN" else "HI", true) { second = !second }
        Chip("A-", false) { size = (size - 2).coerceAtLeast(12) }
        Chip("A+", false) { size = (size + 2).coerceAtMost(28) }
    }
    Spacer(Modifier.height(10.dp))
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(rows.size) { i ->
            val (head, t) = rows[i]
            if (head) Text(t.first, color = Gold, fontWeight = FontWeight.Bold, fontSize = (size + 4).sp, modifier = Modifier.padding(top = 12.dp))
            else Text(if (second && t.second.isNotBlank()) t.second else t.first, fontSize = size.sp, lineHeight = (size * 1.6f).sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("⚑  Report content", Modifier.weight(1f)) { reportContent(ctx) }
        GhostButton("←  Back", Modifier.weight(1f), onClick = onBack)
    }
}

@Composable
fun KeyHelp(steps: String, url: String) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (open) "How to get this key  ▲" else "How to get this key  ▼", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp))
        if (open) {
            Text(steps, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            if (url.isNotBlank()) GhostButton("🔗  Open website to get key") { openUrl(ctx, url) }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val p = remember { Prefs.keys(ctx) }
    var tick by remember { mutableStateOf(0) }
    val total = remember(tick) { Providers.totalCached(ctx) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GradTitle("API keys")
        Text("The more provider keys you add, the more free models connect automatically. Keys are stored encrypted on your device only.", color = Muted, fontSize = 13.sp)
        Glass { Text("Total models", color = Muted, fontSize = 12.sp); Text("$total", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Gold) }
        Providers.provs.forEach { pr ->
            var v by remember { mutableStateOf(p.getString(pr.id, "").orEmpty()) }
            var cnt by remember { mutableStateOf(Providers.cachedCount(ctx, pr)) }
            var busy by remember { mutableStateOf(false) }
            Glass(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pr.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("$cnt models", color = if (cnt > 0) Mint else Muted, fontSize = 12.sp)
                }
                OutlinedTextField(v, { v = it; p.edit().putString(pr.id, it.trim()).apply() }, label = { Text("API key") }, singleLine = true,
                    colors = fieldColors(), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                TextButton({
                    scope.launch {
                        busy = true
                        cnt = Providers.refresh(ctx, pr, v.trim()); tick++
                        busy = false
                        Toast.makeText(ctx, if (cnt > 0) "$cnt models found" else "No models found - check the key and internet", Toast.LENGTH_SHORT).show()
                    }
                }, enabled = v.isNotBlank() && !busy) { Text(if (busy) "Fetching..." else "Fetch models", color = Cyan) }
                KeyHelp(pr.steps, pr.keyUrl)
            }
        }
        Glass(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Custom (any OpenAI-compatible API)", fontWeight = FontWeight.Bold)
            listOf("custom_url" to "Base URL (e.g. https://api.deepseek.com/v1)", "custom_key" to "Key", "custom_model" to "Model name (e.g. deepseek-chat)").forEach { (k, l) ->
                var v by remember { mutableStateOf(p.getString(k, "").orEmpty()) }
                OutlinedTextField(v, { v = it; p.edit().putString(k, it.trim()).apply(); tick++ }, label = { Text(l) }, singleLine = true,
                    colors = fieldColors(), visualTransformation = if (k == "custom_key") PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth())
            }
            KeyHelp(
                "Works with any service that offers an OpenAI-compatible chat API. Example - DeepSeek:\n1. Open platform.deepseek.com and sign up.\n2. Go to API keys > \"Create new API key\" and copy it.\n3. Base URL: https://api.deepseek.com/v1  |  Model: deepseek-chat\nFor other services (Together, Fireworks, xAI, etc.) use the base URL and model name from their API docs.",
                "https://platform.deepseek.com/api_keys"
            )
        }
        GhostButton("←  Back", onClick = onBack)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() } catch (e: Exception) { "" } }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GradTitle("About")
        Glass(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("WYRIX - AI Book Generator & Story Writer", fontWeight = FontWeight.Bold)
            Text("Version $ver", color = Muted, fontSize = 13.sp)
            Text("Write one prompt and get a full book or story in Hindi, English or both, exported as PDF or EPUB.", color = Muted, fontSize = 13.sp)
        }
        Glass(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Privacy", fontWeight = FontWeight.Bold)
            Text("• Your prompts and the text being written are sent directly from your phone to the AI providers whose API keys you added (for example Google Gemini, Groq, OpenRouter). Their own privacy policies apply to that data.\n• Your API keys are stored encrypted on this device and are never sent to us.\n• Books are stored only on your device. We run no servers and collect no personal data.\n• You can delete any book, or clear the app data, at any time.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            GhostButton("🔗  Full privacy policy") { openUrl(ctx, PRIVACY_URL) }
        }
        Glass(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("AI content notice", fontWeight = FontWeight.Bold)
            Text("Books are generated by AI models. They can contain factual errors, repeated ideas or unsuitable passages. Review and edit before you publish or share. You are responsible for how you use the generated content.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            GhostButton("⚑  Report offensive content / send feedback") { reportContent(ctx) }
        }
        GhostButton("←  Back", onClick = onBack)
    }
}
