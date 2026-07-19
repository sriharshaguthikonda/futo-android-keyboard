# WS4 (redo) Step 1 — Windowless Gboard-style dictation — Implementation Plan

> Design: [WS4-REDO-DICTATION-DESIGN.md](WS4-REDO-DICTATION-DESIGN.md) · Index: [PLAN.md](PLAN.md)
> Findings: [WS4-REDO-STEP1-FINDINGS.md](WS4-REDO-STEP1-FINDINGS.md) · Ceiling research:
> [../../Research/chatgpt_true_simultaneity_ceiling.md](../../Research/chatgpt_true_simultaneity_ceiling.md)
>
> **For the implementer (Codex):** Execute task-by-task, commit per task. The four unknowns
> are already investigated (Task 0 done). Follow the corrected contracts below exactly.

**Goal:** When "Simultaneous voice + typing" is ON, dictation runs with the normal keyboard
fully visible (no overlay bar), the mic key glows while listening, and voice text streams
**live, word-by-word, into the field** via a single-writer coordinator using a replaceable
committed voice tail — instead of the rejected bottom-bar window that buffered then dumped.

**Architecture (Step 1 subset of the ceiling design):**
Voice becomes a **headless** background session (no `ActionWindow`). All editor mutations go
through one serial `TextEditCoordinator`. Touch keeps Android's single composing region. Voice
keeps a **committed mutable tail** (`VOICE_MUTABLE`) it replaces on each Moonshine snapshot,
freezing a stable prefix (`VOICE_STABLE`) so the mutable range stays small. This yields live
in-field streaming **without** needing Moonshine's discarded `LineCompleted` boundary. Step 1
uses the SAFE typing-coexistence policy (freeze the voice tail on a keypress); the seamless
composition-lift/edit/restore + per-app profiles are Step 2.

**Tech Stack:** Kotlin, Android IME (FUTO/AOSP LatinIME), Jetpack Compose (action bar),
Gradle (`assembleUnstableDebug`), JUnit 4 local unit tests (`src/test`, see Task 1).

## Global Constraints (verbatim from spec)

- Android 7–11: editor has **ONE composing region + ONE selection**. Touch owns the composing
  region. Voice never calls `setComposingText()`; it commits/replaces ordinary text ranges.
- Gating: behind `VOICE_SIMULTANEOUS_TYPING`. **Toggle OFF path must stay byte-for-byte
  unchanged** (original `VoiceInputActionWindow` full-screen).
- Recognition callbacks (already marshalled to `Dispatchers.Main`) submit **intents only** to
  the coordinator; they never call `InputConnection` directly.
- Every voice result carries an input-session generation; drop stale-generation results.
- Do not touch unrelated dirty files (`.settings/…buildship…`, `voiceinput-shared/src/main/ml`).
- No scope-creep into the roadmap (voice commands, snippets, programmable actions).

## Corrected contracts (from Task 0 findings — do not deviate)

- **Moonshine partials are full running snapshots**, revisable (`MoonshineStreamingLocalBackend.kt:96-107`;
  `AudioRecognizer.kt:483-490`; `RecognizerView.kt:197-202`). Step 1 does NOT rely on
  `LineCompleted`; it replaces the `VOICE_MUTABLE` range with the new snapshot each time and
  freezes a stable prefix heuristically (longest prefix unchanged across the last N snapshots).
- **Headless recognizer is feasible** — `AudioRecognizer` (plain class, `AudioRecognizer.kt:110-116`,
  lifecycle `reset/start/finish/cancel` `:252-312`) runs on the injected IME lifecycle scope,
  no Compose required. Host it in `VoiceInputPersistentState` (`VoiceInputAction.kt:157-170`);
  extend its `cleanUp`/`close` to cancel the session.
- **windowImpl fork CANNOT do windowless** (`UixManager.kt:667-680` always takes `windowImpl`
  when non-null; simple-press path skips on-trigger persistent-state init `UixManager.kt:762-771`).
  → Task 2 adds an explicit headless dispatch (see there).
