# WS4a Spec — Experimental simultaneous voice + typing (default OFF)

Goal: let the user type on the keyboard WHILE Moonshine voice dictation is active, instead of
voice taking over the field. Entirely behind a NEW setting that defaults to OFF, so when the
setting is off the behavior is byte-for-byte identical to today (no regression to normal voice or
normal typing). This is v1: keyboard stays live + typing works; voice inserts FINALIZED text at the
cursor (live composing partials are suppressed in simultaneous mode to avoid a two-writer composing
collision — that merge is a later commit).

IMPORTANT constraints for the implementer (codex):
- Do NOT run gradle / any build. Just edit code and report unified diffs.
- Do NOT touch `Q and A.qanda`, `.claude/`, or any status/plan files. Only the code files below.
- Gate EVERY behavioral change behind the new setting read as a boolean. When the setting is false,
  the existing code paths must execute exactly as before (same calls, same order).
- Minimal diffs. Kotlin. Must compile.

## 1. New setting

File: `java/src/org/futo/inputmethod/latin/uix/VoiceInputSettingKeys.kt`
- Add a `SettingsKey<Boolean>` named `VOICE_SIMULTANEOUS_TYPING` defaulting to `false`.
  Follow the EXACT pattern of the existing keys in this file (e.g. `DISALLOW_SYMBOLS`) — same
  SettingsKey/dataStoreKey/booleanPreferencesKey style and a unique preference name string like
  `"voice_simultaneous_typing"`.

File: `java/src/org/futo/inputmethod/latin/uix/settings/pages/VoiceInput.kt`
- In `VoiceInputMenu` add ONE toggle row using the existing `userSettingToggleDataStore(...)` pattern
  already used in that file, bound to `VOICE_SIMULTANEOUS_TYPING`.
  Title: "Simultaneous voice + typing (experimental)".
  Subtitle: "Keep the keyboard usable while dictating. Live partial preview is disabled in this mode."
  Place it near the other voice-input toggles (this is the "appropriate place" the user asked for).

## 2. UI coexistence — keep keyboard visible under the voice pill

File: `java/src/org/futo/inputmethod/latin/uix/actions/VoiceInputAction.kt`
- The bottom-bar voice window (`VoiceInputBottomBarWindow`, around line 396-410) sets
  `onlyShowAboveKeyboard = false`. Change it so that when `VOICE_SIMULTANEOUS_TYPING` is true it
  returns `true` (keyboard stays visible), else keeps `false`. Read the setting via
  `context.getSetting(VOICE_SIMULTANEOUS_TYPING)` (mirror how other settings are read in this file,
  e.g. in `loadSettings()` around lines 179-195). If the value isn't available in the window's scope,
  read it wherever the window is constructed and pass it in.
- Do the same consideration for the full-screen `VoiceInputActionWindow` (around line 171) ONLY IF it
  is the window used by the bottom bar flow; if unsure, leave the full-screen one unchanged and only
  change the bottom-bar window. Note in your report which you changed.

File: `java/src/org/futo/inputmethod/latin/uix/UixManager.kt`
- Around line 771-780, `openActionWindow` sets `mainKeyboardHidden.value` from
  `action...onlyShowAboveKeyboard == false`. No change needed IF step above makes
  `onlyShowAboveKeyboard` true — verify `mainKeyboardHidden` will then stay false. If there is an
  additional place that force-hides the keyboard for voice, gate it on the setting too. Report what
  you found.

## 3. Keep the typing IME alive + route keys to it (the core)

File: `java/src/org/futo/inputmethod/engine/IMEManager.kt`

Read a boolean once, e.g. `val simultaneous = service.getSetting(VOICE_SIMULTANEOUS_TYPING)` using
whatever synchronous getSetting the service exposes (see how other DataStore settings are read
synchronously elsewhere in the engine; `helper.context.getSetting(...)` is available — use the
correct accessor). Then:

- `getActiveIME()` (line ~72-102): the first line `currentActionInputTransactionIME?.let { return it }`
  (line 75) is what routes ALL input to the no-op transaction IME during voice. Gate it:
  when `simultaneous` is true, do NOT short-circuit — fall through and return the real typing IME
  (General/Japanese) so key events and selection updates reach it. When false, keep returning the
  transaction IME exactly as today.

- `createInputTransaction()` (line ~157-196): line 193 `existingIme.onFinishInput()` tears the typing
  IME down. When `simultaneous` is true, SKIP that call so the typing IME stays alive. When false,
  keep calling it. Everything else in this function stays.

- Selection fan-out (`onUpdateSelection` handling around lines 251 and 274, which call
  `getActiveIME(...).onUpdateSelection(...)`): when `simultaneous` is true, the transaction IME will
  no longer be returned by getActiveIME, but it STILL needs cursor updates for its InputConnection
  wrapper. So when `simultaneous` is true AND `currentActionInputTransactionIME != null`, ALSO call
  `currentActionInputTransactionIME!!.onUpdateSelection(...)` with the same args (in addition to the
  normal getActiveIME target). When false, behavior unchanged.

- `endInputTransaction()` (line ~216-227): unchanged, but verify that after voice ends the typing IME
  is in a good state. When simultaneous was true we never finished it, so `startIme(existingIme)` at
  line 224 may double-start it. Guard: when `simultaneous` is true, do NOT re-`startIme` the existing
  IME in endInputTransaction (it was never stopped). Report your reasoning.

## 4. Suppress composing partials in simultaneous mode (avoid two-writer collision)

File: `java/src/org/futo/inputmethod/engine/general/ActionInputTransactionIME.kt`

- `updatePartial(text)` (line 74-92): when `helper.context.getSetting(VOICE_SIMULTANEOUS_TYPING)` is
  true, STORE `partialText = text` but SKIP the `ic?.setComposingText(...)` / `send()` field write
  (so voice does not fight the keyboard's composing region). Keep the existing behavior when false.
- `commit(text)` (line 94-104): unchanged — it uses `commitText`, which inserts finalized text at the
  cursor. That is how dictated text lands in simultaneous mode.
- Add a brief `// ponytail:` comment noting the ceiling: v1 suppresses live composing partials in
  simultaneous mode; a real multi-writer partial merge is a later commit.

## Report
Output a unified diff per changed file and a short note on: which voice window(s) you changed, how
you read the setting synchronously in IMEManager, and your endInputTransaction reasoning. Do not run
gradle.
