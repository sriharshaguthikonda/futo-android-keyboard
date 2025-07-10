# Plan: Implement In-Clipboard Search with Main Keyboard

This plan outlines the steps to implement a search functionality within the clipboard history window, using the main keyboard for input.

## Phase 1: Core Mechanics - State, Layout, and Basic Input (No Action Bar)

- [ ] **1. Create `PLAN.md` for Feature Tracking:**
    - [x] Create this `PLAN.md` file.
    - [x] Populate with initial high-level tasks.

- [ ] **2. State Management for "Clipboard Search Focus Mode":**
    - [ ] Explore `UixManager.kt` and `LatinIME.kt` for current state/visibility management.
    - [ ] Define `isClipboardSearchFocused: MutableState<Boolean>` in `UixManager.kt`.
    - [ ] Update `PLAN.md` with this sub-task.

- [ ] **3. Triggering "Clipboard Search Focus Mode" via `ClipboardHistoryComposables.kt`:**
    - [ ] Modify `ClipboardHistoryWindowContent`: on search `TextField` focus, call `UixManager.setClipboardSearchFocus(true)`.
    - [ ] On lose focus (or other exit conditions later), call `UixManager.setClipboardSearchFocus(false)`.
    - [ ] Update `PLAN.md`.

- [ ] **4. Conditional Main Keyboard Visibility (No Action Bar) in `LatinIME.kt` / `UixManager.kt`:**
    - [ ] Modify Keyboard Rendering Logic: Show main keyboard view if `isClipboardSearchFocused` is true and clipboard history is active.
    - [ ] Implement logic to **hide/not render the main keyboard's standard action bar** when `isClipboardSearchFocused` is true.
    - [ ] Update `PLAN.md`.

- [ ] **5. Layout Adjustments for Combined Clipboard Search + Main Keyboard View:**
    - [ ] Modify `UixManager.kt` / `LatinIME.kt` Composable Structure: Arrange clipboard history UI above the main keyboard (no action bar) when `isClipboardSearchFocused` is true.
    *   [ ] Ensure `LatinIME.onComputeInsets` correctly reflects the new total IME window height.
    - [ ] Update `PLAN.md`.

- [ ] **6. Implement Basic Input Redirection from Main Keyboard to Search Field:**
    - [ ] Expose `searchQuery` updater (state or lambda) from `ClipboardHistoryComposables.kt` up to `UixManager` and make accessible to `LatinIME`/`InputLogicHandler`.
    - [ ] Modify `InputLogicHandler.java` (or `InputLogic.java`):
        - [ ] If `isClipboardSearchFocused` is true, intercept `commitText`, `sendKeyEvent`, `deleteSurroundingText`.
        - [ ] Route these actions to update the `searchQuery` instead of the app's `InputConnection`. Start with basic text and delete.
    - [ ] Update `PLAN.md`.

- [ ] **7. Initial Integration and Testing:**
    - [ ] Verify: TextField focus triggers main keyboard (no action bar).
    - [ ] Verify: Clipboard history UI stays above the main keyboard.
    - [ ] Verify: Basic typed text appears in the search field.
    - [ ] Verify: Basic deletion affects the search field.
    - [ ] Debug and iterate.
    - [ ] Update `PLAN.md`.

## Phase 2: Polish and Full Integration (Future Plan)
- [ ] Handling "Enter" key for search.
- [ ] Robust focus management.
- [ ] Smooth transitions.
- [ ] Advanced keyboard features for search (e.g., suggestions - TBD).
