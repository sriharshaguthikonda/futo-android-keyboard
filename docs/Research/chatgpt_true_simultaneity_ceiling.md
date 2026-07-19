# ChatGPT research — maximum true voice+typing simultaneity on Android 7–11

Source: ChatGPT (browser bridge), 2026-07-19. Thread:
https://chatgpt.com/c/6a5d1df9-4828-83e8-8010-01acc5c2e397
Prompt: how far can genuinely simultaneous voice dictation + touch typing/live-editing in the
SAME field be pushed on Android 7–11 for the FUTO fork; what's the premium architecture.

> Saved verbatim as reference for WS4-redo (windowless Gboard-style dictation) Step 2 design.
> Related: [WS4-REDO-DICTATION-DESIGN.md](../plans/moonshine-streaming-keyboard/WS4-REDO-DICTATION-DESIGN.md),
> [typing while listening..txt](typing%20while%20listening..txt).

## Bottom line

The maximum shippable design is NOT "two InputConnections." It is:

> one serial writer + two logical cursors + one real composing span + internally tracked
> replaceable voice ranges.

Android has only one actual selection and one actual composing region. "Simultaneous"
therefore means both input streams proceed without waiting for each other, while the
coordinator rapidly time-slices editor state. `commitText()` and `setComposingText()` each
replace whichever composing span currently exists. There is no second composition channel,
document revision number, compare-and-swap edit, or editor transaction lock.

## 1. What Gboard actually does — unknown, don't assume

Google confirms Pixel Advanced voice typing lets you type while the mic stays on, using
streaming on-device recognition, but does NOT publish the InputConnection calls / composing
policy. Several implementations are all UX-consistent (commit-stable-only; committed mutable
tail; composition lift-edit-restore; shadow-composer; or a mix). To know exactly, build an
InputConnection trace editor (log beginBatchEdit/endBatchEdit/commitText/setComposingText/
setComposingRegion/finishComposingText/setSelection/deleteSurroundingText/onUpdateSelection)
and test Gboard on a Pixel. Do not base the implementation on "Gboard just commits each word"
— plausible but not established.

## 2. Dropping editor composition entirely

Genuine simultaneity, with a qualification. If every key and voice update enters the same
actor and there is no composing span, neither source clobbers composition; both apply
immediately. But edits remain physically serial, and if both sources share one cursor you get
interleaving garbage ("ur tomorrow ge"). Useful simultaneity needs TWO logical insertion
points (`typingSelection`, `voiceAnchor/voiceMutableRange`); before each op the actor moves
the real selection to the right logical point, performs it, updates ranges, restores the
visible typing selection.

What you lose without a composing span, and how to recover:
- **Autocorrect:** keep a shadow `WordComposer` (typed code points, key coords, suggestion
  state); commit raw chars immediately; track the raw token range; at space/punctuation choose
  the correction and replace the raw range (`finishComposing → setSelection(word) →
  commitText(corrected) → restore selection`); keep an edit journal so Backspace reverts.
  Still worse: app sees misspelt raw word first; validators react per-char; undo history
  fragments; recorrection of older words needs range verification.
- **Suggestions:** almost fully recoverable — feed the shadow composer + context into the
  existing pipeline; lose only editor-visible provisional styling.
- **Gesture/glide:** run decoder normally, commit selected word at finger-up; show live
  gesture candidate in the suggestion strip; lose live provisional gesture text in-field.
- **Backspace:** easy; backspace-word mirrors the token before the logical cursor;
  undo-autocorrect retains original/corrected/range/left+right context fingerprints, revert
  only when text+context still match.
- **CJK / composition-heavy languages:** commit-every-key is unsuitable universally; fine for
  Latin tap typing.

## 3. Techniques beyond "voice defers to touch"

**A. Composition lift–edit–restore (strongest for full touch typing + immediate voice):**
```kotlin
beginBatchEdit()
try {
    finishComposingText()                 // preserve WordComposer internally
    setSelection(vStart, vEnd)            // replace/append committed voice range
    commitText(newVoiceTail, 1)
    updateRangeLedger()
    setComposingRegion(newTStart, newTEnd) // restore keyboard's one real composing span
    setSelection(newTypingStart, newTypingEnd)
} finally { endBatchEdit() }
```
`finishComposingText()` leaves characters/cursor unchanged; `setComposingRegion()` re-marks a
range as composing without moving selection. The main risk: the coordinator must own
transaction state and update `RichInputConnection` caches + expected selection/composition
bounds, so InputLogic doesn't interpret the expected composition remove/restore as an external
cursor move. Best on standard EditText; can break in WebViews/custom editors (composition
finish/restart may surface as JS composition events; some ignore `setComposingRegion()`) — so
make it a capability MODE, not the only mode.

**B. Committed mutable voice tail (live partials in-field):** never put streaming partials in
composition. Maintain `VOICE_STABLE_n` (immutable) + `VOICE_MUTABLE` (replaceable ordinary
text). Per Moonshine partial: find longest stable prefix, freeze newly-stable words, replace
only `VOICE_MUTABLE`, restore typing composition/selection. If the user edits the mutable
range, freeze it and start a new tail. Keep only ONE mutable voice tail. More aggressive than
Gboard's finalized-only, but technically possible.

