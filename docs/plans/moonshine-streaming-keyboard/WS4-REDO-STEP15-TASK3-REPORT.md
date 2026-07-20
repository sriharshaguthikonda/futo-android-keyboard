# WS4-redo Step 1.5 — Task 3 report (Fixes 5 + 6)

## Fix 5 — mic glow animates (ActionBar.kt)

### Why it was static
`micPulseFraction()` unwrapped the infinite transition's `State<Float>` with `by` and returned
a plain `Float`. That value was a snapshot taken at composition; `drawBehind` / `graphicsLayer`
lambdas captured the frozen number. On a phone the IME ComposeView does not recompose per
animation frame, so the draw pass never saw a new value — the circle rendered once (contrast
color) and stayed static.

### The fix: draw-phase snapshot reads
`micDictationAnim(active)` now returns the transition values as `Pair<State<Float>, State<Float>>`
(pulse, ring) without unwrapping. The `.value` reads happen INSIDE the draw-phase lambdas:

- `drawBehind { val p = pulse.value; ... drawMicRing(..., ring.value) }` (both ActionItem and
  ActionItemSmall)
- `graphicsLayer { val p = pulse.value; scaleX/scaleY = 1f + 0.25f * p }` (icon scale in
  `Modifier.micPulse`)

Compose's snapshot system registers a read observer per phase: a state read inside a draw block
invalidates only the draw pass, which re-executes every animation frame driven by the
choreographer — no recomposition required. This is the canonical "defer state reads to the draw
phase" pattern.

### Visuals
- Filled circle: `scheme.primary`, alpha 0.55→1.0, radius base→1.2x (ActionItem) / 1.25x
  (ActionItemSmall), 500ms tween, RepeatMode.Reverse (unchanged parameters, now actually ticking).
- Radar ring (new, `drawMicRing`): `Stroke` circle, width 2.dp, radius base→1.7x
  (`base * (1f + 0.7f * ring)`), alpha 1→0 (`1f - ring`), 900ms tween, RepeatMode.Restart —
  expanding-and-fading ring that re-launches from the base radius every cycle.
- Icon scale pulse 1.0→1.25x, same pulse state.
- Both animations share one `rememberInfiniteTransition` (two `animateFloat`s with different specs).

### Idle path
When not dictating, `micDictationAnim` returns a remembered constant-0 state and no
`rememberInfiniteTransition` object exists; `Modifier.micPulse(active=false, ...)` returns `this`
untouched; ActionItemSmall's `drawBehind` else-branch draws exactly the previous idle rendering
(bgCol fill at circleRadius + isActive stroke border); ActionItem falls through to the previous
`border`/no-op modifiers. Zero extra cost, byte-identical idle visuals.

## Fix 6 — default voice system prompts

### Where defaults live
`java/src/org/futo/inputmethod/latin/uix/VoiceInputSettingKeys.kt` — `GROQ_VOICE_SYSTEM_PROMPT`
("groq_voice_system_prompt") and `LOCAL_VOICE_SYSTEM_PROMPT` ("local_voice_system_prompt"), both
previously `default = ""`. Both now reference a shared `DEFAULT_VOICE_SYSTEM_PROMPT` const with
the exact agreed text (punctuation/grammar, contiguous digits e.g. 9876543210, unbroken
phone/codes/IDs, dates 12/03/2026, times 10:30).

### Proof user-set values survive
The settings framework is Jetpack DataStore Preferences. Every read path resolves
`stored ?: default`:
- `Settings.kt:270` — `DataStoreHelper.getSetting(key, default) = getSettingOrNull(key) ?: default`
  (backs both `Context.getSetting` and `Context.getSettingBlocking`).
- `Settings.kt:291` — `getSettingFlow`: `preferences[key] ?: default`.

The default is consulted ONLY when the key is absent from the persisted DataStore file. Any value
the user ever saved — including explicitly clearing the field to "" — is stored under the key and
always wins. Only fresh installs / never-touched fields see the new default. No UI code changed;
the settings text fields (`GroqConfigWhisper.kt`, `Languages.kt`, `TestingArena.kt`) read through
`useDataStore(key)` which uses the same default-fallback, so they come pre-filled automatically.

## Build
`./gradlew assembleUnstableDebug` → BUILD SUCCESSFUL in 1m 5s (76 tasks: 21 executed, 55 up-to-date).

## Concerns
1. Behavior change by design: fresh installs now SEND a non-empty prompt to Groq/local Whisper by
   default (previously empty = effectively no prompt). Consumers pass the string through unchanged
   (`VoiceInputAction.kt:221/452`, `HeadlessVoiceSession.kt:300`); if any backend treats a prompt
   as transcript-biasing context (Whisper `prompt` semantics), watch on-device output quality.
2. Ring stroke width is fixed 2.dp; on very small mic keys (ActionItemSmall, 16.dp radius) the
   1.7x ring reaches ~27dp radius and may clip against neighboring keys' bounds — drawBehind is
   not clipped by the 42.dp Box, but the parent row might clip. Verify on device.
3. Draw-phase animation on device cannot be proven by the build alone — needs the on-device retest
   the user is already planning (mic tap → pulsing fill + expanding ring).
