package com.nexus.music.core

/**
 * Внимательный разбор «Авторы - Название».
 *
 * Раньше код делил строку по первому « - » и брал левую часть как автора только когда автор был
 * пустым. Из-за этого у коллабов («Artist1, Artist2 - Title», «A x B - Title», «01. A & B - Title»)
 * в название попадали авторы или номер трека, а на обложке-заглушке рисовалась первая буква/цифра
 * не названия, а автора или «0» от «01».
 *
 * Правила:
 *  1. Номер трека в начале («01.», «01 -», «1)», «(01)», «01 Title») отбрасывается. «99 Problems» и
 *     «1-800» остаются: номер должен быть отделён разделителем и пробелом.
 *  2. Левая часть делится на отдельных авторов (запятая, &, x, feat., ft., и, vs, with…) и
 *     сравнивается с полем «автор»:
 *       • слева те же авторы (или часть их) → слева авторы, справа название;
 *       • слева все из поля «автор» и ещё кто-то → слева полный список авторов (коллаб), поле
 *         «автор» дополняется до полного;
 *       • справа автор, слева название («Title - Artist») → название слева;
 *       • иначе строка не трогается (кроме номера трека): «Song - Live at…» остаётся названием.
 *  3. Если поле «автор» пустое — слева автор(ы), справа название (как раньше).
 */
object ArtistTitle {
    data class Parts(val artist: String?, val title: String)

    private val SEPARATORS = listOf(" - ", " – ", " — ", " ‒ ", " ― ")

    // «01. », «01 - », «1) », «(01) », «001 – ». Без пробела после разделителя не срабатывает («1-800», «2.Pac»).
    // PERF: split(Regex("\\s+")) в looksLikeNames собирал Pattern на каждый вызов разбора названия.
    private val NAME_WS = Regex("\\s+")
    private val TRACK_NO = Regex("""^\s*(?:\(\d{1,3}\)|\d{1,3}\s*[.\-–—)]+)\s+(?=\S)""")
    // «01 Title» — только двузначный номер с ведущим нулём, иначе «21 Guns», «7 rings» были бы испорчены.
    private val TRACK_NO_ZERO = Regex("""^\s*0\d\s+(?=\p{L})""")

    private val ARTIST_SPLIT = Regex(
        "(?i)\\s*(?:,|;|/|&|\\+|(?<![\\p{L}\\p{N}])(?:and|и|x|х|vs\\.?|feat\\.?|ft\\.?|with|при\\s+участии)(?![\\p{L}\\p{N}]))\\s*"
    )

    private val PLACEHOLDERS = setOf("unknown", "unknown artist", "<unknown>", "неизвестен", "неизвестный исполнитель")

    fun stripTrackNumber(s: String): String {
        var r = s.trim()
        val m = TRACK_NO.find(r) ?: TRACK_NO_ZERO.find(r)
        if (m != null) r = r.substring(m.range.last + 1).trim()
        return r.ifEmpty { s.trim() }
    }

    /** Делит по первому разделителю « - » / « – » / « — ». Левая часть длиннее [maxLeft] не считается автором. */
    fun split(raw: String, maxLeft: Int = 60): Pair<String, String>? {
        var best: Int = -1
        var bestLen = 0
        for (sep in SEPARATORS) {
            val i = raw.indexOf(sep)
            if (i > 0 && (best == -1 || i < best)) { best = i; bestLen = sep.length }
        }
        if (best <= 0 || best >= raw.length - bestLen) return null
        val l = raw.substring(0, best).trim()
        val r = raw.substring(best + bestLen).trim()
        if (l.isEmpty() || r.isEmpty()) return null
        // Длинный список авторов («A, B, C, D & E») допустим: лимит растёт, если слева несколько авторов.
        val limit = if (artistKeys(l).size >= 2) maxOf(maxLeft, 120) else maxLeft
        if (l.length >= limit) return null
        return l to r
    }

