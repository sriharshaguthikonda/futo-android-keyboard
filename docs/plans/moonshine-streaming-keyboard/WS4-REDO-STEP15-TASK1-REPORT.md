# WS4-redo Step 1.5 — Task 1 report: append-only frontier state machine

Implements the RECONCILIATION spec in
[WS4-REDO-STEP1.5-PLAN.md](WS4-REDO-STEP1.5-PLAN.md) (per
`docs/Research/state_machine.txt`). Fixes the on-device bug where deleting dictated text
mid-session caused the entire transcript to re-paste.

## Files changed

- `java/src/org/futo/inputmethod/latin/uix/voice/TextEditCoordinator.kt` — rewritten
- `java/src/org/futo/inputmethod/latin/uix/voice/HeadlessVoiceSession.kt` — raw submits + new sink
- `src/test/java/org/futo/inputmethod/latin/uix/voice/TextEditCoordinatorTest.kt` — rewritten

## State machine as implemented (TextEditCoordinator)

Pure Kotlin, no Android imports; works in RAW whitespace-token space. The old
`stablePrefix: String` + 3-snapshot agreement mechanism is gone.

Per-burst state: `prevRawTokens: List<String>`, `frontier: Int` (count of tokens emitted as
permanent text; high-water, validated per snapshot), `revisionCrossedFrontier: Boolean`,
`composingActive: Boolean`, `lastWrittenTailTokens: Int` (token count of the tail last handed
to the sink). `currentGeneration` + stale-generation drop in `submit()` unchanged.

`VoiceSnapshot(rawFull)`:
1. tokenize `rawFull.trim()` on `\s+`; empty → `sink.updateVoiceText("", "")` (clear tail),
   keep state, return.
2. `common = commonPrefixLength(prevRawTokens, tokens)`; `prevRawTokens = tokens`.
3. `common < frontier` → `revisionCrossedFrontier = true`; clear tail via
   `updateVoiceText("", "")`; FAIL CLOSED for the rest of the burst.
4. already closed → return (bookkeeping only).
5. `composingActive` → return (queue while typing; no field writes, frontier frozen).
6. `safeEnd = maxOf(frontier, common - 1)` (HOLDBACK = 1).
7. `sink.updateVoiceText(tokens[frontier..safeEnd) joined, tokens[safeEnd..) joined)`;
   `frontier = safeEnd`; `lastWrittenTailTokens = tokens.size - safeEnd`.

Other intents:
- `KeyboardComposingStarted` → `sink.freezeVoiceTail()`; `frontier += lastWrittenTailTokens`;
  `composingActive = true`.
- `KeyboardWordCommitted` → `composingActive = false` (next snapshot flushes the backlog).
- `SelectionChanged(userInitiated, collapsed)` → same freeze; `composingActive` untouched.
- `VoiceFinal(rawFinal)`: closed → `freezeVoiceTail()` only. Else emit
  `tokens[min(frontier, size)..)` as frozen with empty tail, then freeze. Either way full
  burst reset. (`cancelled()` still submits `VoiceFinal("")` → tail deleted, freeze, reset.)
- `NewInputSession(gen)`: freeze, reset, adopt generation.

## Sink transform (VoiceTailSink in HeadlessVoiceSession.kt)

New `EditSink` contract: `updateVoiceText(frozenAppend, tail)` + `freezeVoiceTail()`
(replaces `replaceVoiceTail`/`freezeVoiceTailPrefix`).

- `partialResult`/`finished` now submit RAW recognizer strings (pre-sanitize removed).
- `updateVoiceText`: whole write inside `beginBatchEdit`/`endBatchEdit` —
  `deleteSurroundingText(writtenTailLen)`, then commit `transform(frozenAppend)`, then commit
  `transform(tail)` tracking the WRITTEN (post-transform) tail length.
- Field context (`TextContext` from `getTextBeforeCursor`/`getTextAfterCursor`,
  `Constants.VOICE_INPUT_CONTEXT_SIZE` — same pattern as `ActionInputTransactionIME.kt:68-71`)
  is cached and refreshed only when starting a fresh tail (`writtenTailLen == 0`), because
  reading mid-tail would see our own tail text. Each frozen append is folded into the cached
  before-context so the following tail transforms against it.
- `transform(segment)` = `ModelOutputSanitizer.sanitize(segment, cachedContext, isCapsLocked)`.
  The sanitizer supplies the boundary single-space rule (leading space when the preceding
  field char is non-whitespace) and sentence caps/lowercase; it trims the segment first, so no
  double space can form at the join — no extra collapse pass needed.
- Cursor-delta reporting preserved: one net delta per `updateVoiceText`
  (`written − oldTailLen`) into `pendingVoiceEditDeltas`, exactly as `replaceVoiceTail` did.

## Deviations from spec

- Step 1 empty-snapshot clear also runs while `composingActive`/fail-closed (spec lists it
  first, before those guards). Harmless: the tail is already length 0 then, so the sink write
  is a no-op.
- `VoiceFinal` clamps `frontier` to `tokens.size` (`minOf`) — guards a final hypothesis that
  shrank below the frontier without a preceding snapshot; emits nothing rather than throwing.
- No other deviations. VAD keep-listening restart NOT included (next task, per instructions).

## Tests

All spec cases (a)-(f) plus a collapsed-cursor-move freeze test, against a fake sink recording
`(frozenAppend, tail)` pairs — raw segments, no sanitizer at coordinator level:

- (a) `frontierAdvancesWithHoldbackAndNeverReEmits`
- (b) `revisionBeforeFrontierFailsClosed` — the on-device re-paste regression
- (c) `tokenMergeFailsClosed` — "gon na" → "gonna home"
- (d) `composingQueuesAndFlushesAfterWordCommitted`
- (e) `finalFlushesBeyondFrontierAndResets`
- (f) `staleGenerationIsDropped`
- `userCursorMoveFreezesTail`

Result: PENDING
Build: PENDING
