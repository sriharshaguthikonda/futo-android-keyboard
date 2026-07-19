# WS4 redo Step 1 — investigation findings

Date: 2026-07-20  
Scope: investigation only; no source edits, Git commands, builds, or tests.

## Verdict

**STOP before Task 1. The current plan is not implementable as written.** Two contracts are
missing:

1. Moonshine exposes finalized line boundaries internally but discards them before
   `partialResult(String)`. A consumer cannot reliably distinguish stable text from a revisable
   tail.
2. `VoiceInputAction` cannot dynamically choose windowed versus windowless activation through its
   current `windowImpl` fork. The dispatcher always takes `windowImpl` when it is non-null, and the
   simple-press path does not initialize on-trigger persistent state.

The headless recognizer and mic-key rendering are otherwise feasible. The plan must first revise
the recognizer callback contract and the action activation/state-hosting path.

## 1. Moonshine partial semantics

**Finding: `partialResult(text)` receives the entire running transcript, not an incremental
delta. It is revisable.**

- `MoonshineStreamingLocalBackend` stores every line in a `LinkedHashMap` keyed by line ID
  (`MoonshineStreamingLocalBackend.kt:36-40`). `LineStarted`, `LineUpdated`,
  `LineTextChanged`, and `LineCompleted` all replace the stored text for that ID
  (`MoonshineStreamingLocalBackend.kt:70-94`).
- After every event, it rebuilds the transcript by joining all nonblank line values and emits the
  rebuilt string when it differs from the previous emission
  (`MoonshineStreamingLocalBackend.kt:96-107,166-170`). Therefore each callback is a full snapshot
  of the running hypothesis.
- The actual finalized-segment boundary is `TranscriptEvent.LineCompleted`
  (`MoonshineStreamingLocalBackend.kt:86-89`). However, the adapter emits the same plain `String`
  callback for all four line-event types, so completion metadata is lost
  (`MoonshineStreamingLocalBackend.kt:25-28,70-107`).
- `AudioRecognizer` forwards that snapshot, normalized, as another plain string
  (`AudioRecognizer.kt:483-490`). `RecognizerView` forwards it unchanged
  (`RecognizerView.kt:197-202`).
- Stopping the stream calls `transcriber.stop()` and returns the rebuilt complete transcript
  (`MoonshineStreamingLocalBackend.kt:53-60`); `AudioRecognizer` later delivers that as the final
  `finished` result (`AudioRecognizer.kt:520-528,1012-1017`).

**Consequence:** longest-common-prefix across repeated partial snapshots would only be a heuristic.
A prefix seen in consecutive revisions is not guaranteed final and may still change in a later
`LineUpdated`/`LineTextChanged`. The Step 1 promise to commit only stable chunks cannot be met
correctly from `partialResult(String)`.

**Required plan correction:** preserve the line-event type (or separately emit the concatenated
completed-lines prefix) through `MoonshineStreamingLocalBackend` and `AudioRecognizerListener`.
Only text covered by `LineCompleted` is a reliable live commit boundary. Without that contract,
the only correct option is final-only commit, which recreates the reported end-of-utterance dump.

## 2. Headless recognizer lifecycle

**Finding: headless operation is feasible; Compose does not own recognition.**

- `AudioRecognizer` is a plain class whose constructor takes `Context`,
  `LifecycleCoroutineScope`, `ModelManager`, listener, and settings
  (`AudioRecognizer.kt:110-116`). Its public lifecycle is `reset`, `start`, `finish`, and `cancel`
  (`AudioRecognizer.kt:252-312`).
- Recording/model jobs and main-thread callback delivery use the injected lifecycle scope
  (`AudioRecognizer.kt:486-490,802-812,871-875,1012-1017,1033-1037`). No composable is required.
- `RecognizerView` merely adapts listeners, constructs `AudioRecognizer` with the supplied scope,
  and delegates those lifecycle calls (`RecognizerView.kt:169-202,257-280`). The existing action
  window already constructs and starts it from a lifecycle-scope job before UI composition is
  relevant (`VoiceInputAction.kt:243-267`).
- `VoiceInputPersistentState` is the natural host because it already owns the shared
  `ModelManager` and its `manager` supplies the IME lifecycle scope
  (`VoiceInputAction.kt:157-160`; `Action.kt:54-58`).

**Lifecycle requirement:** the persistent state must own and cancel/reset the headless recognizer.
Its current `cleanUp`/`close` only clean the model manager and unregister the dictionary observer
(`VoiceInputAction.kt:162-169`). Session generation should be bumped from
`UixManager.inputStarted`, which is called by `LatinIME.onStartInput`
(`LatinIME.kt:577-581`; `UixManager.kt:1655-1663`), and the session must stop from
`UixManager.onInputFinishing` (`LatinIME.kt:593-604`; `UixManager.kt:1690-1697`). Using only
`IMEManager.onStartInput` is insufficient because it is invoked from `onStartInputView`, not the
earlier input-session callback (`LatinIME.kt:584-587`; `IMEManager.kt:123-128`).

## 3. Keystroke/composition interception

**Finding: there is no existing coordinator subscription. The complete touch-only interception
point is `RichInputConnection`, plus the raw selection callback in `IMEManager`.**

