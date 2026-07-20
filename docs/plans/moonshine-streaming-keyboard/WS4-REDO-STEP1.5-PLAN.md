# WS4-redo Step 1.5 — append-only dictation + UX hardening

> **RECONCILED with user-posted ChatGPT state-machine research**
> ([../../Research/state_machine.txt](../../Research/state_machine.txt)) — it rejects the
> positional word-count (K) design below (token merges/splits drift positions: "gon na"→"gonna"
> loses words) and prescribes the per-burst frontier state machine. **Fix 1 as specified in the
> RECONCILIATION section at the bottom supersedes the original Fix 1 section.** Q1-Q4 user
> decisions unchanged; ChatGPT's default bundle matches them (continuous mic, pause resets line,
> editor-context formatting, smart boundary spacing, queue during composition, fail closed).

## Context

On-device test of windowless dictation (Step 1, pushed `1aa5e2a47`) surfaced a real bug: user
deleted dictated text mid-session, kept speaking → the **entire previous transcript re-pasted**
into the field. Root cause is known and marked in code as a deferred limitation:
`TextEditCoordinator` strips a frozen `stablePrefix` **string** off each full Moonshine
snapshot; Moonshine revises earlier words (`LineUpdated`/`LineTextChanged`, per
WS4-REDO-STEP1-FINDINGS.md §1) and the sanitizer's caps/spacing can shift after an edit — the
moment a snapshot no longer literally starts with `stablePrefix`, `removePrefix()` returns the
whole string and the coordinator re-commits everything.

User decided (AskUserQuestion, all recommended options): append-only fix + VAD
finalize-and-keep-listening + live revisable tail (unchanged) + field-context caps + auto
boundary spacing. ChatGPT bridge is down (no browser instance); re-consult optional later —
design follows the saved ceiling research (`docs/Research/chatgpt_true_simultaneity_ceiling.md`:
range-ledger/append-only sections).

## Fix 1 — append-only positional word alignment (the bug)

File: `java/src/org/futo/inputmethod/latin/uix/voice/TextEditCoordinator.kt`
Test: `src/test/java/org/futo/inputmethod/latin/uix/voice/TextEditCoordinatorTest.kt`

Replace the `stablePrefix: String` mechanism with a **frozen word count K (high-water mark,
never decreases)** over the tokenized snapshot:

- `VoiceSnapshot(full)`: `words = full.split-on-whitespace`; tail = `words[K..]` joined;
  `sink.replaceVoiceTail(tail)`. Revisions to `words[<K]` are **dropped forever** (user
  deletions stay deleted; nothing before the tail is ever rewritten). Tail region remains
  live-replaceable (Q2 decision: revisable tail).
- Stability advance: compare last-3 snapshots **positionally by word token**; freeze words
  `K..K+m` when all three agree; whole words only (existing rule preserved).
- Freeze on touch (`KeyboardComposingStarted` / user `SelectionChanged`): `K += tailWordCount`,
  clear tail (replaces current `stablePrefix += mutableTail`).
- Transcript shrinks below K (Moonshine merge e.g. "gon na"→"gonna"): commit nothing, keep K
  (documented one-word-dupe edge; proper fingerprint repair = Step 2).
- `VoiceFinal(text)`: commit `words[K..]` only, then full reset (new utterance).

New tests: (a) frozen-word revision does NOT re-commit (regression for the on-device bug);
(b) shrunken snapshot → no field write; (c) freeze → early-word revision → only new words land.
Keep existing 6 tests green (adapt expectations where the prefix→K change legitimately shifts
them).

## Fix 2 — VAD pause: finalize + keep listening (Q1)

File: `java/src/org/futo/inputmethod/latin/uix/voice/HeadlessVoiceSession.kt` (+ tiny
`UixManager` touch if stop flag needs plumbing)

- Add `userRequestedStop` flag: `stop()` sets it; `finished(result)` commits the final text,
  then if NOT user-requested (VAD auto-stop) → `view.reset()` + re-`start()` a fresh burst
  (transcript + K reset). `listening` stays true across the restart, so the mic glow never
  blinks off mid-session. User mic-tap or `onInputFinishing` fully stops.

## Fix 3 — sanitizer reads field at cursor (Q3)

File: `HeadlessVoiceSession.kt`

- Replace `ModelOutputSanitizer.sanitize(result, null, …)` with a `TextContext` built from
  `getTextBeforeCursor`/`getTextAfterCursor` (same `Constants.VOICE_INPUT_CONTEXT_SIZE` pattern
  as `ActionInputTransactionIME.kt:68-71`), refreshed at burst start and after each freeze
  (cursor/context changed). Correct caps after user edits.

