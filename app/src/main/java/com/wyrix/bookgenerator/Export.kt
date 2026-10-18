package com.wyrix.bookgenerator

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.text.*
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Export {
    suspend fun run(ctx: Context, id: Long, pdf: Boolean): String = withContext(Dispatchers.IO) {
        val dao = Db.get(ctx).dao()
        val b = dao.book(id)!!
        val ss = dao.sections(id).filter { it.status == "DONE" }
        if (ss.isEmpty()) return@withContext "Abhi kuch likha nahi gaya"
        val safe = b.title.ifBlank { "book$id" }.replace(Regex("[^\\p{L}\\p{N}]+"), "_")
        val versions = when (b.lang) { "en" -> listOf(false to "en"); "hi" -> listOf(false to "hi"); else -> listOf(false to "hi", true to "en") }
        for ((second, l) in versions) {
            val ch = ss.groupBy { it.chIdx }.toSortedMap().map { (_, l2) ->
                l2.first().chTitle to l2.joinToString("\n\n") { if (second) it.text2 else it.text }
            }
            if (pdf) pdf(ctx, "${safe}_$l", b.title, ch) else epub(ctx, "${safe}_$l", b.title, l, ch)
        }
        "Downloads/WyrixBookGenerator me save ho gaya"
    }

    private fun save(ctx: Context, file: String, mime: String, write: (OutputStream) -> Unit) {
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file); put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WyrixBookGenerator")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
        ctx.contentResolver.openOutputStream(uri)!!.use(write)
    }

    // System font fallback Devanagari khud render karta hai, alag font embed nahi karna padta.
    private fun pdf(ctx: Context, name: String, title: String, ch: List<Pair<String, String>>) {
        val doc = PdfDocument(); val w = 595; val h = 842; val m = 50; var n = 0
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        fun pages(text: CharSequence) {
            val lay = StaticLayout.Builder.obtain(text, 0, text.length, tp, w - 2 * m).setLineSpacing(4f, 1f).build()
            var line = 0
            while (line < lay.lineCount) {
                val top = lay.getLineTop(line); var end = line
                while (end < lay.lineCount && lay.getLineBottom(end) - top <= h - 2 * m) end++
                if (end == line) end = line + 1
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, ++n).create())
                val c = page.canvas
                c.save(); c.translate(m.toFloat(), (m - top).toFloat())
                c.clipRect(0f, top.toFloat(), (w - 2 * m).toFloat(), lay.getLineBottom(end - 1).toFloat())
                lay.draw(c); c.restore(); doc.finishPage(page); line = end
            }
        }
        fun styled(t: String, size: Float, tail: String) = SpannableStringBuilder(t).apply {
            setSpan(RelativeSizeSpan(size), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); append(tail)
        }
        pages(styled(title, 2f, ""))
        for ((t, b) in ch) pages(styled(t, 1.6f, "\n\n$b"))
        save(ctx, "$name.pdf", "application/pdf") { doc.writeTo(it) }; doc.close()
    }

    private fun epub(ctx: Context, name: String, title: String, lang: String, ch: List<Pair<String, String>>) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val ns = """xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops""""
        save(ctx, "$name.epub", "application/epub+zip") { out ->
            ZipOutputStream(out).use { z ->
                fun put(path: String, s: String, stored: Boolean = false) {
                    val bytes = s.toByteArray(); val e = ZipEntry(path)
                    if (stored) { e.method = ZipEntry.STORED; e.size = bytes.size.toLong(); e.compressedSize = bytes.size.toLong(); e.crc = CRC32().apply { update(bytes) }.value }
                    z.putNextEntry(e); z.write(bytes); z.closeEntry()
                }
                put("mimetype", "application/epub+zip", true)
                put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
                val items = ch.indices.joinToString("") { """<item id="c$it" href="c$it.xhtml" media-type="application/xhtml+xml"/>""" }
                val spine = ch.indices.joinToString("") { """<itemref idref="c$it"/>""" }
                val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
                put("OEBPS/content.opf", """<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">bookgen-${System.currentTimeMillis()}</dc:identifier><dc:title>${esc(title)}</dc:title><dc:language>$lang</dc:language><meta property="dcterms:modified">$now</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$items</manifest><spine>$spine</spine></package>""")
                val toc = ch.mapIndexed { i, p -> """<li><a href="c$i.xhtml">${esc(p.first)}</a></li>""" }.joinToString("")
                put("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="utf-8"?><html $ns><head><title>${esc(title)}</title></head><body><nav epub:type="toc"><ol>$toc</ol></nav></body></html>""")
                ch.forEachIndexed { i, (t, b) ->
                    val body = b.split("\n").filter { it.isNotBlank() }.joinToString("") { "<p>${esc(it)}</p>" }
                    put("OEBPS/c$i.xhtml", """<?xml version="1.0" encoding="utf-8"?><html $ns><head><title>${esc(t)}</title></head><body><h2>${esc(t)}</h2>$body</body></html>""")
                }
            }
        }
    }
}
