# WS4b — Fix simultaneous voice+typing bugs (found by audit of a0520d210)

Repo: FUTO LatinIME fork (Kotlin Android IME). Branch `feature/moonshine-streaming`.
An experimental "simultaneous voice + typing" feature was added behind a **default-OFF**
boolean setting `VOICE_SIMULTANEOUS_TYPING`.

## Hard constraints for the implementer
- Every fix MUST stay gated behind `VOICE_SIMULTANEOUS_TYPING`. When it is false, behavior must be
  byte-for-byte identical to today (same calls, same order). No regression to normal voice/typing.
- Minimal diffs. Kotlin. Must compile. Do NOT run gradle (the orchestrator builds).
- Do NOT touch `Q and A.qanda`, `.claude/`, or any plan/status/docs files. Edit ONLY code files.
- Read the setting via the SAME synchronous accessor already present in each file from commit
  a0520d210 (e.g. `helper.context.getSetting(VOICE_SIMULTANEOUS_TYPING)` in
  `ActionInputTransactionIME.kt`; `context.getSetting(...)` / `service.getSetting(...)` elsewhere).

## BUG 1 (HIGH) — dictation overwrites the word the user is mid-typing
File: `java/src/org/futo/inputmethod/engine/general/ActionInputTransactionIME.kt`, `commit(text)` ~lines 94-104.
Symptom: on Android 7-11 the voice IME and typing IME share the same raw `InputConnection`. `commit(text)`
calls `ic.commitText(...)` without resolving the keyboard's active composing span, so finalizing dictation
can REPLACE a word the user is currently typing.
Fix: when `VOICE_SIMULTANEOUS_TYPING` is true, BEFORE `ic.commitText(text)` call `ic?.finishComposingText()`
to finalize the keyboard's active word first. When false, `commit()` stays exactly as today.
Add a short `// ponytail:` comment.

## BUG 2 (MEDIUM) — cancel() inserts an unfinished suppressed partial
File: same file, `cancel()` ~lines 106-114 (the branch that commits the latest stored `partialText`).
Symptom: in simultaneous mode live partials are intentionally suppressed (stored, not written). But
`cancel()` commits the latest stored `partialText`, so closing/cancelling after "hello wor" inserts
"hello wor" — violating finalized-text-only behavior.
Fix: when `VOICE_SIMULTANEOUS_TYPING` is true, `cancel()` must skip committing the stored `partialText`.
Keep existing behavior when false.

## BUG 3 (MEDIUM; blocks typing entirely in full-screen mode) — setting ignored when bottom-bar mode is OFF
Files: `java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt` (bottom-bar window reads the
setting ~line 403; full-screen window created ~line 966) and `UixManager.kt:773` (hides keyboard for the
full-screen window).
Symptom: only the bottom-bar voice window honors `VOICE_SIMULTANEOUS_TYPING`. When
`VOICE_INPUT_BOTTOM_BAR_MODE` is false, the full-screen `VoiceInputActionWindow` opens and hides the
keyboard, so the user can NEVER type during dictation even with the setting on.
Fix (preferred, minimal): when `VOICE_SIMULTANEOUS_TYPING` is true, force the bottom-bar voice window path
REGARDLESS of `VOICE_INPUT_BOTTOM_BAR_MODE` (simultaneous typing requires the keyboard visible, which only
the bottom-bar window supports). Find where bottom-bar vs full-screen is chosen (where
`VOICE_INPUT_BOTTOM_BAR_MODE` is read for that decision) and OR-in the simultaneous setting. Verify it
routes to `VoiceInputBottomBarWindow` whose `onlyShowAboveKeyboard` already returns true when simultaneous.
If forcing bottom-bar isn't clean, instead keep the keyboard visible in the full-screen window when
simultaneous — but prefer the smaller change. Report which approach you used.

## Deliverable
Apply the edits. Output a unified diff per changed file and a 3-line summary confirming the OFF path is
unchanged for each fix. Do not run gradle.
