package com.nexus.music.data.stream

/**
 * Распознавание ссылок на ПЛЕЙЛИСТ/АЛЬБОМ на разных площадках. Чистая логика без Android —
 * покрыта юнит-тестами (`PlaylistLinksTest`). Всё, что здесь не распознано, идёт прежним путём
 * (один трек).
 */
object PlaylistLinks {

    // PERF (OPT-25): Regex компилируются один раз, а не на каждый вызов. Шаблоны и флаги — прежние.
    private val RE_YT_LIST = Regex("[?&]list=([A-Za-z0-9_-]+)")
    private val RE_VK_MUSIC = Regex("music/(?:playlist|album)/(-?\\d+)_(\\d+)(?:_([A-Za-z0-9]+))?")
    private val RE_VK_AUDIO_PLAYLIST = Regex("audio_playlist(-?\\d+)_(\\d+)(?:(?:/|%2F)([A-Za-z0-9]+))?")
    private val RE_VK_ACCESS_HASH = Regex("access_hash=([A-Za-z0-9]+)")
    private val RE_YA_TRACK = Regex("/track/\\d+")
    private val RE_YA_USER_PL = Regex("users/([^/?#]+)/playlists/(\\d+)")
    private val RE_YA_UUID = Regex("/playlists/([A-Za-z0-9.\\-]{8,})")
    private val RE_YA_ALBUM = Regex("/album/(\\d+)")
    private val RE_SPOTIFY_PL = Regex("(?:open|play|embed)\\.spotify\\.com/(?:embed/)?(?:intl-[a-z]+/)?(?:user/[^/?#]+/)?(playlist|album)/([A-Za-z0-9]+)")
    private val RE_SPOTIFY_URI = Regex("^spotify:(playlist|album|track):([A-Za-z0-9]+)$")
    private val RE_ANY_URL = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
    private val RE_SC_MOBILE = Regex("^(https?://)(?:m|mobile)\\.soundcloud\\.com", RegexOption.IGNORE_CASE)
    private val RE_SC_SET = Regex("soundcloud\\.com/[^/?#]+/(?:sets|albums)/[^/?#]+", RegexOption.IGNORE_CASE)

    // ── YouTube ─────────────────────────────────────────────────────────────────────────────
    /** id списка из `?list=` / `&list=`. Автомиксы YouTube (`RD…`) — не настоящие плейлисты
     *  (бесконечные, зависят от просмотра), их не трогаем: остаётся обычный разбор одного видео. */
    fun youtubeListId(url: String): String? {
        val id = RE_YT_LIST.find(url)?.groupValues?.get(1) ?: return null
        if (id.startsWith("RD")) return null
        return id
    }

    fun youtubePlaylistUrl(listId: String) = "https://www.youtube.com/playlist?list=$listId"

    // ── VK ──────────────────────────────────────────────────────────────────────────────────
    data class VkPlaylistRef(val ownerId: Long, val playlistId: Long, val accessKey: String?)

    /** Форматы: `vk.com/music/playlist/-2000_12_hash`, `vk.com/music/album/…`,
     *  `vk.com/audios1?z=audio_playlist1_12/hash` (в т.ч. `%2F`), `…audio_playlist-1_12&access_hash=hash`. */
    fun vkPlaylist(url: String): VkPlaylistRef? {
        RE_VK_MUSIC.find(url)?.let {
            return VkPlaylistRef(it.groupValues[1].toLong(), it.groupValues[2].toLong(), it.groupValues[3].ifBlank { null })
        }
        RE_VK_AUDIO_PLAYLIST.find(url)?.let {
            val key = it.groupValues[3].ifBlank { null }
                ?: RE_VK_ACCESS_HASH.find(url)?.groupValues?.get(1)
            return VkPlaylistRef(it.groupValues[1].toLong(), it.groupValues[2].toLong(), key)
        }
        return null
    }

