# WS4 (redo) Step 1 — Windowless Gboard-style dictation — Implementation Plan

> Design: [WS4-REDO-DICTATION-DESIGN.md](WS4-REDO-DICTATION-DESIGN.md) · Index: [PLAN.md](PLAN.md)

> **For the implementer (Codex):** Execute task-by-task. Confirm each *Investigation*
> block against the real code BEFORE writing the code for that task — do not guess Android
> internals. Report unknowns back rather than fabricating. Commit per task.

**Goal:** When "Simultaneous voice + typing" is ON, dictation runs with the normal keyboard
fully visible (no overlay bar), the mic key glows while listening, and stable voice text
streams live into the field via a single-writer coordinator — instead of the rejected
bottom-bar window that buffered then dumped.

**Architecture:** Voice becomes a headless background session (no `ActionWindow`) whose
output flows through a serial `TextEditCoordinator`. Touch keeps ownership of the Android
composing region; voice only ever `commitText`s *stable* chunks, buffering them while a typed
word/selection is active. A shared `listening` state drives the mic-key glow.

**Tech Stack:** Kotlin, Android IME (FUTO/AOSP LatinIME), Jetpack Compose (action bar UI),
Gradle (`assembleUnstableDebug`), JUnit (JVM unit tests for the pure coordinator).

## Global Constraints (verbatim from spec)

- Android 7–11: editor has **ONE composing region + ONE selection**. Voice must NOT own a
  composing span. Voice never calls `setComposingText()` while touch typing coexists.
- Gating: behind `VOICE_SIMULTANEOUS_TYPING`. **Toggle OFF path must stay byte-for-byte
  unchanged** (original `VoiceInputActionWindow` full-screen).
- Recognition callbacks must **never** call `InputConnection` directly — everything through
  the coordinator, serialized on the IME thread.
- Do not touch unrelated dirty files (`.settings/…buildship…`, `voiceinput-shared/src/main/ml`).
- Do not scope-creep into the roadmap (voice commands, snippets, programmable actions).

## File structure

- New: `java/src/org/futo/inputmethod/latin/uix/voice/TextEditCoordinator.kt` — pure serial
  edit-intent reducer (unit-tested).
- New test: `java/tests/.../uix/voice/TextEditCoordinatorTest.kt` (mirror existing test dir
  layout — confirm path in Task 1 investigation).
- Modify: `java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt` — windowless
  start path when toggle ON.
- Modify: `java/src/org/futo/inputmethod/engine/general/ActionInputTransactionIME.kt` —
  remove partial-suppression; route stable voice text via coordinator/commit.
- Modify: `java/src/org/futo/inputmethod/latin/uix/ActionBar.kt` — mic-key glow from
  `listening` state.
- Modify (location TBD): shared `listening` state holder (`VoiceInputPersistentState` or
  `UixManager`).

---

### Task 0: Investigation — confirm the four unknowns (no code, report findings)

Codex reads the code and answers, in a short `WS4-REDO-STEP1-FINDINGS.md`, before any edits:

1. **Moonshine partial semantics.** In `AudioRecognizer` / `RecognizerView.partialResult`,
   does `partialResult(text)` deliver the *entire running hypothesis so far* (revisable) or
   an *incremental delta*? Where do finalized segment boundaries occur? This determines how
   the coordinator computes the "stable prefix delta."
   - Files: `voiceinput-shared/src/main/java/org/futo/voiceinput/shared/AudioRecognizer.kt`,
     `RecognizerView.kt`.
2. **Headless recognizer lifecycle.** Can `AudioRecognizer` be driven (create → `reset` →
   `start` → `finish`/`cancel`) with a listener but WITHOUT composing `RecognizerView.Content()`?
   What owns its coroutine scope if there's no window? (`VoiceInputPersistentState` already
   holds `ModelManager` + lives across windows — candidate host.)
