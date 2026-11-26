package com.wyrix.bookgenerator

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NoKeys : Exception("Add at least one API key in Settings")
class RateLimit : Exception("429")
class BadModel : Exception("model unusable")

/** kind = "openai" (OpenAI-compatible) ya "gemini". listUrl = models list ka alag URL (agar ho). */
class Prov(val id: String, val name: String, val base: String, val kind: String = "openai", val listUrl: String? = null,
           val keyUrl: String = "", val steps: String = "")
class Entry(val p: Prov, val key: String, val model: String) { val id get() = "${p.id}:$model" }

object Providers {
    // Naya provider jodna ho to bas yahan ek line add karein. Order = priority.
    val provs = listOf(
        Prov("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini",
            keyUrl = "https://aistudio.google.com/apikey",
            steps = "1. Open aistudio.google.com/apikey and sign in with your Google account.\n2. Tap \"Create API key\" (choose any project or let it create one).\n3. Copy the key (it starts with AIza) and paste it above.\nFree tier, no card needed."),
        Prov("groq", "Groq", "https://api.groq.com/openai/v1",
            keyUrl = "https://console.groq.com/keys",
            steps = "1. Open console.groq.com/keys and sign up / log in.\n2. Tap \"Create API Key\", give it a name.\n3. Copy the key (starts with gsk_) - it is shown only once - and paste it above.\nFree tier, no card needed."),
        Prov("openrouter", "OpenRouter (free models)", "https://openrouter.ai/api/v1",
            keyUrl = "https://openrouter.ai/keys",
            steps = "1. Open openrouter.ai/keys and sign in (Google/GitHub works).\n2. Tap \"Create Key\" and name it.\n3. Copy the key (starts with sk-or-) and paste it above.\nThe app uses only the models marked free."),
        Prov("cerebras", "Cerebras", "https://api.cerebras.ai/v1",
            keyUrl = "https://cloud.cerebras.ai",
            steps = "1. Open cloud.cerebras.ai and create a free account.\n2. Go to \"API Keys\" in the left menu.\n3. Tap \"Create API key\", copy it (starts with csk-) and paste it above."),
        Prov("mistral", "Mistral", "https://api.mistral.ai/v1",
            keyUrl = "https://console.mistral.ai/api-keys",
            steps = "1. Open console.mistral.ai and create an account.\n2. Choose the free \"Experiment\" plan and verify your phone number when asked.\n3. Go to API Keys > \"Create new key\", copy it and paste it above."),
        Prov("sambanova", "SambaNova", "https://api.sambanova.ai/v1",
            keyUrl = "https://cloud.sambanova.ai/apis",
            steps = "1. Open cloud.sambanova.ai and sign up.\n2. Open the \"APIs\" page from the menu.\n3. Tap \"Create API key\", copy it and paste it above."),
        Prov("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1",
            keyUrl = "https://build.nvidia.com",
            steps = "1. Open build.nvidia.com and sign in / create a free NVIDIA account (phone verification may be asked).\n2. Open any model page and tap \"Get API Key\" (or \"Build with this NIM\").\n3. Tap \"Generate Key\", copy it (starts with nvapi-) and paste it above."),
        Prov("github", "GitHub Models", "https://models.github.ai/inference", listUrl = "https://models.github.ai/catalog/models",
            keyUrl = "https://github.com/settings/personal-access-tokens/new",
            steps = "1. Sign in to GitHub and open Settings > Developer settings > Personal access tokens > Fine-grained tokens > Generate new token.\n2. Give it a name and, under Permissions, add \"Models: Read\".\n3. Generate, copy the token (starts with github_pat_) and paste it above."),
        Prov("hf", "HuggingFace", "https://router.huggingface.co/v1",
            keyUrl = "https://huggingface.co/settings/tokens",
            steps = "1. Open huggingface.co/settings/tokens and sign in.\n2. Tap \"Create new token\" > type \"Fine-grained\" and enable \"Make calls to Inference Providers\".\n3. Create, copy the token (starts with hf_) and paste it above.")
    )
    private val BAD = listOf("embed", "whisper", "tts", "guard", "moderation", "rerank", "image", "audio", "vision", "ocr",
        "speech", "transcribe", "imagen", "veo", "aqa", "robotics", "live", "diffusion", "flux", "stable", "clip", "safety")

    private val http = OkHttpClient.Builder().callTimeout(240, TimeUnit.SECONDS).readTimeout(240, TimeUnit.SECONDS).build()
    private val cool = HashMap<String, Long>()
    private val coolProv = HashMap<String, Long>()

    // ---------- model discovery ----------
    private fun score(id: String): Int {
        val l = id.lowercase()
        val b = Regex("(\\d+)b").findAll(l).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0
        return when { l.contains("pro") -> 200; l.contains("flash") && !l.contains("lite") -> 120; else -> b }
    }