- **Session generation**: bump from `UixManager.inputStarted` (via `LatinIME.onStartInput`
  `LatinIME.kt:577-581`; `UixManager.kt:1655-1663`); stop the session from
  `UixManager.onInputFinishing` (`LatinIME.kt:593-604`; `UixManager.kt:1690-1697`). NOT
  `IMEManager.onStartInput` (too late).
- **Keystroke/selection interception**: observe touch composing at the shared
  `RichInputConnection.setComposingText`, and word-commit when `commitText`/`finishComposingText`
  clears nonempty tracked composing text (`RichInputConnection.java:324-375,655-668`); observe
  selection at the START of `IMEManager.onUpdateSelection` (`IMEManager.kt:285-301`), before its
  20 ms debounce. `ActionInputTransactionIME.onUpdateSelection` alone is too late.
- **Mic glow**: plumb `listening` through `ActionBar`→`ActionItems`/`PinnedActionItems`→
  `ActionItem` (`ActionBar.kt:496-532`) and `ActionItemSmall` (`ActionBar.kt:538-588`); gate on
  `action == VoiceInputAction`.
- **Unit-test path**: `src/test/java/org/futo/inputmethod/latin/uix/voice/` (JUnit 4 declared
  `build.gradle:416`; `tests/src` is androidTest instrumentation — do NOT put the pure test there).

## File structure

- New: `java/src/org/futo/inputmethod/latin/uix/voice/TextEditCoordinator.kt` — serial edit
  reducer with `VOICE_STABLE`/`VOICE_MUTABLE` tail (unit-tested).
- New test: `src/test/java/org/futo/inputmethod/latin/uix/voice/TextEditCoordinatorTest.kt`
  (create the `src/test` tree; confirm module gradle wiring in Task 1).
- New: `java/src/org/futo/inputmethod/latin/uix/voice/HeadlessVoiceSession.kt` — drives
  `AudioRecognizer`, owns `listening` state, feeds the coordinator. Hosted by
  `VoiceInputPersistentState`.
- Modify: `VoiceInputAction.kt` — headless dispatch when toggle ON; host session in persistent
  state; keep windowed path for toggle OFF.
- Modify: `UixManager.kt` — headless dispatch branch + session-generation bump/stop hooks.
- Modify: `ActionInputTransactionIME.kt` — remove partial suppression (`:80-84`); this
  transaction is no longer the voice writer in simultaneous mode (coordinator is).
- Modify: `RichInputConnection.java` / bridge — emit composing-start / word-commit signals to
  the coordinator.
- Modify: `ActionBar.kt` — mic-key glow from `listening`.

---

### Task 0: Investigation — DONE

See [WS4-REDO-STEP1-FINDINGS.md](WS4-REDO-STEP1-FINDINGS.md). Two blockers found and resolved
in this plan (mutable-tail dissolves the Moonshine-boundary blocker; Task 2 adds explicit
headless dispatch for the windowImpl blocker).

---

### Task 1: `TextEditCoordinator` (serial reducer + mutable voice tail) + unit tests

Pure/JVM-testable. Android specifics sit behind a tiny `EditSink`.

**Files:** Create `…/uix/voice/TextEditCoordinator.kt`; create `src/test` tree + test.

**Interfaces (Produces):**
```kotlin
interface EditSink {
    /** Replace the current voice tail range [start,end) in the field with [text]; returns
     *  the new tail end offset. Implemented via setSelection + commitText on the IME thread. */
    fun replaceVoiceTail(text: String)
    /** Freeze only the first [length] characters of the current tail, keeping the suffix
     *  replaceable. Implemented by advancing the tracked tail start; no field change. */
    fun freezeVoiceTailPrefix(length: Int)
    /** Freeze: the current tail becomes immutable committed text (no field change). */
    fun freezeVoiceTail()
}

sealed interface EditIntent {
    data class VoiceSnapshot(val fullHypothesis: String) : EditIntent
    data class VoiceFinal(val text: String) : EditIntent
    object KeyboardComposingStarted : EditIntent
    object KeyboardWordCommitted : EditIntent
    data class SelectionChanged(val start: Int, val end: Int, val userInitiated: Boolean) : EditIntent
    data class NewInputSession(val generation: Long) : EditIntent
}

class TextEditCoordinator(private val sink: EditSink) {
    fun submit(intent: EditIntent, generation: Long)   // serial; drops stale generations
}
```

