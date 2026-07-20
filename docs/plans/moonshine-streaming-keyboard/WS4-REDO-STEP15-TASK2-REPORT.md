# WS4-redo Step 1.5 — Task 2 report: VAD pause finalizes + keeps listening (Fix 2)

File changed: `java/src/org/futo/inputmethod/latin/uix/voice/HeadlessVoiceSession.kt` (only).
UixManager untouched — no plumbing was needed: stop/cancel already reach the session via
`stop()` (mic-tap toggle) and `cancel()` (`onInputFinishing` → `stopHeadlessSession()`), so a
flag inside the session covers every stop path.

## What changed

- `userRequestedStop`: set by `stop()` and `cancel()`, cleared in `start()`. Distinguishes
  user/lifecycle stops from VAD auto-stop.
- `finished(result)`: after submitting `VoiceFinal` (final text commits, coordinator resets
  burst state), if the stop was NOT user-requested and the guards pass, a fresh burst is
  restarted via `startBurst()` and `listeningState` stays `true` — the mic glow never blinks.
  Logcat marker: `VAD finalize -> auto-restart burst`.
- `startBurst(view)`: the exact sequence `start()` already used, extracted —
  `getVoiceInputPrebufferSnapshot()` + `stopVoiceInputPrebuffering()` + `view.reset()` +
  `view.setPendingPrebuffer()` + `view.start()`. No model/locale storage needed: the
  `RecognizerView` bakes its settings at construction and is reused for the restart.
- `cancelled()` unchanged: real cancel never restarts; `listening` goes false as before.

## Why the restart sequence is safe post-`finished()`

After VAD auto-stop, `AudioRecognizer` is idle: `recordingJob` called `finish()` →
`onFinishRecording()` set `isRecording=false`, stopped (not released) the recorder, and
`runModel()` delivered `finished()` on Main. `reset()` then:

- increments `sessionId` — any straggler callback from the old burst (including the Groq
  path's double-`finished` quirk in `transcribe()`, which checks `sessionId` inside its Main
  dispatch) is dropped before it reaches us;
- releases the stopped recorder, cancels recorder/model/load jobs, closes the moonshine
  streaming session, clears sample/prebuffer buffers, abandons audio focus.

`start()` after that is indistinguishable from a cold user start. Because the restart runs
synchronously inside the `finished()` Main dispatch (`Dispatchers.Main.immediate`), the
`sessionId` bump lands before any queued second `finished()` can execute.

## Guard conditions (no restart → `listening=false`)

1. `userRequestedStop` — mic tap (`stop()`) or `cancel()`/`onInputFinishing`. A mic tap during
   the post-VAD processing gap also works: `finish()` no-ops (`!isRecording`) but the flag
   makes the imminent `finished()` terminal.
2. `generation != burstGeneration` — `onNewInputSession` happened since the burst started
   (field/editor changed); do not resurrect the mic in a new field.
3. `recognizerView == null` — nothing to restart.
4. `consecutiveEmptyFinals >= 2` — runaway-silence brake: two consecutive blank finals stop
   the auto-restart loop. Counter resets on any nonempty final and on every user `start()`.

`onInputFinishing` racing `finished()`: if `cancel()` runs first, `reset()`'s `sessionId` bump
means `finished()` never arrives (only `cancelled()`, which never restarts); if `finished()`
runs first and restarts, the following `cancel()` tears the new burst down normally.

## Build / test

- `./gradlew assembleUnstableDebug` → BUILD SUCCESSFUL (warning-clean for this file).
- `./gradlew testUnstableDebugUnitTest --tests "...TextEditCoordinatorTest"` → BUILD
  SUCCESSFUL (no regression; coordinator untouched).

## Concerns

1. VAD auto-stop requires `hasTalked`, so every auto-restart cycle needs ~150ms of speech to
   arm and ~2s (66 frames) of silence to fire — a user pausing for many minutes keeps the mic
   hot until they tap it off (by design per Q1, but battery/privacy worth an on-device look).
2. The empty-final brake allows exactly one silent restart cycle; a noisy environment that
   yields nonempty junk finals ("you", "uh") resets the counter and can loop indefinitely —
   acceptable per plan, tunable via `MAX_CONSECUTIVE_EMPTY_FINALS`.
3. Restart replays the prebuffer-snapshot call even though IME-side prebuffering is inactive
   mid-session (returns empty); harmless, kept for sequence parity with `start()`.