- General keyboard events enter `GeneralIME.onEventInternal`, which dispatches keypress, generated
  text, and suggestion-pick events into `InputLogic`, then flushes the buffered connection
  (`GeneralIME.kt:287-344`). Gesture input has separate start/update/end/cancel entry points
  (`GeneralIME.kt:454-470`), so observing only `onEventInternal` would miss part of touch input.
- `InputLogic` owns one `RichInputConnection` created from `IMEHelper`
  (`InputLogic.java:101-105,182-192`). All touch composing writes converge on
  `RichInputConnection.setComposingText`; all touch commits clear its tracked composing text in
  `commitText` or `finishComposingText` (`RichInputConnection.java:324-375,655-668`). This is more
  complete than instrumenting individual separator/suggestion callers and, unlike voice, it is the
  connection used specifically by `InputLogic`.
- At the higher level, a typed/autocorrected word normally converges through `commitTyped` or
  `commitCurrentAutoCorrection`, then `commitChosenWord`
  (`InputLogic.java:2591-2655,2665-2719`). These lines confirm the semantics, but they are not the
  safest sole hook because other input paths call the connection directly.
- Cursor, selection, and editor composing-span updates enter `IMEManager.onUpdateSelection`
  (`IMEManager.kt:277-305`). The existing fan-out to `ActionInputTransactionIME` includes
  `newSelStart`, `newSelEnd`, `composingSpanStart`, and `composingSpanEnd`
  (`IMEManager.kt:291-301`; `ActionInputTransactionIME.kt:34-47`). A noncollapsed selection is
  `newSelStart != newSelEnd`; an editor composing span exists when its start/end are valid.

**Required hooks:**

1. Route touch `setComposingText` to `KeyboardComposingStarted`, and route a
   `commitText`/`finishComposingText` that clears nonempty tracked composing text to
   `KeyboardWordCommitted`, from the shared `RichInputConnection` methods through its existing
   `InputMethodConnectionProvider`/`IMEHelper` bridge.
2. Route selection changes to the coordinator at the start of
   `IMEManager.onUpdateSelection`, before its current 20 ms debounce
   (`IMEManager.kt:285-301`). Subscribing only in `ActionInputTransactionIME.onUpdateSelection` is
   too late to be a collision guard because that callback is delivered after the delay.

Both routes already execute in the IME/main-thread flow; recognition callbacks are also marshalled
to `Dispatchers.Main` (`AudioRecognizer.kt:486-490,1012-1017`). This provides the required serial
ordering without a second writer thread.

## 4. Mic-key rendering

**Finding: feasible without restructuring the action bar, but the state must be plumbed to both
existing icon composables.**

- The voice action is the singleton `VoiceInputAction` with `mic_fill`
  (`VoiceInputAction.kt:945-950`) and is registered as `voice_input`
  (`Registry.kt:22-32`). It is the default pinned action (`Registry.kt:268-280`).
- Expanded/favorite icons render in `ActionItem`; the icon tint is selected at
  `ActionBar.kt:496-513` and applied at `ActionBar.kt:527-532`.
- The normal pinned mic renders in `ActionItemSmall`; its colors/border are selected at
  `ActionBar.kt:538-573` and applied at `ActionBar.kt:583-588`. `PinnedActionItems` passes the
  actual `Action` instance through unchanged (`ActionBar.kt:772-786`).

A per-item check for `action == VoiceInputAction` can therefore combine `listening` with the
existing active-color/border calculation and drive a pulse/tint in both `ActionItem` and
`ActionItemSmall`. The bar layout and action registry do not need restructuring. `ActionBar`
currently receives no listening state, so an observable state owned by `UixManager` or
`VoiceInputPersistentState` must be passed through `ActionBar`/`ActionItems`/`PinnedActionItems`
(`ActionBar.kt:593-647,804-817,839-875,907`).

## Additional activation blocker

The Task 3 instruction to fork inside `VoiceInputAction.windowImpl` cannot produce a headless
session:

- `VoiceInputAction` currently has `simplePressImpl = null` and a non-null `windowImpl`
  (`VoiceInputAction.kt:945-971`).
- `UixManager` always enters the action-window path when `windowImpl` is non-null; it considers
  `simplePressImpl` only otherwise (`UixManager.kt:667-680`). Returning a tiny/no-op window would
  still run the action-window lifecycle and contradict the no-window requirement.
- On-trigger persistent state is initialized only by `enterActionWindowView`
  (`UixManager.kt:762-771`). The simple-press branch does not initialize it, while `Action` defaults
  persistent-state initialization to `OnActionTrigger` (`Action.kt:189-204`).

Task 3 therefore needs an explicit dispatch design before implementation: either a dedicated
headless action selected before window dispatch, or a dispatcher change that initializes state and
chooses the path from the setting. The toggle-OFF path can then continue to instantiate the
existing `VoiceInputActionWindow` unchanged.

## Test-layout note

The plan's proposed `java/tests/...` path does not exist. Existing tests under `tests/src` are wired
as `androidTest` instrumentation sources (`build.gradle:301-334`). JUnit 4 is also declared for
local tests (`build.gradle:416`), but there is currently no `src/test` tree. A pure JVM coordinator
test should use the standard local-test path `src/test/java/org/futo/inputmethod/latin/uix/voice/`
unless the plan intentionally chooses instrumentation tests.
