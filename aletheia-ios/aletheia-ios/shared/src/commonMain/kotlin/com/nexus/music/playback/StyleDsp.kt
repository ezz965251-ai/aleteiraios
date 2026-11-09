package com.nexus.music.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Живые параметры «Стилей трека» для собственного DSP ([StyleAudioProcessor]). Пишет NexusViewModel
 * (applyTrackStyles), читает аудиопоток. Всё 0..1, кроме [pitch] и [makeupDb].
 *
 * Почему свой DSP, а не системный BassBoost/Virtualizer: системные эффекты на многих телефонах
 * «заглушены» — бас еле слышен, зато общая громкость падает (эффект режет запас по уровню). Здесь бас
 * строится из трёх частей (низкий «шельф», гармоники для динамиков телефона, суб-октава), а в конце
 * стоит лимитер с компенсацией громкости: стало басовитее — и НЕ тише.
 */
object StyleDspTap {
    @Volatile var bass = 0f
    @Volatile var sub = 0f
    @Volatile var drive = 0f
    @Volatile var punch = 0f
    @Volatile var wide = 0f
    @Volatile var lofi = 0f

    /** Итоговый множитель тона после нашего процессора (Sonic стоит позже): кроссовер баса сдвигается обратно. */
    @Volatile var pitch = 1f

    /** Компенсация громкости, дБ (0..+6). Лимитер не даёт ей клиппить. */
    @Volatile var makeupDb = 0f

    /** Широкое стерео отключается на время 8D, иначе оно размазывает положение источника. */
    @Volatile var suppressWide = false

    val isNeutral: Boolean
        get() = bass <= 0f && sub <= 0f && drive <= 0f && punch <= 0f && wide <= 0f && lofi <= 0f
}

/**
 * Ядро обработки без Android и media3 (чтобы его можно было проверять обычным тестом).
 * Вход/выход — float −1..1, стерео. Один вызов [process] на кадр.
 */
class StyleDsp(private val fs: Float) {
    var outL = 0f
        private set
    var outR = 0f
        private set

    private class Bq {
        var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
        private var z1 = 0f; private var z2 = 0f
        fun lowpass(fs: Float, f: Float, q: Float = 0.7071f) {
            val w = 2.0 * PI * f.coerceIn(10f, fs * 0.45f) / fs
            val al = sin(w) / (2.0 * q)
            val c = cos(w)
            val a0 = 1.0 + al
            b0 = (((1.0 - c) / 2.0) / a0).toFloat()
            b1 = ((1.0 - c) / a0).toFloat()
            b2 = b0
            a1 = ((-2.0 * c) / a0).toFloat()
            a2 = ((1.0 - al) / a0).toFloat()
        }
        fun highpass(fs: Float, f: Float, q: Float = 0.7071f) {
            val w = 2.0 * PI * f.coerceIn(5f, fs * 0.45f) / fs
            val al = sin(w) / (2.0 * q)
            val c = cos(w)
            val a0 = 1.0 + al
            b0 = (((1.0 + c) / 2.0) / a0).toFloat()
            b1 = ((-(1.0 + c)) / a0).toFloat()
            b2 = b0
            a1 = ((-2.0 * c) / a0).toFloat()
            a2 = ((1.0 - al) / a0).toFloat()
        }
        fun run(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
        fun reset() { z1 = 0f; z2 = 0f }
    }

    // Кроссовер баса: два каскада ФНЧ (24 дБ/окт.) по моно-сумме.
    private val lpA = Bq()
    private val lpB = Bq()
    // Гармоники: срез ниже кроссовера и выше ~700 Гц.
    private val hHp = Bq()
    private val hLp = Bq()
    // Суб-октава: ФНЧ квадрата + ФВЧ от инфразвука.
    private val sLpA = Bq()
    private val sLpB = Bq()
    private val sHp = Bq()
    // Моно-бас: низ боковой составляющей.
    private val sideLp = Bq()
    private val dcL = Bq()
    private val dcR = Bq()

