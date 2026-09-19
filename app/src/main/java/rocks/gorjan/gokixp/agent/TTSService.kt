package rocks.gorjan.gokixp.agent

import android.content.Context

/**
 * Privacy-preserving speech shim.
 *
 * The previous implementation sent the complete text entered by the user, plus the selected
 * agent voice parameters, to tetyys.com to synthesize SAPI4 audio. WIN26 does not allow
 * non-browser data transmission, so external speech synthesis is disabled.
 *
 * Callers already implement a text-only fallback through [onError], which preserves Clippy's
 * visible speech bubble without transmitting its contents.
 */
class TTSService(@Suppress("UNUSED_PARAMETER") private val context: Context) {

    fun speakText(
        text: String,
        agent: Agent,
        onStart: () -> Unit,
        onAudioReady: (audioDurationMs: Long) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        @Suppress("UNUSED_VARIABLE")
        val unused = Triple(text, agent, onAudioReady)
        @Suppress("UNUSED_VARIABLE")
        val unusedComplete = onComplete

        onStart()
        onError(ExternalTtsDisabledException())
    }

    fun stopCurrentAudio() {
        // No external audio is created.
    }

    fun cleanup() {
        // No network TTS cache is maintained.
    }

    private class ExternalTtsDisabledException :
        IllegalStateException("External TTS is disabled to keep speech text on-device")
}
