package com.nexus.music.playback

/**
 * AUDIO-01: A–B повтор — цикл по отрезку текущего трека.
 *
 * Чистая логика без Android: состояние и переходы, чтобы их можно было проверить юнит-тестом.
 * Нажатия меню идут по кругу: «задать A» → «задать B» → «сбросить».
 */
data class AbLoop(
    val trackId: Long = -1L,
    val startMs: Long = -1L,
    val endMs: Long = -1L
) {
    val hasStart: Boolean get() = startMs >= 0L
    val active: Boolean get() = hasStart && endMs > startMs
    val isEmpty: Boolean get() = !hasStart

    companion object {
        /** Минимальная длина отрезка: короче — точка B игнорируется (иначе цикл «дребезжит»). */
        const val MIN_LENGTH_MS = 1_000L
    }
}

/** Следующее состояние при очередном нажатии пункта меню в позиции [positionMs]. */
fun AbLoop.step(trackId: Long, positionMs: Long): AbLoop {
    val pos = positionMs.coerceAtLeast(0L)
    return when {
        // другой трек или пустое состояние → ставим A
        this.trackId != trackId || isEmpty -> AbLoop(trackId, pos, -1L)
        // A есть, B ещё нет → ставим B, если отрезок не слишком короткий
        !active -> if (pos - startMs >= AbLoop.MIN_LENGTH_MS) copy(endMs = pos) else this
        // цикл активен → сбрасываем
        else -> AbLoop()
    }
}

/**
 * Если цикл активен, а позиция дошла до B (или ушла за него) — возвращает позицию для перемотки
 * (точка A), иначе null. [trackId] — текущий трек: у чужого трека цикл не применяется.
 */
fun AbLoop.seekTarget(trackId: Long, positionMs: Long): Long? =
    if (active && this.trackId == trackId && positionMs >= endMs) startMs else null
