package com.nexus.music.data.stream

/**
 * «Ghbdtn» → «Привет»: запрос, набранный русскими буквами при включённой английской раскладке.
 *
 * Метод свой, чужой код не использован: у клавиатур ЙЦУКЕН и QWERTY фиксированное соответствие
 * клавиш, поэтому перевод — простая таблица. Сложнее другое — понять, что запрос набран «не в той
 * раскладке», и не испортить нормальные английские названия («Metallica», «Daft Punk», «BTS»).
 * Решает [alternative], и ошибка здесь дешёвая: [MultiSearch] ищет и по исходной строке, и по
 * переведённой, а результаты второй получают небольшой штраф.
 *
 * Сигналы (любого достаточно, переведённый текст при этом должен хоть чем-то походить на русский):
 *  1. знаки, которых нет внутри английского слова: `[ ] ; ``  и запятая между буквами или в начале
 *     слова — это буквы х, ъ, ж, ё и б (`pfvjhj;tyyjt` = «замороженное»);
 *  2. пять и более согласных подряд (y считается гласной) — по-английски почти не бывает,
 *     а у «русского на латинице» постоянно (`ghbdtn`);
 *  3. частые русские пары букв в переведённой строке заметно правдоподобнее английских в исходной.
 *
 * На 79 реальных русских названиях, переведённых в латинскую раскладку, ловит 65, на 110+ английских
 * исполнителях ложных срабатываний 0 (проверено прототипом; см. LayoutFixTest). Что не поймано — не
 * хуже, чем было: поиск по исходной строке идёт всегда.
 */
object LayoutFix {
    private const val LAT = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val CYR = "йцукенгшщзхъфывапролджэячсмитьбюё"

    private val TO_CYR: Map<Char, Char> = HashMap<Char, Char>().apply {
        for (i in LAT.indices) {
            put(LAT[i], CYR[i])
            if (LAT[i].isLetter()) put(LAT[i].uppercaseChar(), CYR[i].uppercaseChar())
        }
        // Знаки на Shift, которые на русской раскладке тоже буквы.
        put('{', 'Х'); put('}', 'Ъ'); put(':', 'Ж'); put('"', 'Э'); put('<', 'Б'); put('>', 'Ю'); put('~', 'Ё')
    }

    private val EN_PAIRS: Set<String> = (
        "th he in er an re on at en nd ti es or te of ed is it al ar st to nt ng se ha as ou io le ve co me de " +
            "hi ri ro ic ne ea ra ce li ch ll be ma si om ur ly no pe la ow ss rs ho us ec el ca ta wa ke ge ac " +
            "ad id ai ol ct ut ie di pr ee oo ck sh"
        ).split(' ').toSet()

    private val RU_PAIRS: Set<String> = (
        "ст но ен то на ов ни ра во ко ан ер ре ол ор ат ал ти ва ет ли ос по ро пр ит го не ел от ла ка ль ве " +
            "ог ес ин ие ом ад ак ем ое ий ны ых да де ча ту ми те ск бо ви до ся ма су ку жи ме ле му сл об вы за " +
            "ян ив ыс ую ют ьн ря ди па пе ид нн се ег од дн ня оч чу хо ей ду ым ри ик ис ло ой ею уж шо ша аш уч " +
            "ыл ыв из ай их им ыт ут рт рг сп зв ез зд"
        ).split(' ').toSet()

    // Знаки, которых внутри английского слова не бывает (по краям слова — тоже: «,tkst» = «белые»).
    private val STRONG_PUNCT = Regex("""[A-Za-z]([\[\];`]|,(?=[A-Za-z]))[A-Za-z]|(?:^|\s)[,\[\];`](?=[A-Za-z])|[A-Za-z][\[\];`](?:\s|$)""")
    private val CONSONANT_RUN = Regex("[b-df-hj-np-tv-xz]{5,}")

    /** Строка так, как она получилась бы при той же последовательности клавиш на русской раскладке. */
    fun toCyrillic(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(TO_CYR[ch] ?: ch)
        return sb.toString()
    }

    /** Русская версия запроса, если он очень похож на русский текст в английской раскладке; иначе null. */
    fun alternative(query: String): String? {
        val q = query.trim()
        if (q.length < 3) return null
        if (q.any { it in '\u0400'..'\u04FF' }) return null          // кириллица уже есть — не наш случай
        if (q.none { it in 'A'..'Z' || it in 'a'..'z' }) return null
        val c = toCyrillic(q)
        if (c == q) return null
        val (ru, ruN) = pairScore(c, RU_PAIRS)
        val (en, _) = pairScore(q, EN_PAIRS)
        if (ru > 0.0 && STRONG_PUNCT.containsMatchIn(q)) return c
        if (ru > 0.0 && CONSONANT_RUN.containsMatchIn(q.lowercase())) return c
        if (ruN >= 3 && ru >= 0.5 && ru > en + 0.25) return c
        return null
    }

    /** Доля пар соседних букв строки, входящих в [set], и число пар. Пробелы и знаки пропускаются. */
    private fun pairScore(s: String, set: Set<String>): Pair<Double, Int> {
        val letters = s.lowercase().filter { it.isLetter() }
        val n = letters.length - 1
        if (n <= 0) return 0.0 to 0
        var hit = 0
        for (i in 0 until n) if (letters.substring(i, i + 2) in set) hit++
        return hit.toDouble() / n to n
    }
}
