# Moonshine Streaming Keyboard — Implementation Plan

> Authoritative in-repo plan. Working copy mirrored at `~/.claude/plans/` (agent scratch).
> Related: [`docs/Research/chatgpt_input_roadmap.txt`](../../Research/chatgpt_input_roadmap.txt) (vision) ·
> branch `feature/moonshine-streaming` · upstream `gitlab.futo.org/keyboard/latinime`.

## Goal

Turn voice input into a true streaming, programmable, production-grade dictation system on the
FUTO Keyboard fork. Four immediate workstreams + two follow-ups, executed **one at a time,
commit by commit**, each verified on-device before the next.

## Root cause (why it's not streaming today)

Moonshine live-streaming is gated behind `groqApiKey.isBlank()`:

- [`AudioRecognizer.kt:468-473`](../../../voiceinput-shared/src/main/java/org/futo/voiceinput/shared/AudioRecognizer.kt#L468)
  `shouldUseMoonshineLiveStreaming()` returns false whenever a Groq key is stored — even offline.
- So `runModel().transcribe()` ([AudioRecognizer.kt:919](../../../voiceinput-shared/src/main/java/org/futo/voiceinput/shared/AudioRecognizer.kt#L919))
  runs the **batch** Moonshine path, which chunks the finished recording → looks like streaming only
  at the end. This is the reported "records fully, then streams" behavior.
- Partials are also UI-hidden: `shouldShowInlinePartialResult = false`
  ([VoiceInputAction.kt:211](../../../java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt#L211)).

**Design decision (user):** Groq never streams (too costly). Live streaming is a Moonshine-only
feature, active only when the Moonshine streaming backend is selected.

## Constraints

- **Don't reinvent** — reuse `upstream` and the repo's own `codex/*` branches (esp.
  `exclusive-groq-use-branch-fall-back-if-fail`, `add-cyclical-buffer-for-voice-input`).
- **License** — copy freely from FUTO upstream (same Source-First license) + own branches.
  HeliBoard (GPLv3) / Thumb-Key (AGPL) = design references only, no source copying.

## Workstreams (in execution order)

| WS  | Goal | Key files | Status |
|-----|------|-----------|--------|
| 0   | In-repo plan + build/install `unstable` variant, switch active IME | this file; `build.gradle` | in progress |
| 1   | True Moonshine live streaming (gate on backend, not key; show partials) | `AudioRecognizer.kt`, `MoonshineStreamingLocalBackend.kt`, `VoiceInputAction.kt` | pending |
| 2   | Production offline fallback (connectivity pre-check, readTimeout, error UI, guard last stage, fix double-commit) | `AudioRecognizer.kt:919-971`, `GroqWhisperApi.kt`, `RecognizerView.kt` | pending |
| 3   | Voice commands + literal mode (local deterministic parser) | `ModelOutputSanitizer.kt`, `Action.kt`, `VoiceInputSettingKeys.kt`, `VoiceInput.kt`, `InputLogic.java` | pending |
| 4a  | Talk-and-type: keyboard visible+tappable under voice pill (`onlyShowAboveKeyboard=true`) | `UixManager.kt:773`, `VoiceInputAction.kt:403` | pending |
| 4b  | (deferred) True concurrent multi-writer merge | `IMEManager.kt:75`, `ActionInputTransactionIME.kt:49`, `InputConnectionInternalComposingWrapper.kt` | deferred |
| 5   | AI Reply UX fix (ChatGPT design pass first) + roadmap §6 text tools | AI-reply/radial-menu classes | pending |

Detail for each WS lives in the agent working copy; this table + root-cause is the durable summary.

## Verification (on-device, adb — phone `10BF191Z51001DC`)

Prereq: active IME must be `org.futo.inputmethod.latin.unstable` (NOT the Play Store build).

- **WS1:** `adb logcat -s MoonshineStreaming AudioRecognizer VoiceInputAction`; expect
  "Starting Moonshine live streaming session" (not the `:477` "disabled" line) and partials
  arriving mid-utterance; field updates live on screen.
- **WS2:** airplane mode + Groq key stored → fast offline fallback (no long hang) + visible
  error/fallback, not silence; bad key → visible auth error.
- **WS3:** "new line" / "delete" / "send" → editor actions; literal mode → words stay literal.
- **WS4a:** voice pill up + tap keys → characters appear.