**C. Virtual dual cursor:** track `TypingState` + `VoiceState` separately; borrow the real
selection briefly per edit. Voice appends at a pinned anchor; user can tap/edit elsewhere;
text inserted before the anchor shifts it via range-transform. Android exposes no second
caret — show the voice destination in the keyboard toolbar ("Voice inserting after: …").

**D. Batch edits:** suppress intermediate display only — NOT a lock/rollback/revision check.
Use for lift-restore, range replacement, spacing, avoiding cursor jumps; don't treat as a
transaction guarantee.

**E. getExtractedText():** optional reconciliation only (may return null/time out); AOSP even
disabled word-correction monitoring over token-mismatch issues. Keep your own range ledger +
context fingerprints; use extracted text only to repair the mirror.

**F. Dual InputConnectionWrappers:** no concurrency benefit (same editor/selection/span);
useful only for logging, actor-only enforcement, metrics, capability checks.

**G. Accessibility:** do NOT use as the editing backplane. `ACTION_SET_TEXT` replaces node
text + cursor-to-end; not fine-grained/transactional; WebView nodes incomplete; consent/Play
policy burden.

## 4. Premium architecture

- **Layer 1 — single writer actor:** tap/gesture-final/backspace/suggestion/voice-partial/
  voice-stable/voice-correction/selection-reconcile all go through one actor. Recognition
  callbacks submit intents only; never touch InputConnection.
- **Layer 2 — range ledger:** `OwnedRange(id, origin{TOUCH_RAW, TOUCH_AUTOCORRECTED, GESTURE,
  VOICE_STABLE, VOICE_MUTABLE}, start, end, expectedText, mutable, taintedByUser)`. Every edit
  transforms all later ranges (`delta = N - (end-start)`); anchors need affinity
  (BEFORE/AFTER_INSERTION).
- **Layer 3 — editor mirror + fingerprints:** small local window around typing selection,
  typed composing range, voice mutable range, voice anchor. Before modifying a voice range,
  verify expected text + left/right context fingerprints + session generation; if offset
  no longer matches, search a nearby window for the unique fingerprint, repair or mark tainted
  and stop rewriting. This substitutes for the missing compare-and-swap API.
- **Layer 4 — two typing modes:** PREMIUM composition mode (lift-edit-restore, keeps
  autocorrect/suggestions/WordComposer/gesture) and COMMIT_ONLY compatibility mode (committed
  chars + shadow composer) for WebViews / editors that fail composition restore / repeated
  unexpected onUpdateSelection / connection restarts.
- **Layer 5 — streaming voice segmentation:** don't let raw partials drive editor replacements
  directly; track stable prefix / mutable suffix / revision confidence / last-changed time.
  Commit newly-stable words as immutable; one mutable tail if the editor is capable; otherwise
  show unstable tail in the voice pill and commit stable deltas only (voice still proceeds).
- **Layer 6 — per-app capability profile:** FULL / COMMIT_ONLY / CONSERVATIVE; dynamic
  downgrade when final selection ≠ expected, restored composing bounds vanish, anchor context
  differs, setComposingRegion/selection ops fail, app restarts input, or editor transforms
  text. Store profile by package + EditorInfo.

## Critical failure modes to design around
- **User taps during a voice selection-hop** (worst race): voice saves selection A, user taps
  B, voice restores stale A. Keep transactions very short; treat unexpected onUpdateSelection
  as a user action; never restore a saved selection after an unexpected change — abort/redirect
  voice; touch selection beats ASR revision.
- **App transforms text** (caps filters, char limits, markdown, mention chips, rich spans,
  validation): never assume `commitText()` left the exact string; verify local result before
  extending/rewriting an owned range.
- **WebView composition lifecycle:** repeated finish/restart triggers JS composition events →
  use COMMIT_ONLY after detecting incompatibility.
- **User edits a mutable voice segment:** mark user-owned immediately; later ASR revisions must
  not rewrite it; start a fresh mutable tail.
- **Focus changes:** every voice result carries inputSessionGeneration + package + field id;
  drop results from a previous connection.
- **UTF-16 indices:** ranges/deletion lengths are UTF-16 units, not graphemes; emoji/combining
  marks/surrogate pairs need care; `deleteSurroundingTextInCodePoints()` (API 24) not
  universally implemented.
- **Boundary spaces:** when a user finishes a word immediately before an inserted voice tail,
  intercept the separator and own the boundary (merge/replace whitespace) in the same batch to
  avoid "urgent  tomorrow".

## Recommendation (verbatim)

Build FULL mode: touch typing owns the sole Android composing span; voice partials are
ordinary internally-owned replaceable ranges; typing and voice each have a logical cursor;
each voice update temporarily lifts composition, edits its range, restores composition, then
restores the typing selection. Build COMMIT_ONLY as the serious fallback (every key committed;
shadow WordComposer preserves suggestions/autocorrect; gesture words commit at release; voice
keeps its own mutable committed range). That is approximately the real ceiling on Android
7–11: genuine no-word-boundary-waiting and live voice + touch editing on cooperative editors.
Android cannot give a universally reliable second cursor, second composing region, or atomic
document-edit transaction across every WebView/chat app/custom editor.