3. **Keystroke interception point.** Where do committed keystrokes/word-commits flow through
   the IME (`InputLogic` / `IMEHelper`) such that the coordinator can observe "keyboard is
   composing a word" / "word just committed" / "selection/cursor changed"? Identify the exact
   hook(s) the coordinator will subscribe to. (`ActionInputTransactionIME.onUpdateSelection`
   already exists as one signal.)
4. **Mic-key rendering.** In `ActionBar.kt`, find the composable that renders an action item's
   icon and how the voice `Action` maps to it. Identify where a per-item `listening` boolean
   can drive a tint/pulse without restructuring the bar.

Acceptance: `WS4-REDO-STEP1-FINDINGS.md` answers all four with file:line refs. If any is
infeasible as designed, STOP and report — do not work around silently.

---

### Task 1: `TextEditCoordinator` pure logic + unit tests

The one piece that is pure and testable in isolation. Everything Android-specific is behind a
tiny `EditSink` interface so the reducer is JVM-unit-testable.

**Files:**
- Create: `java/src/org/futo/inputmethod/latin/uix/voice/TextEditCoordinator.kt`
- Test: confirm test dir in Task 0.4 / existing layout, then create
  `…/uix/voice/TextEditCoordinatorTest.kt`

**Interfaces (Produces):**
```kotlin
interface EditSink {
    fun commitVoiceText(text: String)   // ic.commitText(text, 1) on IME thread
    fun showUnstable(text: String)      // suggestion-strip preview ("" clears)
}

sealed interface EditIntent {
    data class VoicePartial(val fullHypothesis: String) : EditIntent
    data class VoiceFinal(val text: String) : EditIntent
    object KeyboardComposingStarted : EditIntent   // touch owns composing now
    object KeyboardWordCommitted : EditIntent       // flush boundary
    data class SelectionChanged(val start: Int, val end: Int) : EditIntent
    data class NewInputSession(val generation: Long) : EditIntent
}

class TextEditCoordinator(private val sink: EditSink) {
    fun submit(intent: EditIntent, generation: Long)   // serial; drops stale generations
}
```

**Behavior to encode (and test):**
- Maintains `committedVoicePrefix`. On `VoicePartial(full)`: compute stable prefix (per Task 0.1
  finding — e.g. longest stable common prefix across recent partials, or up to last finalized
  boundary). If keyboard is NOT composing and no active selection → `sink.commitVoiceText(delta)`
  and advance prefix; put the remaining unstable tail to `sink.showUnstable(tail)`.
- If keyboard IS composing a word / selection active → buffer the stable delta; do NOT commit.
  On `KeyboardWordCommitted` / `SelectionChanged(collapsed)` → flush buffered delta.
- `VoiceFinal(text)` → flush everything, `showUnstable("")`, reset prefix.
- `NewInputSession(gen)` → bump generation, clear buffers/prefix, `showUnstable("")`.
- `submit(..., generation)` where `generation != current` → drop (stale result guard).

**Steps (TDD):**
- [ ] 1. Confirm test dir + JUnit setup from an existing test in the module (Task 0). 
- [ ] 2. Write failing tests: (a) partial with idle keyboard commits stable delta once, not
  re-committing the same prefix; (b) partial while `KeyboardComposingStarted` buffers, then
  `KeyboardWordCommitted` flushes; (c) stale generation dropped; (d) `VoiceFinal` flushes +
  clears unstable. Use a fake `EditSink` recording calls.
- [ ] 3. Run tests → FAIL.
- [ ] 4. Implement `TextEditCoordinator` minimally to pass.
- [ ] 5. Run tests → PASS.
- [ ] 6. Commit: `feat(voice): TextEditCoordinator serial edit reducer + tests`.

---

### Task 2: Headless voice session wired to the coordinator

**Files:** Modify `ActionInputTransactionIME.kt`, and the session host confirmed in Task 0.2
(likely `VoiceInputPersistentState` or a new small `HeadlessVoiceSession`).

