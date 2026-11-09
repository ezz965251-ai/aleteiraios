package com.nexus.music.ui

/**
 * TEST-07: константы `testTag` для UI-тестов (`androidTest`, `createAndroidComposeRule<MainActivity>()`).
 * Теги не влияют на внешний вид и поведение; используются только как `Modifier.testTag(TestTags.X)`.
 * Новые теги добавлять сюда, а не строкой по месту.
 */
object TestTags {
    // Нижняя навигация и мини-плеер
    const val BOTTOM_NAV = "nav.bottom"
    const val MINI_PLAYER = "player.mini"

    // Кнопки управления воспроизведением
    const val BTN_PLAY_PAUSE = "player.btn.playPause"
    const val BTN_NEXT = "player.btn.next"
    const val BTN_PREV = "player.btn.prev"

    // Поиск и библиотека
    const val SEARCH_FIELD = "search.field"
    const val LIBRARY_LIST = "library.list"
    const val TRACK_ROW = "library.trackRow"

    /** Тег строки конкретного трека (для поиска нужной строки в списке). */
    fun trackRow(trackId: Long) = "$TRACK_ROW.$trackId"
}
