# WS2 Spec — Harden the online→offline voice fallback (backend only)

Scope: robustness fixes in the transcription fallback path. NO UI changes in this commit. All in
`voiceinput-shared/src/main/java/org/futo/voiceinput/shared/AudioRecognizer.kt` unless noted.

Rules for implementer (codex):
- Do NOT run gradle/build. Edit code, output unified diffs, short report.
- Do NOT touch `Q and A.qanda`, `.claude/`, status/plan files. Code only.
- Minimal diffs, must compile (Kotlin). Preserve all existing behavior except the three fixes below.
- If any referenced symbol/plumbing differs from this description, STOP and report rather than guess.

Context: inside `runModel()` there is a local `suspend fun transcribe(floatArray): String`. After the
WS1 change its order is: (1) if `shouldUseMoonshineLocalBackend()` use Moonshine and return;
(2) else if `settings.groqApiKey.isNotBlank()` try Groq; (3) else/last `modelRunner.run(...)` (whisper).
`runModel()` then computes `finalText` from the returned `primaryText` and calls
`listener.finished(finalText)` exactly once near the end.

## Fix 1 — remove the double `finished()` on Groq success
In the Groq branch, on a non-blank `groqResult` the code currently BOTH launches
`listener.finished(normalizeTranscription(groqResult))` on the main dispatcher AND returns
`groqResult` (which later flows to the single `listener.finished(finalText)` in `runModel`). That
delivers the transcription twice. Remove the inner `lifecycleScope.launch { withContext(Main) { ...
listener.finished(...) } }` block for the Groq-success case so Groq just returns `groqResult` and the
outer `runModel` path fires `finished` once. Keep the session-id guard/return semantics intact.

## Fix 2 — connectivity pre-check before calling Groq
Add a small private helper that returns whether the network is up, e.g.:

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true  // if we can't tell, don't block the online path
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

Use the existing `context` field of AudioRecognizer. Add the needed imports
(`android.net.ConnectivityManager`, `android.net.NetworkCapabilities`, `android.content.Context` if
not already imported). Then change the Groq guard from `if (settings.groqApiKey.isNotBlank())` to
`if (settings.groqApiKey.isNotBlank() && isNetworkAvailable())` so that when offline we skip the
network attempt entirely and fall straight through to the local whisper model — no socket timeout wait.

## Fix 3 — guard the last-resort offline (whisper) stage
The final `return modelRunner.run(floatArray, settings.modelRunConfiguration,
settings.decodingConfiguration, runnerCallback).trim()` can throw when the model isn't present
(e.g. `ModelDoesNotExistException`, `InvalidModelException`) — today that propagates uncaught in the
coroutine and the user sees nothing. Wrap this final `modelRunner.run(...)` call so that:
- `InferenceCancelledException` continues to propagate exactly as before (do NOT swallow it — the
  outer `runModel` catches it). Re-throw it.
- Any other `Throwable` is logged (`Log.e("AudioRecognizer", ...)`) and results in returning an empty
  string `""` (so the normal blank-result handling in `runModel` produces an empty final transcript
  rather than a crash). Report this choice in your notes.

## Deferred (do NOT do in this commit — just note them in your report)
- Honor the `USE_GROQ_WHISPER` toggle for selection (not only `groqApiKey.isNotBlank()`).
- Surface differentiated errors (401/429/timeout/offline) in the voice UI (`RecognizerView`).

## Report
Unified diff of AudioRecognizer.kt, confirmation the three fixes are in, and note the two deferred
items. No gradle.