**Behavior to encode + test:**
- Maintains `stablePrefix` (frozen) + `mutableTail` (current replaceable text) + a small ring
  of recent snapshots.
- `VoiceSnapshot(full)`: `newTail = full.removePrefix(stablePrefix)`; `sink.replaceVoiceTail(newTail)`.
  Advance `stablePrefix` to the longest prefix unchanged across the last N (=3) snapshots;
  when it grows, call `sink.freezeVoiceTailPrefix(newlyStableLength)` so the newly-stable
  prefix stays committed while only the suffix remains replaceable.
- On `KeyboardComposingStarted` OR `SelectionChanged(userInitiated=true, collapsed elsewhere)`:
  `sink.freezeVoiceTail()`, clear `mutableTail`; the next `VoiceSnapshot` starts a fresh tail
  at the new cursor. (SAFE Step-1 coexistence — no lift/restore yet.)
- `KeyboardWordCommitted`: no-op for the tail in Step 1 (typing already committed normally).
- `VoiceFinal(text)`: replace tail with final text, then `freezeVoiceTail()`, reset prefix.
- `NewInputSession(gen)`: bump generation, freeze+clear.
- `submit(_, generation)` with `generation != current` → drop.

**Steps (TDD):**
- [ ] 1. Create root `src/test` tree; wire a minimal JUnit4 local test to run via
  `./gradlew testUnstableDebugUnitTest` (confirmed from the live root app Gradle tasks).
- [ ] 2. Failing tests with a fake `EditSink` recording calls: (a) three qualifying growing
  snapshots → tail replaced on each, stable prefix advances on the third, and a following
  snapshot does not re-emit frozen text; (b) snapshot then
  `KeyboardComposingStarted` → `freezeVoiceTail` called, next snapshot starts fresh tail;
  (c) stale generation dropped; (d) `VoiceFinal` freezes + resets.
- [ ] 3. Run → FAIL.
- [ ] 4. Implement minimally.
- [ ] 5. Run → PASS.
- [ ] 6. Commit: `feat(voice): TextEditCoordinator with mutable voice tail + tests`.

---

### Task 2: Headless voice session + explicit windowless dispatch

**Files:** Create `HeadlessVoiceSession.kt`; modify `VoiceInputAction.kt`, `UixManager.kt`,
`ActionInputTransactionIME.kt`.

- [ ] 1. `HeadlessVoiceSession`: constructs `AudioRecognizer` with the IME lifecycle scope +
  `ModelManager` from `VoiceInputPersistentState`; exposes `listening: State<Boolean>` and
  `start()/stop()/cancel()`. Listener callbacks submit `EditIntent`s only:
  `recordingStarted`→`listening=true`; `partialResult`→`VoiceSnapshot`; `finished`→`VoiceFinal`
  +`listening=false`; `cancelled`→freeze+`listening=false`.
- [ ] 2. Host it in `VoiceInputPersistentState`; extend `cleanUp`/`close` to cancel it.
- [ ] 3. `EditSink` impl: `replaceVoiceTail` = `setSelection(tailStart,tailEnd)` +
  `commitText(text,1)` on the IME `RichInputConnection`, tracking `tailStart`; `freezeVoiceTail`
  = drop tracking. Wrap in `beginBatchEdit/endBatchEdit`.
- [ ] 4. Windowless dispatch (fixes Blocker 2): in `UixManager` action dispatch
  (`UixManager.kt:667-680`), when the triggered action is `VoiceInputAction` AND
  `VOICE_SIMULTANEOUS_TYPING` is ON: initialize on-trigger persistent state (as
  `enterActionWindowView` does), start the headless session, and DO NOT enter the action-window
  view (keyboard stays). Toggle OFF → unchanged windowed path.
- [ ] 5. Remove suppression at `ActionInputTransactionIME.kt:80-84` (dead once coordinator owns
  voice writing in simultaneous mode). Keep the non-simultaneous composing path intact.
