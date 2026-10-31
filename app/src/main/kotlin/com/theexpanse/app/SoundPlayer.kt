package com.theexpanse.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.theexpanse.game.Sfx
import com.theexpanse.game.Synth

/** Toca os efeitos sintetizados por [Synth]: um AudioTrack estático por efeito, criado uma vez e reaproveitado. */
class SoundPlayer {
    private val tracks = HashMap<Sfx, AudioTrack>()
    private var engineTrack: AudioTrack? = null

    private fun build(sfx: Sfx): AudioTrack? = try {
        val pcm = Synth.render(sfx)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Synth.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        if (sfx == Sfx.ENGINE) track.setLoopPoints(0, pcm.size, -1)
        track.setVolume(if (sfx == Sfx.ENGINE) 0.35f else 1f)
        track
    } catch (e: Exception) {
        null // sem áudio neste aparelho: o jogo segue mudo
    }

    fun play(sfx: Sfx) {
        val t = tracks.getOrPut(sfx) { build(sfx) ?: return }
        try {
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) {
        }
    }

    fun engine(on: Boolean) {
        try {
            if (on) {
                val t = engineTrack ?: build(Sfx.ENGINE)?.also { engineTrack = it } ?: return
                t.stop(); t.reloadStaticData(); t.play()
            } else engineTrack?.stop()
        } catch (e: Exception) {
        }
    }

    fun release() {
        tracks.values.forEach { it.release() }
        tracks.clear()
        engineTrack?.release()
        engineTrack = null
    }
}