    private fun discover(p: Prov, key: String): List<String> {
        val rb = Request.Builder().url(p.listUrl ?: (p.base + "/models"))
        if (p.kind == "gemini") rb.header("x-goog-api-key", key) else rb.header("Authorization", "Bearer $key")
        http.newCall(rb.build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw Exception("${r.code}")
            val ids: List<String> = if (p.kind == "gemini") {
                val a = JSONObject(s).getJSONArray("models")
                (0 until a.length()).map { a.getJSONObject(it) }
                    .filter { it.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") == true }
                    .map { it.getString("name").removePrefix("models/") }
            } else {
                val a = if (s.trim().startsWith("[")) JSONArray(s) else JSONObject(s).getJSONArray("data")
                (0 until a.length()).map { a.getJSONObject(it) }
                    .filter { m ->
                        p.id != "openrouter" || (m.optJSONObject("pricing")?.let { it.optString("prompt") == "0" && it.optString("completion") == "0" } ?: false)
                    }.map { it.getString("id") }
            }
            return ids.filter { id -> BAD.none { id.contains(it, true) } }
        }
    }

    suspend fun models(ctx: Context, p: Prov, key: String, force: Boolean = false): List<String> = withContext(Dispatchers.IO) {
        val pr = ctx.getSharedPreferences("models", 0)
        fun cached(): List<String> = pr.getString("m_${p.id}", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } } ?: emptyList()
        val fresh = System.currentTimeMillis() - pr.getLong("ts_${p.id}", 0) < 86_400_000
        if (!force && fresh && pr.contains("m_${p.id}")) return@withContext cached()
        try {
            val l = discover(p, key).distinct().sortedByDescending { score(it) }
            pr.edit().putString("m_${p.id}", JSONArray(l).toString()).putLong("ts_${p.id}", System.currentTimeMillis()).apply(); l
        } catch (e: Exception) { cached() }
    }

    private fun keyOf(ctx: Context, id: String) = Prefs.keys(ctx).getString(id, "").orEmpty()
    fun hasKeys(ctx: Context): Boolean = provs.any { keyOf(ctx, it.id).isNotBlank() } ||
        (keyOf(ctx, "custom_key").isNotBlank() && keyOf(ctx, "custom_url").isNotBlank() && keyOf(ctx, "custom_model").isNotBlank())
    fun cachedCount(ctx: Context, p: Prov): Int =
        ctx.getSharedPreferences("models", 0).getString("m_${p.id}", null)?.let { JSONArray(it).length() } ?: 0
    fun totalCached(ctx: Context): Int =
        provs.filter { keyOf(ctx, it.id).isNotBlank() }.sumOf { cachedCount(ctx, it) } +
            (if (keyOf(ctx, "custom_key").isNotBlank() && keyOf(ctx, "custom_model").isNotBlank()) 1 else 0)
    suspend fun refresh(ctx: Context, p: Prov, key: String): Int = models(ctx, p, key, true).size

    private suspend fun entries(ctx: Context): List<Entry> {
        val out = ArrayList<Entry>()
        for (p in provs) {
            val k = keyOf(ctx, p.id)
            if (k.isNotBlank()) models(ctx, p, k).forEach { out.add(Entry(p, k, it)) }
        }
        val cu = keyOf(ctx, "custom_url"); val ck = keyOf(ctx, "custom_key"); val cm = keyOf(ctx, "custom_model")
        if (cu.isNotBlank() && ck.isNotBlank() && cm.isNotBlank()) out.add(Entry(Prov("custom", "Custom", cu.trimEnd('/')), ck, cm))
        return out
    }

    // ---------- calls ----------
    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val rb = Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType()))
        headers.forEach { (k, v) -> rb.header(k, v) }
        http.newCall(rb.build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            when {
                r.code == 429 -> throw RateLimit()
                r.code in listOf(400, 401, 403, 404, 422) -> throw BadModel()
                !r.isSuccessful -> throw Exception("${r.code}: ${s.take(150)}")
            }
            return JSONObject(s)
        }
    }

    private fun call(e: Entry, p: String): String {
        if (e.p.kind == "gemini") {
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", p)))))
            val j = post("${e.p.base}/models/${e.model}:generateContent", mapOf("x-goog-api-key" to e.key), body)
            return j.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
        val body = JSONObject().put("model", e.model).put("max_tokens", 6000)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", p)))
        val j = post(e.p.base + "/chat/completions", mapOf("Authorization" to "Bearer ${e.key}"), body)
        val m = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        if (m.isNull("content")) throw Exception("empty")
        return m.getString("content")
    }

    suspend fun ask(ctx: Context, userPrompt: String): String {
        val prompt = ContentFilter.SAFETY + userPrompt
        val list = entries(ctx)
        if (list.isEmpty()) throw NoKeys()
        repeat(4) {
            for (e in list) {
                val t = System.currentTimeMillis()
                if ((cool[e.id] ?: 0L) > t || (coolProv[e.p.id] ?: 0L) > t) continue
                try {
                    val r = withContext(Dispatchers.IO) { call(e, prompt) }
                        .replace(Regex("(?s)<think>.*?</think>"), "").trim()
                    if (r.contains(ContentFilter.MARK)) throw ContentBlocked()
                    if (r.isNotBlank()) return r
                } catch (x: RateLimit) {
                    cool[e.id] = t + 120_000; coolProv[e.p.id] = t + 10_000
                } catch (x: BadModel) {
                    cool[e.id] = t + 6 * 3_600_000L
                } catch (x: ContentBlocked) {
                    throw x
                } catch (x: CancellationException) {
                    throw x
                } catch (x: Exception) {
                    cool[e.id] = t + 30_000
                }
            }
            delay(20_000)
        }
        throw Exception("All models are busy or out of limit")
    }
}