## Fix 4 — auto single-space boundary (Q4)

File: `HeadlessVoiceSession.kt` (`VoiceTailSink`)

- When committing a **fresh** tail (tailLen == 0): read 1 char before cursor; if non-whitespace
  and tail doesn't start with space → prepend one space; collapse double space. Inside the
  existing `beginBatchEdit` block.

## Fix 5 — mic glow actually animates + radar ring (Q&A #8-9)

File: `java/src/org/futo/inputmethod/latin/uix/ActionBar.kt`

Device shows a static contrast circle: state chain works, animation doesn't tick. Cause: pulse
is captured as a plain `Float` at composition; if the IME ComposeView doesn't recompose per
frame, draw never updates. Fix: pass the infinite-transition value as **`State<Float>` and read
it INSIDE `drawBehind`/`graphicsLayer` lambdas** (draw-phase snapshot reads redraw every frame
independent of recomposition). Visual per user request: keep the filled contrast circle +
add an expanding **radar ring** (radius r→1.7r, alpha 1→0, ~900ms repeating) in `scheme.primary`
— unmistakably alive. Both ActionItem and ActionItemSmall. Idle path unchanged.

## Fix 6 — default voice system prompts (Q&A #5-7)

Files: where `LOCAL_VOICE_SYSTEM_PROMPT` / `GROQ_VOICE_SYSTEM_PROMPT` setting keys define their
default values (locate in `VoiceInputSettingKeys.kt` / settings definitions — small exploration
at execution), so the existing editable fields come pre-filled.

Default prompt (both, same text, tuned wording allowed):
"Transcribe accurately with proper punctuation, capitalization, and grammar. Write numbers as
contiguous digits with no spaces or punctuation between them (e.g. 9876543210); keep phone
numbers, codes, and IDs as one unbroken sequence. Format dates like 12/03/2026 and times like
10:30."
Constraint: only the DEFAULT changes — user-edited values must be preserved (verify the
settings framework only applies defaults when unset).

## Supporting steps (Q&A #10-11)

- Dump device logcat (`HeadlessVoiceSession` etc.) from the user's latest test runs; use it to
  confirm listening flips + spot anything unexpected before/after the fixes.
