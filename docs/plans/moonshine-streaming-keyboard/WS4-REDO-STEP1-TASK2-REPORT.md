# WS4-redo Step 1, Task 2 — Report

Headless, windowless voice-dictation session + explicit windowless dispatch.

## Status: DONE

Build `assembleUnstableDebug` → **BUILD SUCCESSFUL** (1m39s, warnings pre-existing/unrelated).

## Files changed

### New: `java/src/org/futo/inputmethod/latin/uix/voice/HeadlessVoiceSession.kt`
- `HeadlessVoiceSession(manager, state) : RecognizerViewListener` — drives the shared
  `RecognizerView` (which constructs `AudioRecognizer` with the IME lifecycle scope +
  `VoiceInputPersistentState.modelManager`, no Compose). Reusing `RecognizerView` instead of
  constructing `AudioRecognizer` directly avoids a third copy of the `AudioRecognizerSettings`
  assembly and gives the same main-thread callback marshalling.
- Exposes `listening: State<Boolean>` and `start(model, locales, generation)` / `stop()` /
  `cancel()`.
- Callbacks submit `EditIntent`s ONLY, never touch `InputConnection`:
  `recordingStarted`→`listening=true`; `partialResult`→`VoiceSnapshot`;
  `finished`→`VoiceFinal`+`listening=false`; `cancelled`→`VoiceFinal("")`+`listening=false`.
  Each is wrapped in `Dispatchers.Main.immediate` so coordinator mutation stays serial on the IME
  thread even if a callback ever arrives off-main.
- `onNewInputSession(newGen)` submits `NewInputSession(newGen)` stamped with the *old* generation
  so it passes the coordinator's stale-drop guard, then adopts `newGen`; every subsequent voice
  intent is stamped with the current generation.
- Private `VoiceTailSink : EditSink` performs field writes via
  `manager.getLatinIMEForDebug().currentInputConnection` inside `beginBatchEdit/endBatchEdit`.
  Partial-result partials are `ModelOutputSanitizer.sanitize`d (matching the windowed path).

### Modified: `java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt`
- `VoiceInputPersistentState` now hosts a lazily-created `headlessSession` with
  `startHeadlessSession(generation)`, `onNewInputSession(generation)`, `stopHeadlessSession()`.
  `cleanUp()` and `close()` cancel the session.
- Model lookup (`ResourceHelper.tryFindingVoiceInputModelForLocale`) + active locales moved into
  `startHeadlessSession`. Windowed `VoiceInputActionWindow` / `VoiceInputBottomBarWindow` and the
  `windowImpl` factory are untouched (toggle-OFF byte-for-byte unchanged).

### Modified: `java/src/org/futo/inputmethod/latin/uix/UixManager.kt`
- **Dispatch change** in `onActionActivatedInternal` (the resolved-override block that formerly went
  straight to `enterActionWindowView`):
  ```kotlin
  if (action == VoiceInputAction && latinIME.getSetting(VOICE_SIMULTANEOUS_TYPING)) {
      startHeadlessVoiceSession(action)
      return
  }
  ```
  Comparing the *post-override* `action` (not `rawAction`) means the system-voice override
  (`SystemVoiceInputAction`) is never captured — only the real local voice action.
- New `startHeadlessVoiceSession(action)`: initializes on-trigger persistent state exactly like
  `enterActionWindowView` (`persistentStates[action] = action.persistentState?.invoke(...)`),
  adds `FLAG_KEEP_SCREEN_ON`, and calls `state.startHeadlessSession(voiceSessionGeneration)` —
  it does NOT enter the action-window view, so the keyboard stays. A second tap while
  `listening` finalizes (`stop()`) instead of resetting the burst.
- New `voiceSessionGeneration: Long`. `inputStarted` bumps it and forwards to a live session via
  `onNewInputSession`. `onInputFinishing` calls `stopHeadlessSession()`.

### Modified: `java/src/org/futo/inputmethod/engine/general/ActionInputTransactionIME.kt`
- Removed the simultaneous-mode partial-suppression block in `updatePartial` (old `:80-84`). The
  coordinator now owns voice writing in simultaneous mode, and headless dispatch never creates an
  `ActionInputTransactionIME` for voice. Non-simultaneous composing path unchanged; the
  `VOICE_SIMULTANEOUS_TYPING` branches in `commit`/`cancel` were left intact (out of scope, and
  gated so harmless).

## Deviations / concerns for the reviewer

1. **EditSink implemented via relative `deleteSurroundingText`+`commitText`, not absolute
   `setSelection(tailStart,tailEnd)`.** The task described the setSelection form; I keep the tail at
   the cursor by tracking a single `tailLen` and deleting it before re-committing. Functionally
   identical under the Step-1 SAFE policy (the coordinator freezes the tail on any keypress/selection
   change via `freezeVoiceTail`, so voice never holds an offset across a cursor move) and avoids
   fragile cross-app absolute-offset reads (`getExtractedText` offsets are inconsistent across
   editors). `freezeVoiceTailPrefix(len)` subtracts from `tailLen`; `freezeVoiceTail` zeroes it.
   Marked with a `ponytail:` comment; absolute range tracking against the InputLogic
   `RichInputConnection` is the documented Step-2 upgrade path.

2. **Writes go through `LatinIME.currentInputConnection`, not the InputLogic-owned
   `RichInputConnection`.** This matches how `VoiceInputBottomBarWindow.deleteWordBeforeCursor`
   already pokes the connection directly. It bypasses `RichInputConnection`'s cache, which can
   desync InputLogic's model — acceptable for Step 1 (voice commits ordinary text; touch owns the
   composing region), but the reviewer should confirm on-device that touch autocorrect state is not
   corrupted after a voice burst. Task 3 (touch composing/selection signals to the coordinator) is
   the intended follow-up.

3. **Settings assembly duplicated.** `HeadlessVoiceSession.loadSettings` is a third copy of the
   `RecognizerViewSettings` builder (alongside the two window classes). Not extracted because the
   OFF-path `VoiceInputActionWindow` must stay byte-for-byte unchanged.

4. **Stop affordance.** Task 2 delivers start + toggle-stop via re-tapping the mic action. The mic
   glow (Task 4) and touch-collision signals (Task 3) are out of scope here; on-device the keyboard
   stays visible and voice streams into the field, but the mic key does not yet visibly indicate
   listening.

## Not visible in the diff
- `TextEditCoordinator.kt` (Task 1, already committed) is unmodified.
- No unrelated dirty files staged (`.settings/*`, `voiceinput-shared/src/main/ml/*` excluded).
- No build/tests beyond `assembleUnstableDebug` per spec (Android glue verified by compile;
  on-device is Task 6).
