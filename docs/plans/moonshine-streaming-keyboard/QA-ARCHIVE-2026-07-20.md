# Q and A

## Agent status
- Active. Executing plan WS1 → WS2 → WS3 → WS4a, commit by commit.

## Questions for user
- Is your Groq API key currently saved in the app? (confirms streaming diagnosis)

## user comments
1. see if any features are implemneted in upstream as well, try to pull the ideas and code from upstream as well. do not reinvent the wheel.
2. if there are already features that are implemented elsewhere and can be just copied, just get it done. don't try to reinvent the wheel ever.
3. offline fallback when internet is not working, we need to have a proper production grade sort of thing, where offline fallback when groc or online models are not working has to be there. it is not proper right now.
4. i mean it works when i completely disconnect the internet and selected moonshine, but it does not do streaming sort of thing. it records the voice completely and then it sort of does the streaming. that is not the whole point of using a streaming model.
5. C:\Android_software\latinime\docs\Research\chatgpt_input_roadmap.txt -  some ideas by chatgpt!
6. groq cannot live stream it is too costly...moonshine streaming models will only have that feature
7. if they are enabled only then this streaming capability should be present - think that is a good idea
8. ai reply is still clunky to use..idk how to fix it?! you discuss chatgpt to fix it!
9. commit by commit !
10. create plans in the repository and link to the roadmap and other things properly, even if you create your own plans in your own folder.