    private var fcApplied = -1f
    private var lofiApplied = -1f
    private var lofiL = 0f
    private var lofiR = 0f
    private var lofiA = 1f

    // Сглаженные параметры (≈25 мс): включение/выключение без щелчков.
    private var pBass = 0f; private var pSub = 0f; private var pDrive = 0f
    private var pPunch = 0f; private var pWide = 0f; private var pLofi = 0f; private var pMakeup = 0f
    private val smoothK = 1f - exp(-1.0 / (0.025 * fs)).toFloat()

    // Огибающие: суб-октава, «пунш».
    private var subEnv = 0f
    private var subState = 1f
    private var subArmed = true
    private var envFast = 0f
    private var envSlow = 0f
    private val kSubAtk = 1f - exp(-1.0 / (0.004 * fs)).toFloat()
    private val kSubRel = 1f - exp(-1.0 / (0.060 * fs)).toFloat()
    private val kFastAtk = 1f - exp(-1.0 / (0.001 * fs)).toFloat()
    private val kFastRel = 1f - exp(-1.0 / (0.015 * fs)).toFloat()
    private val kSlowAtk = 1f - exp(-1.0 / (0.020 * fs)).toFloat()
    private val kSlowRel = 1f - exp(-1.0 / (0.120 * fs)).toFloat()

    // Лимитер.
    private var gain = 1f
    private val kLimRel = 1f - exp(-1.0 / (0.140 * fs)).toFloat()

    /** true, пока эффект полностью выключен и сглаженные значения упали до нуля — можно копировать вход без обработки. */
    val idle: Boolean
        get() = StyleDspTap.isNeutral && pBass < 1e-4f && pSub < 1e-4f && pDrive < 1e-4f &&
            pPunch < 1e-4f && pWide < 1e-4f && pLofi < 1e-4f && pMakeup < 1e-3f

    fun reset() {
        listOf(lpA, lpB, hHp, hLp, sLpA, sLpB, sHp, sideLp, dcL, dcR).forEach { it.reset() }
        subEnv = 0f; envFast = 0f; envSlow = 0f; gain = 1f; lofiL = 0f; lofiR = 0f
        subState = 1f; subArmed = true
        fcApplied = -1f; lofiApplied = -1f
    }

    private fun retune(pitch: Float) {
        // Тон меняется ПОСЛЕ нас: делим на pitch, чтобы бас в итоге остался на своих герцах.
        val fc = (125f / pitch.coerceIn(0.5f, 2f)).coerceIn(60f, 250f)
        if (abs(fc - fcApplied) < 0.5f) return
        fcApplied = fc
        lpA.lowpass(fs, fc); lpB.lowpass(fs, fc)
        hHp.highpass(fs, fc * 0.7f)
        hLp.lowpass(fs, 720f)
        sLpA.lowpass(fs, 70f); sLpB.lowpass(fs, 70f)
        sHp.highpass(fs, 24f)
        sideLp.lowpass(fs, fc)
        dcL.highpass(fs, 18f); dcR.highpass(fs, 18f)
    }

