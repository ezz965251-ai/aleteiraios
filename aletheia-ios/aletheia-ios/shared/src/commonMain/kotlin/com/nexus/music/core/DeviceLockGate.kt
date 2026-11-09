package com.nexus.music.core

/**
 * SEC-03: подтверждение замком устройства (отпечаток, PIN, графический ключ, пароль) перед
 * действиями с секретами — подключение/отключение токена ListenBrainz, экспорт резервной копии и
 * настроек. Системное окно подтверждения открывает `ui/DeviceLockUi.kt`; здесь — только решение,
 * нужно ли его показывать. После успешного подтверждения окно «льготы» [GRACE_MS] не спрашивает снова.
 * Если на устройстве нет блокировки экрана, окно не показывается и действие выполняется сразу.
 */
object DeviceLockGate {
    const val GRACE_MS = 60_000L

    /** Время (elapsedRealtime), до которого подтверждение считается свежим. */
    @Volatile var unlockedUntil: Long = 0L

    fun needsPrompt(enabled: Boolean, now: Long, unlockedUntil: Long = this.unlockedUntil): Boolean =
        enabled && now >= unlockedUntil

    fun markUnlocked(now: Long) { unlockedUntil = now + GRACE_MS }

    fun reset() { unlockedUntil = 0L }
}
