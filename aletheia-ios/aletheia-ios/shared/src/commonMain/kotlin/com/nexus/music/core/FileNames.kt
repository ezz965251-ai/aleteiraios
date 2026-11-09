package com.nexus.music.core

/** Имена скачанных файлов: шаблон из Настроек → Загрузки и очистка от запрещённых символов. */
object FileNames {
    private val forbidden = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")
    private val spaces = Regex("\\s+")

    /** Запрещённые в файловых системах символы заменяем, буквы любых алфавитов остаются. */
    fun sanitize(base: String): String = base.replace(forbidden, "_").trim().ifBlank { "track" }

    /**
     * Подпапка для скачанного файла по шаблону из Настроек → Загрузки ({artist}, {album}; разделитель «/»).
     * Каждая часть очищается как имя файла, пустые части и «.»/«..» отбрасываются, уровней не больше двух —
     * выйти за пределы Music/NexusMusic шаблон не может. Пустой шаблон или пустой результат → "".
     */
    fun renderFolder(template: String, artist: String, album: String): String =
        template.replace('\\', '/').split('/')
            .map { part ->
                part.replace("{artist}", artist).replace("{album}", album)
                    .replace("{title}", "").replace(forbidden, "_").replace(spaces, " ").trim().trim('.', ' ')
            }
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .take(2)
            .joinToString("/") { it.take(80).trim().trim('.') }
            .let { if (it.contains("//")) "" else it }

    /** {artist}/{title}/{album} → имя; пустые части не оставляют висящих разделителей. */
    fun render(template: String, artist: String, title: String, album: String): String =
        template.ifBlank { "{title}" }
            .replace("{artist}", artist).replace("{title}", title).replace("{album}", album)
            .replace(spaces, " ").trim().trim('-', '—', ' ').ifBlank { title }
}