    // ── Яндекс Музыка ───────────────────────────────────────────────────────────────────────
    sealed class YandexRef {
        data class User(val login: String, val kind: String) : YandexRef()
        data class Album(val id: String) : YandexRef()
        data class Uuid(val uuid: String) : YandexRef()
    }

    /** Ссылка на конкретный трек (`…/track/123`) плейлистом не считается, даже если в ней есть album. */
    fun yandexPlaylist(url: String): YandexRef? {
        if (RE_YA_TRACK.containsMatchIn(url)) return null
        RE_YA_USER_PL.find(url)?.let {
            return YandexRef.User(it.groupValues[1], it.groupValues[2])
        }
        RE_YA_UUID.find(url)?.let { return YandexRef.Uuid(it.groupValues[1]) }
        RE_YA_ALBUM.find(url)?.let { return YandexRef.Album(it.groupValues[1]) }
        return null
    }

    // ── Spotify ─────────────────────────────────────────────────────────────────────────────
    /** kind = "playlist" | "album", id. */
    fun spotifyPlaylist(url: String): Pair<String, String>? {
        val m = RE_SPOTIFY_PL.find(url) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    // ── Подготовка ссылки перед разбором ────────────────────────────────────────────────────
    /**
     * Достаёт ссылку из вставленного текста: «Послушай это https://on.soundcloud.com/abc» → сама ссылка;
     * `spotify:playlist:ID` → `https://open.spotify.com/playlist/ID`. Если ссылки в тексте нет — возвращает
     * исходную строку (так работает и поиск по названию).
     */
    fun extractUrl(text: String): String {
        val t = text.trim()
        RE_SPOTIFY_URI.find(t)?.let {
            return "https://open.spotify.com/${it.groupValues[1]}/${it.groupValues[2]}"
        }
        val found = RE_ANY_URL.find(t)?.value ?: return t
        return found.trimEnd(')', ']', '}', '.', ',', ';', '!', '?', '»')
    }

    /** Служебные параметры из «Поделиться» (si, utm_*, fbclid…), которые не нужны для разбора, но ломают
     *  точное сравнение и иногда сам запрос (SoundCloud resolve). Для YouTube `list` и `v` остаются. */
    private val TRACKING = Regex("^(si|utm_[a-z]+|fbclid|gclid|feature|ref|ref_|context|app|dt|ts_url)$", RegexOption.IGNORE_CASE)

    /** Мобильные адреса → обычные, `m.soundcloud.com` → `soundcloud.com`, лишние параметры убираются.
     *  Ключ доступа VK (`access_hash`) и всё остальное не трогается. */
    fun normalize(url: String): String {
        if (!url.startsWith("http", ignoreCase = true)) return url
        var u = url.trim()
        u = u.replace(RE_SC_MOBILE, "$1soundcloud.com")
        val q = u.indexOf('?')
        if (q < 0) return u
        val hash = u.indexOf('#').let { if (it in (q + 1)..Int.MAX_VALUE) it else -1 }
        val frag = if (hash >= 0) u.substring(hash) else ""
        val base = u.substring(0, q)
        val query = u.substring(q + 1, if (hash >= 0) hash else u.length)
        val kept = query.split('&').filter { it.isNotEmpty() && !TRACKING.matches(it.substringBefore('=')) }
        // VK кладёт ссылку на плейлист в параметр z — он обязан остаться.
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + frag
    }

    /** Ссылка на плейлист/альбом/сет SoundCloud: `/sets/` или `/albums/` в пути. */
    fun isSoundCloudSet(url: String): Boolean =
        RE_SC_SET.containsMatchIn(url)

    // ── Сопоставление скачанных треков с библиотекой ─────────────────────────────────────────
    /** Ключ «название|исполнитель» без регистра, пробелов и знаков препинания — для поиска уже
     *  скачанного трека и для привязки файла к плейлисту, если MediaStore не вернул id. */
    fun matchKey(title: String, artist: String): String {
        fun n(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        return n(title) + "|" + n(artist)
    }
}