    private fun norm(s: String): String =
        s.lowercase().replace('ё', 'е').filter { it.isLetterOrDigit() }

    /** Нормализованные имена отдельных авторов строки. */
    fun artistKeys(raw: String): List<String> {
        val parts = ARTIST_SPLIT.split(raw).map { norm(it) }.filter { it.isNotEmpty() }.distinct()
        // Имя целиком из «разделителя» («X», «Ft.», «&») — это сам автор, а не пустой список.
        return parts.ifEmpty { listOf(norm(raw)).filter { it.isNotEmpty() } }
    }

    private fun same(a: String, b: String): Boolean {
        if (a == b) return true
        val (s, l) = if (a.length <= b.length) a to b else b to a
        return s.length >= 4 && l.contains(s)
    }

    private fun isSubset(small: List<String>, big: List<String>): Boolean =
        small.isNotEmpty() && small.all { k -> big.any { same(it, k) } }

    // Слова, по которым поле «автор» похоже на имя канала/загрузчика, а не на исполнителя.
    private val CHANNEL_WORDS = Regex(
        "(?i)(?:^|[\\s._-])(records?|recordings?|music|official|vevo|topic|channel|label|entertainment|" +
            "releases?|network|lyrics?|hd|tv|radio|media|studio|promo|premiere|exclusive|bass|trap|house|" +
            "музыка|музло|лейбл|канал|новинки|хиты|official\\s*audio)(?=$|[\\s._-])"
    )

    // «Хвосты» названия: если справа такое слово, то слева, скорее всего, название, а не автор.
    private val VERSION_START = Regex(
        "(?i)^(?:\\(|\\[)?\\s*(live|remix|remaster(?:ed)?|ost|instrumental|version|edit|mix|acoustic|cover|" +
            "demo|radio|extended|slowed|sped|reverb|part|pt\\.?|vol\\.?|ver\\.?|lyrics?|official|audio|video|" +
            "ремикс|версия|вариант|часть|кавер|живой|караоке)\\b"
    )

    /** Поле «автор» похоже на имя канала/загрузчика (а не на исполнителя) — ему нельзя верить при разборе. */
    fun looksLikeUploader(artist: String?): Boolean {
        val a = artist?.trim().orEmpty()
        if (a.isEmpty() || a.lowercase() in PLACEHOLDERS) return true
        if (CHANNEL_WORDS.containsMatchIn(a)) return true
        if (a.contains("http", ignoreCase = true) || a.contains(".com", ignoreCase = true) || a.contains(".ru", ignoreCase = true)) return true
        if (a.startsWith("@")) return true
        return false
    }

    /** Каждая часть слева — похоже на имя (до 4 слов, не версия, не голые цифры). */
    private fun looksLikeNames(left: String): Boolean {
        val parts = ARTIST_SPLIT.split(left).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false
        return parts.all { p ->
            p.split(NAME_WS).size <= 4 && p.any { it.isLetter() } && !VERSION_START.containsMatchIn(p)
        }
    }

    fun resolve(rawTitle: String, rawArtist: String?): Parts {
        val t0 = stripTrackNumber(rawTitle)
        val a0 = rawArtist?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in PLACEHOLDERS }
        val sp = split(t0) ?: return Parts(a0, t0)
        val (left, right) = sp
        if (a0 == null) return Parts(left, right)

        val aKeys = artistKeys(a0)
        val lKeys = artistKeys(left)
        val rKeys = artistKeys(right)

        val leftInArtist = isSubset(lKeys, aKeys)       // «A - Title» при авторе «A, B»
        val artistInLeft = isSubset(aKeys, lKeys)       // «A, B - Title» при авторе «A»
        if (leftInArtist || artistInLeft) {
            val fullArtist = if (artistInLeft && lKeys.size > aKeys.size) left else a0
            return Parts(fullArtist, right)
        }
        // «Title - Artist»: справа именно автор.
        if (isSubset(rKeys, aKeys) && !isSubset(lKeys, aKeys)) return Parts(a0, left)

