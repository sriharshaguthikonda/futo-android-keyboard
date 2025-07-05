package org.futo.voiceinput.shared.local

import org.futo.voiceinput.shared.util.DebugLogger

/**
 * Simple placeholder for on-device chat inference.
 * This implementation returns null as offline chat is
 * not yet supported in this build.
 */
object LocalChatApi {
    fun chat(systemPrompt: String, userPrompt: String, modelPath: String): String? {
        DebugLogger.log("LocalChatApi.chat called but not implemented")
        return null
    }
}