- ChatGPT consult: user sent the query manually (Q&A #13) and will post the response. When it
  lands, reconcile it against Fix 1's policy + the default prompts BEFORE finalizing those two;
  if it hasn't landed by then, proceed on the saved research and reconcile after.

## Execution

Per user workflow (Q&A #12): subagents (fable) implement per task, controller (this session)
orchestrates + reviews each diff; Codex quota-dead until Jul 25. Three tasks:
(1) coordinator append-only rework + tests (Fix 1);
(2) session UX — VAD keep-listening, field-context sanitizer, boundary space (Fixes 2–4);
(3) glow animation + radar ring, and default system prompts (Fixes 5–6).
Step zero (Q&A #14): this file is a plan-mode scratch copy only — immediately save the
authoritative plan as `docs/plans/moonshine-streaming-keyboard/WS4-REDO-STEP1.5-PLAN.md`,
link it from `PLAN.md`'s workstream table, commit.
First: dump logcat from the user's latest test. Reconcile user-posted ChatGPT response when it
lands. Then per task: build `assembleUnstableDebug`, review, commit; finally `adb install -r`
to `10BF191Z51001DC`, **push** (standing order), post decision record + test instructions to
`Q and A.qanda`, update memory project-state row + progress ledger.

## Verification

- Unit: coordinator tests incl. 3 new regressions → all green.
- Build: `./gradlew assembleUnstableDebug` → SUCCESS; install via adb.
- On-device (user): (a) dictate → delete some words → keep speaking → only NEW words appear,
  no re-paste; (b) pause mid-session → speak again → continues, mic glow steady, transcript
  independent; (c) type a word then speak → exactly one space at the join; (d) caps correct
  after deleting back to mid-sentence. Logcat markers: `HeadlessVoiceSession` listening flips +
  add one line on VAD auto-restart.

---

## RECONCILIATION — authoritative Fix 1 algorithm (supersedes "Fix 1" above)

Per docs/Research/state_machine.txt. Coordinator works in RAW token space; the sink owns all
text transformation.

### New EditSink contract (replaces replaceVoiceTail/freezeVoiceTailPrefix)

```kotlin
interface EditSink {
    /**
     * Replace the current revisable tail with [frozenAppend] + [tail].
     * [frozenAppend] becomes permanent immediately (never rewritten); [tail] is the new
     * revisable region. Either may be empty ("" + "" just clears the tail).
     * The sink transforms outgoing text (sanitize w/ field context, boundary space,
     * sentence caps) and tracks the WRITTEN length of the tail itself.
     */
    fun updateVoiceText(frozenAppend: String, tail: String)
    /** Current tail becomes permanent field content; tracking drops. */
    fun freezeVoiceTail()
}
```

### Coordinator per-burst state

`prevRawTokens: List<String>`, `frontier: Int` (emitted-token count, high-water, validated),
`revisionCrossedFrontier: Boolean`, `composingActive: Boolean`. Burst = one recognizer
utterance (VoiceFinal / NewInputSession resets all).

### On VoiceSnapshot(rawFull) — raw text, whitespace-tokenized

1. `tokens = rawFull.trim().split(Regex("\s+"))` (empty → clear tail, keep state).
2. `common = longestCommonPrefixLength(prevRawTokens, tokens)`; `prevRawTokens = tokens`.
3. If `common < frontier` → `revisionCrossedFrontier = true`; `sink.updateVoiceText("", "")`
   (clear tail, FAIL CLOSED — nothing more this burst until VoiceFinal). Return.
4. If `revisionCrossedFrontier` → return (stay closed).
5. If `composingActive` → return (queue: bookkeeping only, no field writes, frontier frozen).
6. `safeEnd = maxOf(frontier, common - HOLDBACK)` where `HOLDBACK = 1` (one-word holdback —
   stable words only become permanent).
7. `sink.updateVoiceText(frozenAppend = tokens[frontier until safeEnd].join(" "),
   tail = tokens[safeEnd until tokens.size].join(" "))`; `frontier = safeEnd`.
   (Tail = newest words incl. the held-back one → live revisable tail per user Q2.)

### Other intents

- KeyboardComposingStarted → `sink.freezeVoiceTail()`; `frontier += <tail token count last
  written>`; `composingActive = true`. (Voice queues while a word is being typed.)
- KeyboardWordCommitted → `composingActive = false` (next snapshot flushes backlog beyond
  frontier at the cursor, after the typed word).
- SelectionChanged(userInitiated, collapsed) → same as ComposingStarted but composingActive
  stays false (cursor move: freeze, continue at new cursor; deliberate user placement).
- VoiceFinal(rawFinal): if `revisionCrossedFrontier` → `sink.freezeVoiceTail()` only (fail
  closed: uncertain tail already cleared). Else tokenize; `sink.updateVoiceText(tokens beyond
  frontier joined, "")` then `sink.freezeVoiceTail()`. Reset burst state.
- NewInputSession(gen): freeze, reset burst state, adopt generation.

### Sink (VoiceTailSink) — owns transformation (folds Fixes 3+4 here)

- `updateVoiceText`: batchEdit { deleteSurroundingText(writtenTailLen); write
  transform(frozenAppend); write transform(tail) tracking writtenTailLen }.
- `transform(segment)`: ModelOutputSanitizer with a TextContext read from the field
  (getTextBeforeCursor/AfterCursor, VOICE_INPUT_CONTEXT_SIZE) refreshed per updateVoiceText
  when starting fresh (writtenTailLen == 0); boundary rule: if preceding field char is
  non-whitespace and segment doesn't start with space → prepend one space; collapse double
  spaces at the join; sentence-capitalize segment start when field context warrants.
- HeadlessVoiceSession submits RAW hypothesis strings (stop pre-sanitizing in
  partialResult/finished) — the raw-diff requirement.

### Tests (pure coordinator, fake sink recording (frozenAppend, tail) pairs)

(a) growing snapshots: frontier advances only past validated common prefix w/ 1-word holdback;
frozen text never re-emitted. (b) REGRESSION (the on-device bug): early-word revision after
frontier advance → fail closed: tail cleared, NO re-emission of old text, later snapshots
emit nothing until VoiceFinal. (c) merge "gon na"→"gonna home": common=0 < frontier → fail
closed (never silent word loss / wrong emission). (d) composingActive queues: no writes while
typing, flush after KeyboardWordCommitted. (e) VoiceFinal beyond frontier flushes + resets;
next burst starts clean. (f) stale generation dropped. Rewrite existing tests to the new
contract (raw segments, no sanitizer in coordinator).