        // Поле «автор» не совпало ни с левой, ни с правой частью. Раньше строка оставалась как есть,
        // и авторы коллаба попадали в название. Теперь решаем по признакам:
        val rightIsVersion = VERSION_START.containsMatchIn(right)
        // а) поле «автор» — имя канала/загрузчика: слева авторы, если это похоже на имена;
        if (looksLikeUploader(a0) && !rightIsVersion && looksLikeNames(left)) return Parts(left, right)
        // б) слева явный коллаб («A, B», «A x B», «A feat. B»), справа не версия — это авторы,
        //    а поле «автор» (чужое, например канал) заменяется списком слева.
        if (lKeys.size >= 2 && !rightIsVersion && looksLikeNames(left)) {
            return Parts(left, right)
        }
        return Parts(a0, t0)
    }

    /** Результат проверки перед скачиванием. */
    data class Verdict(val parts: Parts, val issues: List<String>, val fixed: Boolean)

    /**
     * Проверка перед скачиванием: разбирает название/авторов и убеждается, что итог чистый.
     * Признаки ошибки: в названии остался номер трека; название начинается с автора или с списка авторов;
     * название пустое или равно автору; название всё ещё «Авторы - Название» с коллабом слева.
     * При проблеме запускается строгий разбор (доверяем левой части, а не полю «автор»).
     */
    fun verify(rawTitle: String, rawArtist: String?): Verdict {
        var parts = resolve(rawTitle, rawArtist)
        val issues = mutableListOf<String>()
        fun check(p: Parts): List<String> {
            val out = mutableListOf<String>()
            val t = p.title
            if (t.isBlank()) out += "empty-title"
            if (TRACK_NO.containsMatchIn(t) || TRACK_NO_ZERO.containsMatchIn(t)) out += "track-number"
            val a = p.artist
            if (a != null && t.isNotBlank() && norm(a) == norm(t)) out += "title-equals-artist"
            val sp = split(t)
            if (sp != null) {
                val lk = artistKeys(sp.first)
                val ak = a?.let { artistKeys(it) }.orEmpty()
                if (ak.isNotEmpty() && isSubset(lk, ak)) out += "artist-in-title"
                else if (lk.size >= 2 && looksLikeNames(sp.first) && !VERSION_START.containsMatchIn(sp.second)) out += "collab-in-title"
            }
            return out
        }
        issues += check(parts)
        var fixed = false
        if (issues.isNotEmpty()) {
            // Строгий проход: убираем номер, а слева берём авторов, не оглядываясь на поле «автор».
            val t0 = stripTrackNumber(parts.title)
            val sp = split(t0)
            if (sp != null && (looksLikeNames(sp.first)) && !VERSION_START.containsMatchIn(sp.second)) {
                val newArtist = if (parts.artist != null && isSubset(artistKeys(parts.artist!!), artistKeys(sp.first))) sp.first
                                else if (parts.artist != null && !looksLikeUploader(parts.artist) && artistKeys(sp.first).size < 2) parts.artist
                                else sp.first
                parts = Parts(newArtist, sp.second)
            } else if (t0 != parts.title) {
                parts = Parts(parts.artist, t0)
            }
            fixed = true
            if (parts.title.isBlank()) parts = Parts(rawArtist?.trim(), stripTrackNumber(rawTitle)) // не теряем название совсем
        }
        return Verdict(parts, issues, fixed)
    }

    /** Буква/цифра для обложки-заглушки: первая у НАСТОЯЩЕГО названия, без номера трека и авторов. */
    fun coverLetter(title: String, artist: String?): String {
        val t = resolve(title, artist).title
        val ch = t.firstOrNull { it.isLetterOrDigit() } ?: title.firstOrNull { it.isLetterOrDigit() }
        return ch?.uppercaseChar()?.toString() ?: "♪"
    }
}
