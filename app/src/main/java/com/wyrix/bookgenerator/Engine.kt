package com.wyrix.bookgenerator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Engine {
    private fun json(s: String) = JSONObject(s.substring(s.indexOf('{'), s.lastIndexOf('}') + 1))

    /** Generated text me explicit cheez mile to ek baar saaf karwao, phir bhi mile to rok do. */
    private suspend fun clean(ctx: Context, t: String): String {
        if (!ContentFilter.explicit(t)) return t
        val r = Providers.ask(ctx, "Rewrite the following text with all sexual or explicit content removed. Keep the same language, story and length. Output only the text:\n\n$t")
        if (ContentFilter.explicit(r)) throw ContentBlocked()
        return r
    }

    private const val PER = 5      // sections per chapter
    private const val WORDS = 800  // words per section

    suspend fun run(ctx: Context, id: Long, progress: suspend (String) -> Unit) {
        val dao = Db.get(ctx).dao()
        var b = dao.book(id) ?: return
        if (ContentFilter.blocked(b.prompt)) throw ContentBlocked()
        val main = if (b.lang == "en") "English" else "Hindi (pure Devanagari script)"

        if (dao.totalNow(id) == 0) {
            progress("Planning the book...")
            val n = (b.pages + 12) / 13
            val first = minOf(n, 20)
            val out = Providers.ask(ctx, """You are a professional book planner. Book request: "${b.prompt}"
Type/genre: ${b.genre}. Language: $main. Target length: ${b.pages} pages ($n chapters in total).
Return ONLY valid JSON, no markdown: {"title":"...","bible":"detailed plan: genre/type, tone, main characters or key concepts, setting, rules, and the ending","chapters":[{"title":"...","summary":"3-4 sentences"}]}
In "chapters" write exactly $first chapters: the first $first of the $n chapters of the whole book. Titles and text must be in $main.""")
            val j = json(out)
            val bookTitle = j.getString("title")
            val bible = j.optString("bible")
            val titles = ArrayList<Pair<String, String>>()
            fun add(a: JSONArray) {
                for (c in 0 until a.length()) { val o = a.getJSONObject(c); titles.add(o.getString("title") to o.optString("summary")) }
            }
            add(j.getJSONArray("chapters"))
            var tries = 0
            while (titles.size < n && tries < 12) {
                tries++
                progress("Planning chapters ${titles.size}/$n...")
                val need = minOf(20, n - titles.size)
                val prev = titles.joinToString("\n") { it.first }
                val o2 = Providers.ask(ctx, """Continue planning the book "$bookTitle" ($main).
BOOK PLAN: $bible
CHAPTERS SO FAR (titles only):
$prev
Return ONLY valid JSON, no markdown: {"chapters":[{"title":"...","summary":"3-4 sentences"}]}
Write exactly $need NEW chapters, chapter ${titles.size + 1} to ${titles.size + need} of $n, continuing toward the planned ending. Do not repeat earlier chapters. Titles and text must be in $main.""")
                val a = json(o2).getJSONArray("chapters")
                if (a.length() == 0) break
                add(a)
            }
            val list = ArrayList<Section>()
            titles.forEachIndexed { c, (t, sm) ->
                for (p in 1..PER) list.add(Section(bookId = id, idx = c * PER + p, chIdx = c, chTitle = t, chSummary = sm, part = p))
            }
            dao.addSections(list)
            dao.saveBook(b.copy(title = bookTitle, bible = bible, status = "RUNNING"))
        }

        dao.setStatus(id, "RUNNING")
        val total = dao.totalNow(id)
        while (true) {
            val s = dao.next(id) ?: break
            b = dao.book(id)!!
            progress("${s.idx}/$total • ${s.chTitle}")
            var cur = s
            if (cur.text.isBlank()) {
                val tail = dao.at(id, cur.idx - 1)?.text?.takeLast(700).orEmpty()
                val txt = Providers.ask(ctx, """Book: "${b.title}" (${b.genre}). Language: $main.
BOOK PLAN: ${b.bible}
STORY SO FAR: ${b.summary.ifBlank { "(just begun)" }}
CURRENT CHAPTER ${cur.chIdx + 1}: ${cur.chTitle} - ${cur.chSummary}
Write part ${cur.part} of $PER of this chapter, about $WORDS words, in $main. Continue seamlessly from the previous text. Do not repeat earlier events, do not summarize, no headings, notes or commentary - output only the book text.
PREVIOUS TEXT ENDS: $tail""")
                cur = cur.copy(text = clean(ctx, txt)); dao.saveSection(cur)
            }
            if (b.lang == "both" && cur.text2.isBlank()) {
                cur = cur.copy(text2 = Providers.ask(ctx, "Translate into natural English. Output only the translation:\n\n${cur.text}"))
                dao.saveSection(cur)
            }
            if (cur.part == PER) {
                val chText = dao.chapter(id, cur.chIdx).joinToString("\n") { it.text }.takeLast(20000)
                val sm = Providers.ask(ctx, "Previous summary: ${b.summary}\n\nNew chapter text: $chText\n\nWrite an updated summary of the whole book so far in max 300 words (English): key events, character states, open threads. Output only the summary.")
                dao.saveBook(b.copy(summary = sm))
            }
            dao.saveSection(cur.copy(status = "DONE"))
        }
        dao.saveBook(dao.book(id)!!.copy(status = "DONE"))
    }
}
