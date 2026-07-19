# WS4 (redo) — Gboard-style windowless voice dictation (design)

> Supersedes WS4a (bottom-bar pill) + WS4b in [PLAN.md](PLAN.md). Numbered WS4-redo because
> WS5 is the AI-Reply workstream. Step 1 plan: [WS4-REDO-STEP1-PLAN.md](WS4-REDO-STEP1-PLAN.md).

Date: 2026-07-20
Branch: feature/moonshine-streaming
Supersedes: WS4a/WS4b "simultaneous voice + typing" (bottom-bar approach — rejected by user)

## Problem

User tested WS4b and rejected it. Two concrete failures, both caused by the WS4a/WS4b
implementation itself:

1. **Bulky voice bar on top of the keyboard.** The entire voice feature is an
   `ActionWindow` overlay (`VoiceInputActionWindow` / `VoiceInputBottomBarWindow`). Even
   the "bottom bar" is a window. When it opens, the keyboard's action-bar row, shortcuts,
   and settings buttons are replaced by the window's own chrome. Gboard has no such bar.

2. **Buffers whole utterance, then dumps all text at once.** In simultaneous mode,
   `ActionInputTransactionIME.updatePartial()` (java/.../engine/general/ActionInputTransactionIME.kt:80-84)
   deliberately discards live partials:

   ```kotlin
   if (helper.context.getSetting(VOICE_SIMULTANEOUS_TYPING)) {
       partialText = text
       // ponytail: v1 suppresses live composing partials; add a multi-writer merge later.
       return
   }
   ```

   So text only reaches the field at `commit()` (called once at `finished()`), i.e. one dump
   at the end.

Additional user requirements:
- Mic key on the keyboard must **glow/pulse while listening** (Gboard behavior).
- User must be able to **type while speaking, in the same text field.**

## Target behavior (Gboard, confirmed via Google docs + ChatGPT research 2026-07-19)

- Normal keyboard keys stay **fully visible and usable**; no overlay bar.
- Mic key **glows** while listening.
- Dictated text streams **live** into the field while speaking; earlier words may revise.
- User can **tap keys / move cursor / edit while the mic is on**, without stopping.
- Stop = tap the glowing mic again (also: close keyboard / VAD auto-stop).

## Android platform constraint (from research: docs/Research/typing while listening..txt)

Android 7–11 gives an editor **exactly one selection and one composing region.** Voice and
touch typing **cannot each own a separate live composing span.** `setComposingText()` always
replaces whatever span is currently active, and `commitText()` removes+replaces the current
composing text. This is the root reason the WS4b "stream partials via composing text in
simultaneous mode" idea is unsafe, and why the previous agent crudely suppressed partials
instead (killing streaming entirely → dump at end).

**Consequence for the design:** the composing region belongs to touch typing. Voice must not
fight it.

## Approach — windowless background dictation + single-writer coordinator

Voice stops being an `ActionWindow` (when the toggle is ON) and becomes a **headless
background session** whose output flows through a **single-writer edit coordinator**:

- Tapping the mic starts an `AudioRecognizer` session directly. **No window opens.** The
  main keyboard stays mounted exactly as-is.
- All `InputConnection` mutations (keystrokes AND voice) are serialized through one
  coordinator on the IME thread. **The recognition callback never calls `InputConnection`
  directly.**
- Voice emits two kinds of output:
  - **Stable chunks** → committed into the field via `commitText` (this is the live,
    progressive text the user sees appear as they speak).
  - **Unstable tail** → NOT shown anywhere (user decision **B** — no extra UI). Only
    stabilized words appear, and they appear in the field. No suggestion-strip preview in
    Step 1. (`EditSink.showUnstable` stays a no-op hook so Step 2 could add a preview later
    without an interface change.)
- **Touch owns the composing region.** While the keyboard is composing a typed word (or a
  selection is active), voice stable-chunks are briefly buffered and flushed at the next
  natural commit boundary (space, punctuation, enter, suggestion chosen, cursor moved).
- A **session-generation** counter (bumped on `onStartInput`) drops voice results that
  arrive after the input field changed.
- A shared observable "listening" state drives the mic key's glow animation in `ActionBar`.
- Tapping the mic again (or VAD auto-stop) finalizes and ends the session.

