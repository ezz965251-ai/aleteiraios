package com.nexus.music.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * CUSTOM-08: приблизительные восход и закат (точность порядка 10–15 минут) без сети и геолокации.
 * Долгота берётся из часового пояса телефона (15° на час), широта задаётся вручную в настройках.
 * Чистая функция без Android API.
 */
object SunTimes {
    /** Время суток в часах (0..24, по местным часам). */
    data class Day(val sunrise: Double, val sunset: Double, val polar: Polar)
    enum class Polar { None, AlwaysLight, AlwaysDark }

    fun compute(dayOfYear: Int, latitudeDeg: Double, tzOffsetHours: Double): Day {
        val n = dayOfYear.coerceIn(1, 366)
        val lat = latitudeDeg.coerceIn(-89.0, 89.0) * PI / 180.0
        val decl = 23.44 * PI / 180.0 * sin(2.0 * PI * (284 + n) / 365.0)
        val cosH = -tan(lat) * tan(decl)
        if (cosH > 1.0) return Day(12.0, 12.0, Polar.AlwaysDark)
        if (cosH < -1.0) return Day(0.0, 24.0, Polar.AlwaysLight)
        val halfDayHours = acos(cosH) * 180.0 / PI / 15.0
        val b = 2.0 * PI * (n - 81) / 364.0
        val eqMinutes = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val lon = tzOffsetHours * 15.0
        val noon = 12.0 - lon / 15.0 + tzOffsetHours - eqMinutes / 60.0
        return Day(noon - halfDayHours, noon + halfDayHours, Polar.None)
    }

    /** Светло ли в [hourOfDay] (дробные часы). Рассвет и закат сдвигаются на [offsetMinutes]. */
    fun isDaylight(day: Day, hourOfDay: Double, offsetMinutes: Int = 0): Boolean = when (day.polar) {
        Polar.AlwaysLight -> true
        Polar.AlwaysDark -> false
        Polar.None -> hourOfDay >= day.sunrise + offsetMinutes / 60.0 && hourOfDay < day.sunset - offsetMinutes / 60.0
    }
}
