package com.nexus.music.core

import kotlinx.serialization.Serializable

/** Как выбирается светлая/тёмная тема: всегда свой профиль, по системе или по расписанию. */
enum class ThemeMode { Fixed, System, Schedule, Sun }

/**
 * Настройки внешнего вида «светлая тема». В режиме [ThemeMode.Fixed] ничего не меняется (работает
 * выбранный цветовой профиль). В остальных режимах, когда «сейчас светло», вместо активного
 * профиля показывается [lightProfileId].
 */
@Serializable
data class ThemeSchedule(
    val mode: ThemeMode = ThemeMode.Fixed,
    val lightProfileId: String = "builtin-ivory",
    // Светлая тема действует с lightFromHour (включительно) до lightToHour (исключительно), 0..23.
    val lightFromHour: Int = 7,
    val lightToHour: Int = 20,
    // UI-07: цвета Material You (обои системы, Android 12+) вместо выбранного профиля. По умолчанию выкл.
    val useSystemColors: Boolean = false,
    // CUSTOM-08: режим «По солнцу»: широта вручную (долгота — из часового пояса), сдвиг в минутах.
    val sunLatitude: Float = 50f,
    val sunOffsetMin: Int = 0
)

object ThemeScheduleLogic {
    /** Попадает ли [hour] в [from, to); интервал может переходить через полночь (from > to). */
    fun inWindow(hour: Int, from: Int, to: Int): Boolean {
        val h = hour.coerceIn(0, 23); val a = from.coerceIn(0, 23); val b = to.coerceIn(0, 23)
        return when {
            a == b -> false
            a < b -> h in a until b
            else -> h >= a || h < b
        }
    }

    /**
     * [minuteOfDay], [dayOfYear] и [tzOffsetHours] нужны только режиму «По солнцу»; значения по
     * умолчанию дают прежнее поведение остальных режимов.
     */
    fun useLight(
        t: ThemeSchedule, systemDark: Boolean, hour: Int,
        minuteOfDay: Int = hour * 60, dayOfYear: Int = 1, tzOffsetHours: Double = 0.0
    ): Boolean = when (t.mode) {
        ThemeMode.Fixed -> false
        ThemeMode.System -> !systemDark
        ThemeMode.Schedule -> inWindow(hour, t.lightFromHour, t.lightToHour)
        ThemeMode.Sun -> {
            val day = SunTimes.compute(dayOfYear, t.sunLatitude.toDouble(), tzOffsetHours)
            SunTimes.isDaylight(day, minuteOfDay / 60.0, t.sunOffsetMin.coerceIn(-120, 120))
        }
    }
}
