# Productivity Improvements Overview

This document outlines ideas to streamline text entry and navigation in the keyboard, with special emphasis on cursor control and selection workflows.

## Power-User Cursor and Selection Controls
- **Two-finger drag to select**: Treat a second finger drag as a selection gesture, selecting the range between the initial cursor position and the drag end point.
- **Double-drag word/line selection**: Interpret a rapid second drag after a placement drag as a request to expand to the nearest word boundary or entire line.
- **Sticky selection modifiers**: Allow a long-press on Shift or a dedicated selection toggle to keep selection mode active while moving the cursor with drag or arrow gestures.
- **Precision vs. velocity mode**: Switch between pixel-precise movement and accelerated movement based on drag distance or pressure, improving navigation in long documents.
- **Haptic/visual feedback**: Provide subtle haptics and highlight updates when selection begins, expands, or snaps to word/line boundaries to reinforce state changes.

## Other Productivity Features to Explore
- **Clipboard search completion**: Finish the in-clipboard search integration described in `PLAN.md`, wiring focus state, showing the keyboard under the clipboard window, and routing keystrokes into the search field.
- **Quick Switch enhancements**: Expand the existing Quick Switch to optionally keep a short history (3–5 apps) with configurable shortcuts for rapid app hopping.
- **AI-assisted templates**: Extend AI Reply and "AI Generate" clipboard actions with reusable templates and macros for common responses while keeping offline defaults safe.
- **Voice input shortcuts**: Add toggles for auto-punctuation and commands like "newline" or "undo" to speed hands-free entry without losing control.
- **Editable gesture map**: Offer a settings page for customizing gesture bindings (e.g., selecting words, lines, or paragraphs) to fit different user preferences.

These ideas can be iterated independently and combined to deliver a faster, more controllable typing experience for advanced users.
