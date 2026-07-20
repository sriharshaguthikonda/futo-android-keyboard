# WS4-redo Step 1, Task 4 — Mic-key glow while dictating

## What renders the glow

`java/src/org/futo/inputmethod/latin/uix/ActionBar.kt`:

- New private helper `isMicDictating(action)`: true only when `action == VoiceInputAction`,
  not in inspection (preview) mode, and `LocalManager.current.isHeadlessVoiceListening()`.
- New private helper `Modifier.micPulse(active)`: when active, wraps the icon in a
  `rememberInfiniteTransition` scale pulse (1.0 → 1.15, `tween(600)`, `RepeatMode.Reverse`) —
  the same parameters as the existing pulse in `VoiceInputAction.kt` `WindowContents`.
  When inactive it returns the receiver unchanged and the transition is never created.
- `ActionItem` (expanded row): `contentCol` becomes `scheme.primary` while dictating
  (same color the active-border path already uses), and the `Icon` modifier gains
  `.micPulse(isDictating)`.
- `ActionItemSmall` (pinned key): `fgCol` becomes `scheme.primary` while dictating, and the
  `Icon` modifier gains `.micPulse(isDictating)`.

## How `listening` reaches the composable

Chain (all snapshot-state reads, so recomposition is automatic):

1. `HeadlessVoiceSession.listening: State<Boolean>` (existing, untouched).
2. `UixManager` gains `headlessVoiceListeningState = mutableStateOf<State<Boolean>?>(null)`.
   `startHeadlessVoiceSession()` stores `state.headlessSession?.listening` into it right after
   `startHeadlessSession(...)`. Two observable layers means the composable recomposes both on
   the very first session creation (null → non-null holder) and on every listening flip —
   `persistentStates` is a plain `HashMap`, so reading through it directly would not have been
   observable for the first session.
3. `UixManager.isHeadlessVoiceListening(): Boolean` reads both layers.
4. `KeyboardManagerForAction` (Action.kt) gains `fun isHeadlessVoiceListening(): Boolean = false`
   as a **default** interface method; `UixActionKeyboardManager` (the only implementor)
   overrides it to delegate to `UixManager`.
5. ActionBar composables reach it via `LocalManager.current` — the same CompositionLocal
   ActionBar already uses (e.g. for haptic feedback), no new global invented.

## Proof the idle path is unchanged

- `isMicDictating` short-circuits: for every non-mic action it is `action == VoiceInputAction`
  → false, no state read, no `LocalManager` read. Previews short-circuit on
  `LocalInspectionMode` before touching `LocalManager` (which previews do not provide).
- `micPulse(false)` returns `this` — no `rememberInfiniteTransition`, no animation clock
  subscription, no `graphicsLayer`. The only steady-state cost while idle is one boolean
  snapshot read (`headlessVoiceListeningState.value == null`) for the mic key itself.
- Colors: `contentCol`/`fgCol` fall through to the exact pre-existing expressions when
  `isDictating` is false. Toggle-OFF users never start a headless session, so the holder stays
  null forever and rendering is byte-identical.
- No changes to `HeadlessVoiceSession`, `TextEditCoordinator`, `RichInputConnection`,
  `IMEManager`.

## Build

`./gradlew assembleUnstableDebug` → **BUILD SUCCESSFUL in 1m 17s** (only pre-existing
deprecation warnings, none in the touched code).

## Concerns

1. If a future code path starts a headless session without going through
   `UixManager.startHeadlessVoiceSession`, the holder will not be populated and the glow will
   not show for that path (today there is exactly one entry point).
2. The glow keys off the headless session only; the classic full-screen voice window keeps its
   own in-window pulse and does not light the action-bar mic (intended — the bar is hidden or
   secondary there).
3. Not yet verified on-device (Task 6 covers that); verified by build + reasoning only.