- [ ] 6. Session generation: bump in `UixManager.inputStarted`, stop session in
  `UixManager.onInputFinishing`.
- [ ] 7. Build `assembleUnstableDebug` → SUCCESS. Commit:
  `feat(voice): headless windowless dictation session + dispatch`.

---

### Task 3: Keystroke / selection signals to the coordinator

**Files:** Modify `RichInputConnection.java` (+ its `IMEHelper`/provider bridge), `IMEManager.kt`.

- [ ] 1. In `RichInputConnection.setComposingText`, signal `KeyboardComposingStarted` to the
  active coordinator (via the bridge, guarded so it's a no-op when no headless session is live).
- [ ] 2. In `commitText`/`finishComposingText`, when they clear nonempty tracked composing text,
  signal `KeyboardWordCommitted`.
- [ ] 3. At the START of `IMEManager.onUpdateSelection` (before the 20 ms debounce), forward
  `SelectionChanged(start,end,userInitiated=<not IME-expected>)` to the coordinator.
- [ ] 4. Build → SUCCESS. Commit: `feat(voice): route touch composing/selection to coordinator`.

---

### Task 4: Mic-key glow while listening

**Files:** Modify `ActionBar.kt`; thread `listening` from `VoiceInputPersistentState`/`UixManager`.

- [ ] 1. Add a `listening: State<Boolean>` param threaded `ActionBar`→`ActionItems`/
  `PinnedActionItems`→`ActionItem`/`ActionItemSmall`.
- [ ] 2. When `listening && action == VoiceInputAction`, drive a pulse/tint on the mic item —
  reuse the `rememberInfiniteTransition` pulse from `VoiceInputAction.kt:566-575`. Applied at the
  existing tint sites (`ActionBar.kt:527-532`, `:583-588`).
- [ ] 3. Build → SUCCESS. Commit: `feat(voice): mic key glows while dictating`.

---

### Task 5: Unstable-tail display — RESOLVED = B (no extra UI)

User decision **B**: only the in-field mutable tail is shown; no suggestion strip. `EditSink`
has no `showUnstable`. **No task.** (Step 2 may add an optional preview.)

---

### Task 6: On-device verification (phone `10BF191Z51001DC`)

- [ ] 1. `assembleUnstableDebug`, install.
- [ ] 2. Toggle ON → text field → dictate. Verify: **no bar**; keyboard fully visible +
  tappable; words appear **as spoken** into the field (mutable tail updates live), not dumped;
  mic key **glows**; tap mic stops+finalizes.
- [ ] 3. Type a word mid-dictation → voice tail freezes, typed word lands, voice resumes with a
  fresh tail (no clobber).
- [ ] 4. Change focus / rotate → no stale voice text leaks (session-generation guard).
- [ ] 5. Toggle OFF → original full-screen voice unchanged.
- [ ] 6. Report results in Q&A. No success claim without on-device evidence
  (`adb logcat -s VoiceInputAction AudioRecognizer TextEditCoordinator`).

---

## Step 2 (next milestone — from the ceiling research, do NOT build in Step 1)
FULL mode: composition lift/edit/restore so typing keeps autocorrect/suggestions while voice
edits its range immediately; range ledger with anchor affinity + context fingerprints;
per-app capability profile (FULL / COMMIT_ONLY / CONSERVATIVE) with dynamic downgrade;
COMMIT_ONLY shadow-`WordComposer` fallback for WebViews; boundary-space handling; gesture.
Reference: [chatgpt_true_simultaneity_ceiling.md](../../Research/chatgpt_true_simultaneity_ceiling.md).

## Self-review
- Blockers resolved: mutable-tail (Blocker 1), explicit headless dispatch (Blocker 2).
- Corrected hooks applied (session-gen, RichInputConnection, onUpdateSelection, test path).
- Testable core (coordinator) isolated + unit-tested; Android glue verified on-device.
- Spec coverage: no-bar (T2), live streaming (T1+T2), mic glow (T4), keyboard-stays (T2),
  safe typing coexistence (T1 freeze + T3 signals), session guard (T1+T2), toggle-OFF
  untouched (T2). Covered.