---
### 🤖 Agent reply
ℹ️ **Root cause of "not streaming" (high confidence):** Moonshine live-streaming is gated behind `groqApiKey.isBlank()` (`AudioRecognizer.kt:470`). You still have a Groq key saved, so even offline the live path never starts — it falls to batch `transcribe()` which replays chunked at the end. Also `transcribe()` calls Groq FIRST whenever a key exists, discarding the streamed Moonshine text. That's your "records fully, then streams" symptom.
ℹ️ Confirmed your design (#6/#7): Groq never streams; streaming = Moonshine-only, active only when Moonshine is selected. Plan matches.
✅ Plan approved + in-repo plan written: `docs/plans/moonshine-streaming-keyboard/PLAN.md` (linked to the roadmap).
✅ Codex confirmed available (quota back).
⏳ **WS1 in progress:** when Moonshine is selected it will fully own dictation — live partials while you speak + final from streamed text, Groq not called. Building the dev app now.
👉 **After it installs** I'll ask you to switch your phone's active keyboard to the "unstable" FUTO build (current active one is the Play Store build — that's why Moonshine may seem broken).
👉 Please answer above: is a Groq API key currently saved?

_(Note: codex accidentally overwrote this file once; restored. Agent will keep this channel intact.)_

---
### 🤖 WS1 built ✅ — installing now, then YOUR test
✅ WS1 code done + **build SUCCESSFUL**. Changes: (1) removed the Groq-key gate blocking Moonshine streaming; (2) when Moonshine is selected it now fully owns dictation — live partials + final from streamed text, Groq not called; (3) live partials now actually shown on screen for Moonshine.
⏳ Installing the `unstable` dev build to your phone (I'll set it as the active keyboard via adb automatically).
👉 **When I say go**, do this on your phone:
   1. Open any text field (e.g. Notes / a chat box).
   2. Make sure **Moonshine** is the selected voice backend (Voice Input settings → local backend = Moonshine).
   3. Tap the mic and **speak a full sentence slowly** — watch if text appears WORD-BY-WORD while you talk (streaming) vs all-at-once at the end (still batch).
👉 Tell me what you see. I'll be watching logcat live for "Starting Moonshine live streaming session" + partial callbacks arriving mid-speech.

---
### ⚠️ ACTION NEEDED — phone disconnected
⚠️ Your phone dropped off adb mid-install (`no devices/emulators found`). The WS1 build is done and ready; I just can't push it until the phone is back.
👉 **Please:** re-plug the USB cable, **unlock the phone**, and if a "Allow USB debugging?" prompt appears tap **Allow** (tick "always allow"). If it still doesn't show, toggle USB debugging off/on in Developer Options, or try a different cable/port.
👉 Reply here once reconnected (or just say "back") and I'll immediately re-install + set the keyboard active + start the live logcat.

## user comments
1. phone plugged in
2.

---
### 🟢 GO — test WS1 streaming now
✅ Phone reconnected, WS1 dev build installed (`...40-g0503334de-unstable`), set as your active keyboard, live logcat running.
👉 **On your phone, do this now:**
   1. Open a text field (Notes / any chat box). The keyboard is already the dev build.
   2. Confirm **Moonshine** is the selected voice backend (Voice Input settings → local model backend = Moonshine). Your Groq key can stay saved — that's the point of the fix.
   3. Tap the mic 🎤 and **speak a full slow sentence** (e.g. "the quick brown fox jumps over the lazy dog and keeps running").
👉 Watch: does text appear **word-by-word while you're still talking** (streaming ✅) or **all at once at the end** (still batch ❌)?
👉 Reply with what you saw. I'm reading logcat for "Starting Moonshine live streaming session" + partial callbacks arriving mid-speech to confirm from the engine side. 

## user comments
1. the dev build is working like you described, but i think now we have to develop the side-by-side voice happening and typing able to happen sort of behavior.
2. i think this sort of setting, side-by-side behavior can be behind a particular setting in the appropriate place.
3. i think right now the settings are sort of here and there. we'll have to figure out somewhere in the roadmap to have them resegregated. voice command spellings are used to set up a specific time frame.
4.

---
### 🤖 WS1 shipped ✅ — starting WS4a (side-by-side)
✅ **WS1 committed + pushed to GitHub** (`38752126b` on `feature/moonshine-streaming`). Moonshine streaming works, confirmed by you.
ℹ️ On #1/#2: real side-by-side needs core IME surgery — the voice mode currently hands ALL input to a no-op "transaction" keyboard that swallows key taps, and tears the typing keyboard down. Making it work = keep the typing keyboard alive + route key taps to it + forward cursor updates to both + stop voice from fighting the keyboard's text region. I'm putting ALL of it behind a new **default-OFF** setting "Simultaneous voice + typing (experimental)" in Voice Input settings (your #2, the appropriate place) — so normal typing/voice can't regress.
ℹ️ v1 trade-off: in simultaneous mode, dictated text lands as finalized chunks (live word-by-word preview is paused only in this mode) to avoid two things writing the same text region at once. The full live-merge is a later commit. Flag me if you'd rather I prioritize the live-merge.
ℹ️ On #3 (settings scattered): noted as a roadmap item — settings re-segregation, later phase. Not touching it now beyond placing the new toggle sensibly.
⏳ Dispatching codex now to implement WS4a from a written spec (`docs/plans/moonshine-streaming-keyboard/WS4a-SPEC.md`). Will review its diff, build, commit, push. You test when you have time.

---
### 🤖 WS4a spec conflict — implementation continues
⏳ The engine changes are continuing; this does not block the rest of the work.
ℹ️ `userSettingToggleDataStore(...)` only accepts Android string-resource IDs, but the spec supplies two new literal strings and forbids editing resource files. Both cannot be true simultaneously.
👉 Choose **A**: allow two string-resource entries so the exact helper is used (**recommended**, normal project pattern), or **B**: keep the file limit and render the same toggle with the lower-level existing settings components.



## user comments
1. phone plugged in again
2. Choose **A**

---
### 🤖 WS4a choice received
✅ Using **A**: two normal string resources plus the exact `userSettingToggleDataStore(...)` row.
ℹ️ This is the only approved expansion beyond the spec's production-file list; all engine changes remain default-OFF gated.
⏳ Finishing the settings row, then independent diff review and static verification. No Gradle/build will run per WS4a.

---
### 🤖 WS4a shipped ✅ — starting WS2 (offline fallback)
✅ **WS4a committed + pushed** (`a0520d210`): experimental simultaneous voice+typing behind the default-OFF toggle in Voice Input settings. Build passed. Installing to your phone now (both WS1+WS4a).
ℹ️ On #3 (production offline fallback): I checked — the `readTimeout` you'd expect is already present and the old groq-fallback branch is already merged, so the structure exists. Remaining real holes I'm fixing now: (1) a **double-delivery bug** where Groq success committed the text twice; (2) **no offline pre-check** — with a Groq key set, airplane-mode dictation blocked on a socket timeout before falling back; (3) the **last-resort offline stage was unguarded** — a missing model crashed silently. Adding a connectivity check + guards.
ℹ️ Deferred to a later commit (noted): honoring the "use Groq" toggle for selection, and showing differentiated errors (401/timeout/offline) in the voice UI.
⏳ Dispatching codex from `docs/plans/moonshine-streaming-keyboard/WS2-SPEC.md`. Review → build → commit → push.

---
### 🤖 WS2 — privacy decision made (no new permission)
ℹ️ Codex found a real snag: FUTO **deliberately strips** the `ACCESS_NETWORK_STATE` permission (AndroidManifest.xml:35). A proper connectivity pre-check would need that permission back — a privacy regression against FUTO's whole ethos. **I'm NOT adding it** (won't touch your privacy posture without you deciding).
✅ Going permission-free instead: keeping the double-delivery fix + the offline-stage guard, and lowering the Groq **connect** timeout so an unreachable server fails fast and falls to local. Airplane mode already errors instantly, so this covers the real case.
👉 If you'd rather have a true connectivity pre-check and accept adding the `ACCESS_NETWORK_STATE` permission, say so and I'll switch. Otherwise proceeding permission-free.


## user comments
1. ask user for permissions
2. phone is connected.
3. i  installed the latest typing and speaking version from github but it doesnot let typing and speaking at once!?.

---
### 🤖 Agent reply — "typing + speaking at once" not working
ℹ️ **Most likely cause:** the feature ships **default-OFF**. It sits behind a toggle **"Simultaneous voice + typing (experimental)"** in **Voice Input settings**. If that switch is off, the app behaves exactly like before (voice takes over the field) — which matches what you saw.
👉 **Do this on your phone first:** Settings → Voice Input → turn ON **"Simultaneous voice + typing (experimental)"**, then open a text field, start Moonshine dictation, and try tapping keys while talking. Tell me if it now lets you type mid-dictation.
ℹ️ Being honest: WS4a was committed + built but I **never got to verify it on your device** (phone dropped off adb during install). So even with the toggle ON there may be a real bug.
⏳ **Codex** is auditing the WS4a code right now (read-only) to check the toggle actually keeps the keyboard live + routes key taps. **ChatGPT** consulted in parallel for known IME pitfalls, per your ask.
👉 Two quick answers help me: (a) was that toggle ON when you tested? (b) is a Groq API key still saved? (older question, confirms streaming path).
ℹ️ Noted your #1 "ask user for permissions" → for WS2 offline pre-check I'll add `ACCESS_NETWORK_STATE` back with your go-ahead (separate track, after this).




## user comments
1. see if this build is supposed to work like that?!? 
2. keep this lean move not relevant stuff to archive
3. it still shows the big voice mic button not the keyboard screen buttons so !!