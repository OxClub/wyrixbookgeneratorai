package com.wyrix.bookgenerator

import android.content.Context
import org.json.JSONObject

object Engine {
    private const val PER = 5      // sections per chapter
    private const val WORDS = 800  // words per section

    suspend fun run(ctx: Context, id: Long, progress: suspend (String) -> Unit) {
        val dao = Db.get(ctx).dao()
        var b = dao.book(id) ?: return
        val main = if (b.lang == "en") "English" else "Hindi (pure Devanagari script)"

        if (dao.totalNow(id) == 0) {
            progress("Planning the book...")
            val n = (b.pages + 12) / 13
            val out = Providers.ask(ctx, """You are a professional book planner. Book request: "${b.prompt}"
Language: $main. Target length: ${b.pages} pages.
Return ONLY valid JSON, no markdown: {"title":"...","bible":"detailed plan: genre/type, tone, main characters or key concepts, setting, rules, and the ending","chapters":[{"title":"...","summary":"3-4 sentences"}]}
Write exactly $n chapters. Titles and text must be in $main.""")
            val j = JSONObject(out.substring(out.indexOf('{'), out.lastIndexOf('}') + 1))
            val ch = j.getJSONArray("chapters")
            val list = ArrayList<Section>()
            for (c in 0 until ch.length()) {
                val o = ch.getJSONObject(c)
                for (p in 1..PER) list.add(Section(bookId = id, idx = c * PER + p, chIdx = c, chTitle = o.getString("title"), chSummary = o.optString("summary"), part = p))
            }
            dao.addSections(list)
            dao.saveBook(b.copy(title = j.getString("title"), bible = j.optString("bible"), status = "RUNNING"))
        }

        val total = dao.totalNow(id)
        while (true) {
            val s = dao.next(id) ?: break
            b = dao.book(id)!!
            progress("${s.idx}/$total • ${s.chTitle}")
            var cur = s
            if (cur.text.isBlank()) {
                val tail = dao.at(id, cur.idx - 1)?.text?.takeLast(700).orEmpty()
                val txt = Providers.ask(ctx, """Book: "${b.title}". Language: $main.
BOOK PLAN: ${b.bible}
STORY SO FAR: ${b.summary.ifBlank { "(just begun)" }}
CURRENT CHAPTER ${cur.chIdx + 1}: ${cur.chTitle} - ${cur.chSummary}
Write part ${cur.part} of $PER of this chapter, about $WORDS words, in $main. Continue seamlessly from the previous text. Do not repeat earlier events, do not summarize, no headings, notes or commentary - output only the book text.
PREVIOUS TEXT ENDS: $tail""")
                cur = cur.copy(text = txt); dao.saveSection(cur)
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
