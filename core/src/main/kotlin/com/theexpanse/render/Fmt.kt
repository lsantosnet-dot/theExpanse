package com.theexpanse.render

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatação em português do Brasil: vírgula decimal, ponto de milhar. */
object Fmt {
    fun dec(v: Double, digits: Int): String = String.format(Locale.ROOT, "%.${digits}f", v).replace('.', ',')

    fun int(v: Double): String {
        val n = v.roundToLong()
        val s = abs(n).toString()
        val sb = StringBuilder()
        for ((i, ch) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
            sb.append(ch)
        }
        return (if (n < 0) "-" else "") + sb
    }

    /** Distância em km (ou m quando curta). */
    fun distance(m: Double): String = when {
        abs(m) >= 100_000.0 -> int(m / 1000) + " km"
        abs(m) >= 1000.0 -> dec(m / 1000, 1) + " km"
        else -> int(m) + " m"
    }

    fun speed(mps: Double): String = if (abs(mps) >= 1000.0) dec(mps / 1000, 2) + " km/s" else dec(mps, 1) + " m/s"

    /** Duração legível: "5 d 3 h", "7 h 32 min", "12 min 10 s", "45 s". */
    fun duration(seconds: Double): String {
        val t = seconds.toLong().coerceAtLeast(0)
        val d = t / 86_400
        val h = (t % 86_400) / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return when {
            d > 0 -> "$d d $h h"
            h > 0 -> "$h h ${m.toString().padStart(2, '0')} min"
            m > 0 -> "$m min ${s.toString().padStart(2, '0')} s"
            else -> "$s s"
        }
    }

    fun clock(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        val d = total / 86_400
        val h = (total % 86_400) / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val hms = String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
        return if (d > 0) "${d}d $hms" else hms
    }
}
