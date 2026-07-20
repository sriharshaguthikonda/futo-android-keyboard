# WS4-redo Step 1 — Task 3 report: touch composing/word-commit/selection → voice coordinator

Branch: `feature/moonshine-streaming`. Scope: Task 3 only (no mic glow, no coordinator changes).

## Files changed

1. `java/src/org/futo/inputmethod/latin/uix/voice/HeadlessVoiceSession.kt`
   - Added public touch-coexistence hooks: `onKeyboardComposingStarted()`,
     `onKeyboardWordCommitted()`, `onSelectionChanged(oldSelStart, oldSelEnd, newSelStart, newSelEnd)`.
     All submit through the same `onMain` (`Dispatchers.Main.immediate`) path the recognizer
     callbacks use, stamped with the session's CURRENT `generation`, so intent ordering into the
     coordinator stays serial.
   - Added `pendingVoiceEditDeltas` (main-thread-only `ArrayDeque<Int>`, capped at 64) +
     `isVoiceCausedSelection(...)` to compute `userInitiated` (see below).
   - `VoiceTailSink` now takes an `onEditApplied(delta)` callback and reports the net cursor delta
     of each applied tail write. The InputConnection call sequence
     (`beginBatchEdit`/`deleteSurroundingText`/`commitText`/`endBatchEdit`) is **unchanged** —
     Task 2's field-write logic is untouched; only a notification was added after the batch edit.

2. `java/src/org/futo/inputmethod/latin/uix/UixManager.kt`
   - New accessor `getListeningVoiceSession(): HeadlessVoiceSession?` — returns
     `(persistentStates[VoiceInputAction] as? VoiceInputPersistentState)?.headlessSession`
     **only while `listening.value == true`**, else null. This is the single gate for every hook.

3. `java/src/org/futo/inputmethod/engine/IMEHelper.kt`
   - New `getListeningVoiceSession()` delegating to `latinIME.uixManager.getListeningVoiceSession()`.
     This is the bridge `RichInputConnection` uses (see below).

4. `java/src/org/futo/inputmethod/latin/RichInputConnection.java`
   - Private helper `getListeningVoiceSession()`: `mConnectionProvider instanceof IMEHelper`
     → `((IMEHelper) mConnectionProvider).getListeningVoiceSession()`, else null.
   - `setComposingText(...)`: signals `onKeyboardComposingStarted()` (guarded).
   - `commitText(...)` and `finishComposingText()`: when the tracked `mComposingText` is nonempty
     (i.e. this call clears a live touch composition), signal `onKeyboardWordCommitted()` (guarded).

5. `java/src/org/futo/inputmethod/engine/IMEManager.kt`
   - At the very START of `onUpdateSelection(...)`, BEFORE the 20 ms debounce:
     `service.uixManager.getListeningVoiceSession()?.onSelectionChanged(oldSelStart, oldSelEnd, newSelStart, newSelEnd)`.
     `ensureUpdateSelectionFinished()` needs no change — it only flushes the debounced dispatch of a
     selection that already passed through `onUpdateSelection` (already forwarded at receipt).

## No-op-when-not-listening guard (most important constraint)

Every hook goes through exactly one gate: `UixManager.getListeningVoiceSession()`, which returns
null unless a `HeadlessVoiceSession` exists AND its `listening` state is true. All four call sites
are of the form `val s = gate() ?: <do nothing>; s.signal(...)` — the guard fires before any work.
When voice is idle the per-keystroke cost is: (RichInputConnection) one `mComposingText.length()`
check and/or one `instanceof` + a HashMap lookup + cast + `State` read returning null; (IMEManager)
the same lookup returning null. No allocation, no behavioral branch taken — normal typing and
selection handling are unchanged by construction.

## Bridge path from RichInputConnection

`RichInputConnection` already holds `mConnectionProvider: InputMethodConnectionProvider`, whose
production implementation is `IMEHelper` (created in `InputLogic.java:188` with the `imeHelper`).
The hook downcasts via `instanceof IMEHelper` → `IMEHelper.getListeningVoiceSession()` →
`UixManager`. No new singleton.