- [ ] 1. Remove the suppression at `ActionInputTransactionIME.kt:80-84`. In simultaneous
  mode, `updatePartial`/`commit` route text to the `TextEditCoordinator` (as
  `VoicePartial`/`VoiceFinal`) instead of `setComposingText`. Non-simultaneous path unchanged.
- [ ] 2. Implement the headless session per Task 0.2: create `AudioRecognizer` with an
  `AudioRecognizerListener` whose callbacks only `submit` `EditIntent`s (no direct IC calls).
  Feed `recordingStarted`→ set `listening=true`; `partialResult`→ `VoicePartial`;
  `finished`→ `VoiceFinal` + `listening=false`; `cancelled`→ clear + `listening=false`.
- [ ] 3. Provide the `EditSink` impl that calls `ic.commitText` / suggestion-strip on the IME
  thread.
- [ ] 4. Build `assembleUnstableDebug` → SUCCESS. Commit:
  `feat(voice): headless dictation session via coordinator`.

Note: no unit-test harness for Android IC here; correctness of this glue is verified on-device
(Task 5). The pure logic it depends on is already tested in Task 1.

---

### Task 3: Windowless start path (no ActionWindow when toggle ON)

**Files:** Modify `VoiceInputAction.kt` (`windowImpl` fork at lines ~945-972).

- [ ] 1. When `VOICE_SIMULTANEOUS_TYPING` is ON, do NOT return a `VoiceInputBottomBarWindow`
  or `VoiceInputActionWindow`. Instead start the Task 2 headless session and keep the main
  keyboard shown (confirm the mechanism: an `Action` with `simplePressImpl` / a no-op window,
  per Task 0). Toggle OFF → original `VoiceInputActionWindow` unchanged.
- [ ] 2. Tapping mic again while listening → stop+finalize the session.
- [ ] 3. Build → SUCCESS. Commit: `feat(voice): windowless dictation start when toggle ON`.

---

### Task 4: Mic-key glow while listening

**Files:** Modify `ActionBar.kt` + the shared `listening` state (Task 0.4 / 0.2).

- [ ] 1. Expose `listening: State<Boolean>` from the session host; observe it where the action
  bar renders the voice item.
- [ ] 2. When `listening`, animate the mic item (pulse/glow tint) — reuse the
  `rememberInfiniteTransition` pulse pattern already in `VoiceInputAction.kt:566-575`.
- [ ] 3. Build → SUCCESS. Commit: `feat(voice): mic key glows while dictating`.

---

### Task 5: Unstable-tail display — RESOLVED = B (no extra UI)

User decision **B**: the not-yet-final voice tail is NOT shown; only stabilized words land in
the field. `EditSink.showUnstable` is a **no-op** in Step 1 — no suggestion-strip work. Kept
in the interface so Step 2 can add an optional preview without an API change. **No task.**

---

### Task 6: On-device verification

- [ ] 1. `assembleUnstableDebug`, install to device.
- [ ] 2. Toggle ON → open text field → dictate. Verify: **no bar**; keyboard fully visible +
  tappable; words appear **as spoken** (not dumped); mic key **glows**; tap mic stops+finalizes.
- [ ] 3. Type a word, then speak → voice text appends after the typed word commits (no clobber).
- [ ] 4. Toggle OFF → original full-screen voice unchanged.
- [ ] 5. Report results in Q&A channel. Do NOT claim success without on-device evidence.

---

## Self-review notes
- Spec coverage: no-bar (T3), live streaming stable chunks (T1+T2), mic glow (T4),
  keyboard-stays (T3), safe typing coexistence (T1 buffer/flush + T2 wiring), session guard
  (T1), toggle-OFF untouched (T3), unstable tail (T5). Covered.
- Testable core (coordinator) is isolated and unit-tested; Android glue verified on-device.
- Open dependency: Task 0 findings may adjust T1's stable-prefix algorithm and T2/T4 hooks —
  that is intended (investigation-gated), not a placeholder.
