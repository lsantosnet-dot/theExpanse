package com.theexpanse.game

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Efeitos sonoros do jogo. O núcleo só diz qual tocar; quem toca é o [Host] (no Android, um AudioTrack). */
enum class Sfx(val seconds: Double) {
    CLICK(0.04), LAUNCH(0.55), ALERT(0.5), HIT(0.45), EXPLOSION(0.9), DECOY(0.45), DOCK(0.6), INTERCEPT(0.25),
    /** Ronco do motor: sai em laço contínuo (sem emendas) enquanto a nave acelera. */
    ENGINE(0.5),
}

/** Sintetiza os efeitos como PCM de 16 bits mono, sem arquivos de áudio: tudo sai de senóides e ruído. */
object Synth {
    const val RATE = 22050

    fun render(sfx: Sfx): ShortArray {
        val n = (sfx.seconds * RATE).toInt()
        val noise = Noise(sfx.ordinal * 7919L + 17)
        val out = ShortArray(n)
        var phase = 0.0
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val u = i.toDouble() / n
            val v: Double = when (sfx) {
                Sfx.CLICK -> sin(2 * PI * 1800 * t) * exp(-t * 140)
                Sfx.LAUNCH -> {
                    // ruído que sobe de tom, com ataque rápido e queda suave
                    lp += (noise.next() - lp) * (0.05 + 0.5 * u)
                    lp * min(1.0, t * 80) * (1 - u) * 1.6
                }
                Sfx.ALERT -> {
                    val f = if ((t * 6).toInt() % 2 == 0) 880.0 else 660.0
                    phase += 2 * PI * f / RATE
                    sin(phase) * 0.55 * min(1.0, (1 - u) * 6)
                }
                Sfx.HIT -> {
                    phase += 2 * PI * (70 + 90 * exp(-t * 12)) / RATE
                    (sin(phase) * 0.9 + noise.next() * 0.5 * exp(-t * 30)) * exp(-t * 7)
                }
                Sfx.EXPLOSION -> {
                    lp += (noise.next() - lp) * 0.12
                    lp * exp(-t * 4.5) * 2.2 + sin(2 * PI * 45 * t) * exp(-t * 6) * 0.6
                }
                Sfx.DECOY -> {
                    phase += 2 * PI * (1400 + 1800 * u) / RATE
                    sin(phase) * 0.35 * (if ((t * 24).toInt() % 2 == 0) 1.0 else 0.2) * (1 - u)
                }
                Sfx.DOCK -> {
                    val thunk = sin(2 * PI * 110 * t) * exp(-t * 18)
                    val chime = sin(2 * PI * 1320 * (t - 0.12).coerceAtLeast(0.0)) * exp(-(t - 0.12).coerceAtLeast(0.0) * 8) * (if (t > 0.12) 0.35 else 0.0)
                    thunk * 0.8 + chime
                }
                Sfx.INTERCEPT -> {
                    phase += 2 * PI * (2400 - 1800 * u) / RATE
                    sin(phase) * exp(-t * 14) * 0.7
                }
                Sfx.ENGINE -> {
                    // ronco grave em laço: só harmônicos inteiros do período do clipe, então a emenda é contínua
                    val w = 2 * PI * t / sfx.seconds
                    0.5 * sin(w * 24) + 0.25 * sin(w * 48 + 1.0) + 0.15 * sin(w * 72 + 2.0) + 0.1 * sin(w * 31 + 0.5)
                }
            }
            out[i] = (v.coerceIn(-1.0, 1.0) * 0.6 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Gerador de ruído branco determinístico em [-1, 1]. */
    private class Noise(var s: Long) {
        fun next(): Double {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 33).toDouble() / (1L shl 31)) * 2 - 1
        }
    }
}
