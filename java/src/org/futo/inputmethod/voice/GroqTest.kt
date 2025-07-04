package org.futo.inputmethod.voice

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.voiceinput.shared.BuildConfig
import org.futo.voiceinput.shared.remote.GroqWhisperApi

fun Context.testGroqConnection() {
    runBlocking {
        val ok = try { GroqWhisperApi.ping(BuildConfig.GROQ_API_KEY) } catch(_: Exception){ false }
        withContext(Dispatchers.Main) {
            val msg = if(ok) getString(R.string.voice_input_groq_success) else getString(R.string.voice_input_groq_failed)
            Toast.makeText(this@testGroqConnection, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
