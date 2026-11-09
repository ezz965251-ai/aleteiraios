package com.nexus.music.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * TEST-03. Диспатчеры, которые можно подменить в тестах. Значения по умолчанию — те же
 * `Dispatchers.*`, что использовались напрямую, поэтому поведение приложения не меняется.
 * Переводить классы по одному файлу: сначала параметр конструктора с значением по умолчанию,
 * затем замена `Dispatchers.IO` на `dispatchers.io`. Уже переведены: [SettingsStore], `NormalizationStore`, `GenreStore`, `AutoBackupStore` (только `io`).
 */
class NexusDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val main: CoroutineDispatcher = Dispatchers.Main
)