### Voice-only path (no concurrent typing) is unchanged
When voice is the sole writer (toggle OFF, or ON but the user isn't typing), the existing
Moonshine `setComposingText` live-partial path already works and stays. The coordinator only
changes behavior when touch and voice would otherwise collide.

### Why this over alternatives
- **Two live composing spans (WS4b idea)** — impossible on Android 7–11 (one composing
  region). Rejected — this is the actual bug.
- **Zero-height/invisible ActionWindow** — still a window lifecycle replacing the action
  bar. Rejected.
- **Fast-interleave (commit every keystroke, no composing while mic on)** — simpler conflict
  handling but loses autocorrect/gesture/suggestion behavior while dictating. Kept as a
  possible opt-in mode, not the default (per research).

### Feasibility (verified during design)
- **Headless recognizer: LOW risk.** `RecognizerView.Content()` is `@Composable`, but the
  recorder is a plain `AudioRecognizer` (RecognizerView.kt:257) driven by
  `start()`/`finish()`/`cancel()` + an `AudioRecognizerListener`. Recording does not require
  composition; drive `AudioRecognizer` (or a UI-less RecognizerView) headless.
- **Mic-key glow: MEDIUM risk.** Action keys render in `ActionBar.kt`; add a listening-state
  tint/pulse to the mic item. Exact composable hook confirmed in the implementation plan.
- **Stable/unstable split from Moonshine: TO CONFIRM in plan.** Need to check what
  `partialResult` emits (running full hypothesis vs incremental) to compute the stable
  prefix delta. Codex to confirm during implementation.

## Gating

Behind the existing `VOICE_SIMULTANEOUS_TYPING` toggle.
- **Toggle ON** → new windowless Gboard-style dictation.
- **Toggle OFF** → original full-screen `VoiceInputActionWindow`, byte-for-byte unchanged.

This preserves an escape hatch and keeps the FUTO upstream default path intact.

## Two-step delivery (user-approved)

### Step 1 — kill the bar + live streaming + mic glow (this delivery)
Fixes complaints #1, #2, #4 and makes the keyboard stay visible/tappable. Concurrent typing
uses the SAFE policy (voice defers while a typed word is composing); the seamless merge is
Step 2.

Scope:
1. When toggle ON, mic press starts a **headless** dictation session; no ActionWindow.
2. Keyboard stays mounted; all keys, action bar, shortcuts remain.
3. Voice text **streams live** into the field by committing **stable chunks** via
   `commitText` through the single-writer coordinator — NOT via `setComposingText` (that
   belongs to touch). This replaces the suppression at ActionInputTransactionIME.kt:80-84.
4. Mic key in `ActionBar` **glows/pulses** while `listening == true`.
5. Tap mic again → finalize remaining stable text and end session.
6. Safe typing coexistence: if the keyboard is composing a typed word / has a selection,
   voice stable chunks buffer and flush at the next natural commit boundary. Insertion may
   pause briefly during active typing (acceptable for Step 1).
7. Session-generation guard: drop voice results after the input field changes.
8. Toggle OFF path untouched.

Verify on device before Step 2.

### Step 2 — true concurrent type-while-dictating (next delivery)
FULL mode from the ceiling research: composition **lift/edit/restore** (touch keeps autocorrect
+ suggestions while voice edits its range immediately), a **range ledger** with anchor affinity
+ context fingerprints, a **per-app capability profile** (FULL / COMMIT_ONLY / CONSERVATIVE)
with dynamic downgrade, and a COMMIT_ONLY shadow-`WordComposer` fallback for WebViews. Full
detail: [../../Research/chatgpt_true_simultaneity_ceiling.md](../../Research/chatgpt_true_simultaneity_ceiling.md).
This is the real Android 7–11 ceiling — no second cursor/composing-region exists for any
keyboard.

## Files in scope (Step 1)

- `java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt` — windowless start
  path when toggle ON; bypass `VoiceInputBottomBarWindow` for that path.
- `java/src/org/futo/inputmethod/engine/general/ActionInputTransactionIME.kt` — route stable
  voice text through commit (coordinator), stop suppressing; keep voice-only composing path
  for the non-typing case.
- Single-writer edit coordinator — new small unit (`TextEditCoordinator`) serializing
  keystroke + voice mutations on the IME thread. Location confirmed in plan.
- `java/src/org/futo/inputmethod/latin/uix/ActionBar.kt` — mic-key listening glow.
- Shared listening state (likely `VoiceInputPersistentState` or `UixManager`).
- (No suggestion-strip work — user decision B: unstable tail not shown.)

## Out of scope
- Groq/non-streaming backends: emit no incremental partials, so they naturally land at
  commit; no regression, no special handling in Step 1.
- Voice command phrases ("delete last sentence", "send", etc.), snippets, programmable
  actions, clipboard upgrades — all in the long-term roadmap
  (docs/Research/chatgpt_input_roadmap.txt), NOT this fix.

## Success criteria (Step 1, on-device)
- Start dictation → **no bar appears**; keyboard fully visible and tappable.
- Words appear in the field **as you speak**, not dumped at the end.
- Mic key **visibly glows** while listening.
- Tap mic → dictation stops, text finalized.
- Toggle OFF → original full-screen voice unchanged.
