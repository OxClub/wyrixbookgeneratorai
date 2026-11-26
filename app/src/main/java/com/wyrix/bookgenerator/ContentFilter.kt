package com.wyrix.bookgenerator

class ContentBlocked : Exception("This content is not allowed")

/** Adult / unsafe content ko rokta hai (Play Store policy). Teen layer: prompt filter, AI safety rules, output scan. */
object ContentFilter {
    const val MARK = "BLOCKED_CONTENT"

    const val SAFETY = "SAFETY RULES (highest priority): Never write sexual or erotic content, nudity, sexual violence, anything sexual involving minors, " +
        "graphic gore, hate speech, or instructions for crimes, weapons or self-harm. Romance must stay clean and non-explicit. " +
        "If the task asks for any of that, output exactly $MARK and nothing else. Otherwise do the task normally.\n\n"

    // English + Hinglish (Roman) - word-start match
    private val latin = listOf(
        "sex", "porn", "erotic", "nude", "naked", "xxx", "hentai", "nsfw", "fetish", "orgasm", "horny", "intercourse", "incest",
        "molest", "pedophil", "paedophil", "lolita", "bdsm", "rape", "masturbat", "blowjob", "handjob", "boobs", "pussy", "cumshot",
        "adult story", "adult content", "18+", "threesome", "orgy", 
        "chudai", "chodna", "=chut", "choot", "=lund", "=lauda", "randi", "bhosd", "gaand", "gand mar", "nangi", "kamuk", "balatkar",
        "sambhog", "antarvasna", "hawas", "hawaas", "suhagraat", "gandi kahani", "sexy", "mms scandal",
        "suicide method", "how to kill myself", "bomb making", "make a bomb", "child abuse"
    )
    // Devanagari - contains match
    private val deva = listOf(
        "सेक्स", "चुदाई", "चोदना", "चूत", "चुत", "लंड", "लौड़ा", "रंडी", "भोसड", "गांड", "नंगी", "नंगा", "कामुक", "अश्लील", "बलात्कार",
        "संभोग", "यौन", "हवस", "कामवासना", "सुहागरात", "पोर्न", "गंदी कहानी", "वयस्क कहानी", "अंतर्वासना", "स्तन", "उत्तेजक", "आत्महत्या का तरीका"
    )
    // Generated text ke liye chhoti, sirf explicit list (taaki normal kahaniyan galat na rukein)
    private val explicitLatin = listOf("porn", "xxx", "hentai", "blowjob", "handjob", "cumshot", "masturbat", "orgasm", "pussy", "boobs", "chudai", "=lund", "=chut", "bhosd", "randi")
    private val explicitDeva = listOf("चुदाई", "चूत", "लंड", "भोसड", "रंडी", "पोर्न", "संभोग", "नंगी")

    private fun norm(s: String): String =
        s.lowercase().map { when (it) { '0' -> 'o'; '1' -> 'i'; '3' -> 'e'; '4' -> 'a'; '5' -> 's'; '@' -> 'a'; '$' -> 's'; else -> it } }
            .joinToString("").replace(Regex("(.)\\1{2,}"), "$1$1").replace(Regex("[._\\-*]+"), " ")

    private fun hits(text: String, lat: List<String>, dv: List<String>): Boolean {
        val n = norm(text)
        if (dv.any { text.contains(it) }) return true
        return lat.any { t ->
            if (t == "18+") n.contains("18+") || n.contains("18 plus")
            else if (t.startsWith("=")) Regex("(^|[^a-z])" + Regex.escape(t.drop(1)) + "($|[^a-z])").containsMatchIn(n)
            else Regex("(^|[^a-z])" + Regex.escape(t)).containsMatchIn(n)
        }
    }

    /** User prompt check. true = block */
    fun blocked(prompt: String): Boolean = hits(prompt, latin, deva)

    /** Generated text check. true = explicit text mila */
    fun explicit(text: String): Boolean = hits(text, explicitLatin, explicitDeva)
}