    fun process(inL: Float, inR: Float) {
        val tap = StyleDspTap
        pBass += (tap.bass - pBass) * smoothK
        pSub += (tap.sub - pSub) * smoothK
        pDrive += (tap.drive - pDrive) * smoothK
        pPunch += (tap.punch - pPunch) * smoothK
        pWide += ((if (tap.suppressWide) 0f else tap.wide) - pWide) * smoothK
        pLofi += (tap.lofi - pLofi) * smoothK
        pMakeup += (tap.makeupDb - pMakeup) * smoothK
        retune(tap.pitch)
        if (abs(pLofi - lofiApplied) > 0.01f) {
            lofiApplied = pLofi
            val f = 15000f * (1f - pLofi) + 4200f * pLofi
            lofiA = 1f - exp(-2.0 * PI * f / fs).toFloat()
        }

        var l = inL
        var r = inR

        // 1) Ширина: боковая составляющая растёт, но ниже кроссовера остаётся моно (бас «в центре»).
        if (pWide > 1e-4f) {
            val m = (l + r) * 0.5f
            var s = (l - r) * 0.5f
            val sLow = sideLp.run(s)
            s = s * (1f + pWide * 0.9f) - sLow * (pWide * 0.85f)
            l = m + s
            r = m - s
        } else {
            sideLp.run(0f)
        }

        // 2) Бас по моно-сумме.
        val mono = (l + r) * 0.5f
        val b = lpB.run(lpA.run(mono))
        var add = b * (pBass * 1.35f)

        // «Пунш»: быстрая огибающая выше медленной — атака бочки усиливается.
        val ab = abs(b)
        envFast += (ab - envFast) * (if (ab > envFast) kFastAtk else kFastRel)
        envSlow += (ab - envSlow) * (if (ab > envSlow) kSlowAtk else kSlowRel)
        if (pPunch > 1e-4f) {
            val ratio = envFast / (envSlow + 1e-4f)
            add *= 1f + pPunch * min(1.6f, max(0f, ratio - 1f)) * 0.9f
        }

        // Гармоники: насыщение баса даёт обертоны, которые слышны и на динамике телефона.
        val harm = pBass * 0.30f + pDrive * 0.35f
        if (harm > 1e-4f) {
            val k = 3f + 9f * max(pBass, pDrive)
            add += hLp.run(hHp.run(tanh(b * k))) * harm
        } else {
            hHp.run(0f); hLp.run(0f)
        }

        // Суб-октава: делитель частоты на 2 по переходам через ноль, амплитуда — от огибающей баса.
        if (pSub > 1e-4f) {
            val thr = 0.004f
            if (b > thr && subArmed) { subState = -subState; subArmed = false }
            else if (b < -thr) subArmed = true
            subEnv += (ab - subEnv) * (if (ab > subEnv) kSubAtk else kSubRel)
            val sq = sLpB.run(sLpA.run(subState * min(1f, subEnv * 5f)))
            add += sHp.run(sq) * (pSub * 1.1f)
        }

        l += add
        r += add

        // 3) Драйв: мягкое насыщение всего сигнала (жёсткий «хардстайловый» кик).
        if (pDrive > 1e-4f) {
            val g = 1f + 2.6f * pDrive
            val inv = 1f / tanh(g)
            val m = pDrive * 0.55f
            l = l * (1f - m) + tanh(l * g) * inv * m
            r = r * (1f - m) + tanh(r * g) * inv * m
        }

        // 4) Lo-fi: срез верхов.
        if (pLofi > 1e-4f) {
            lofiL += (l - lofiL) * lofiA
            lofiR += (r - lofiR) * lofiA
            l = lofiL; r = lofiR
        }

        // 5) Компенсация громкости и лимитер: пики не клиппят, общая громкость не падает.
        val mk = if (pMakeup > 1e-3f) 10f.pow(pMakeup / 20f) else 1f
        l = dcL.run(l) * mk
        r = dcR.run(r) * mk
        val peak = max(abs(l), abs(r))
        val need = if (peak > LIMIT) LIMIT / peak else 1f
        gain = if (need < gain) need else gain + (1f - gain) * kLimRel
        l *= gain; r *= gain
        // Страховка от редких пиков, проскочивших атаку.
        outL = softGuard(l)
        outR = softGuard(r)
    }

    private fun softGuard(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val over = (a - KNEE) / (1f - KNEE)
        val y = KNEE + (1f - KNEE) * tanh(over)
        return if (x < 0f) -y else y
    }

    private companion object {
        const val LIMIT = 0.93f
        const val KNEE = 0.90f
    }
}