**Why a downcast instead of a new interface method:** the module compiles Kotlin 2.1.0 without
`-Xjvm-default=all`, so a Kotlin interface method with a default body is NOT a JVM default method.
The Java instrumentation mock `RichInputConnectionAndTextRangeTests.MockInputMethodService`
(tests/src) implements `InputMethodConnectionProvider` directly and would stop compiling. The
`instanceof` bridge keeps the interface and the test mock untouched; non-IMEHelper providers
(test mocks) simply have no voice session (null → no-op), which is also semantically correct.

## How userInitiated is determined

`userInitiated` = "this selection change was NOT caused by the IME's own expected edit". Two IME
edit sources exist during dictation:

- **Touch edits** go through `RichInputConnection`, but during a *live* dictation the SAFE policy
  wants those to freeze the tail anyway (and the composing/commit hooks fire for them first), so
  misclassifying them as user-initiated is harmless-by-design.
- **Voice's own tail writes** go through the raw `InputConnection` (VoiceTailSink), so
  `RichInputConnection.mExpectedSelStart`/`isBelatedExpectedUpdate` can NOT vouch for them — its
  expected values simply don't include voice's edits. Relying on it (or passing a blanket
  conservative `userInitiated = true`) would be **wrong, not just conservative**: every voice
  snapshot moves the cursor → the resulting selection callback would freeze/reset the tail →
  the next snapshot would re-commit the whole hypothesis → duplicated text in the field.

Therefore the voice session tracks its own expected edits: `VoiceTailSink` reports the net cursor
delta of every applied tail write into `pendingVoiceEditDeltas`. On each raw selection callback:

1. `new == old` (no movement; some editors re-send selection): treated as non-user (nothing to
   freeze either way).
2. Non-collapsed old or new selection: never produced by tail writes → `userInitiated = true`,
   queue cleared (resync).
3. Collapsed→collapsed: the observed delta (`newSelEnd - oldSelEnd`) is matched against prefix
   sums of the queued voice deltas (editors may coalesce several batch edits into one callback).
   Match → voice-caused (`userInitiated = false`), matched deltas consumed. No match →
   `userInitiated = true`, queue cleared.

Direction of error is safe: anything unmatched is treated as user-initiated (tail freezes — the
SAFE default); only provably-our-own writes suppress the freeze. Coincidental match (user tap
landing exactly at the expected voice delta within the same callback window) is the residual risk;
typing is still covered by the composing/commit hooks, and Step 2's absolute range ledger removes
this class entirely.

**Signature deviation (documented):** the plan sketched
`onSelectionChanged(start, end, userInitiated)` with the IME layer deciding `userInitiated`. The
IME layer has no pre-debounce signal that accounts for voice's raw-IC writes (see above), so the
session computes `userInitiated` itself and its public hook takes
`(oldSelStart, oldSelEnd, newSelStart, newSelEnd)`. The intent submitted to the coordinator is
exactly the specified `SelectionChanged(start, end, userInitiated)` — `TextEditCoordinator.kt` and
its tests are untouched.

## Verification

- Build: `./gradlew assembleUnstableDebug` → BUILD SUCCESSFUL (see commit).
- Tests: `./gradlew testUnstableDebugUnitTest --tests "org.futo.inputmethod.latin.uix.voice.TextEditCoordinatorTest"` → passed.

## Concerns / not visible in the diff

1. Delta-matching heuristic (above) is the Step-1 stopgap for voice-caused selection detection;
   Step 2's range ledger with anchor affinity supersedes it.
2. `setComposingText` signals on EVERY composing update (each keystroke of a word), not only the
   first — the coordinator's `freezeAndReset` is idempotent, so this is noise-free but slightly
   chatty; kept for simplicity per plan wording ("in setComposingText, signal ...").
3. Editors that never deliver `onUpdateSelection` leave stale deltas in the queue; capped at 64
   and cleared on any unmatched/non-collapsed callback and (implicitly) irrelevant across
   `NewInputSession` since the tail is frozen there anyway.
