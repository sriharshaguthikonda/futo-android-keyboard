package org.futo.inputmethod.engine.general

import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.InputConnectionInternalComposingWrapper
import org.futo.inputmethod.latin.SupportsNonComposing
import org.futo.inputmethod.latin.VoiceInputAlternativeIC
import org.futo.inputmethod.latin.VoiceInputAlternativeICComposing
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.VOICE_SIMULTANEOUS_TYPING
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.utils.TextContext
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2

class ActionInputTransactionIME(val helper: IMEHelper) : IMEInterface, ActionInputTransaction {
    val ic = if(helper.context.getSetting(VoiceInputAlternativeIC) && SupportsNonComposing) {
        InputConnectionInternalComposingWrapper(
            helper.context.getSetting(VoiceInputAlternativeICComposing),
            true,
            helper.getCurrentInputConnection())
    } else {
        helper.getCurrentInputConnection()
    }

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDeviceUnlocked() {}
    override fun onStartInput() {}
    override fun onOrientationChanged() {}
    override fun onFinishInput() {}
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {
        if(ic is InputConnectionInternalComposingWrapper) {
            if(!ic.mightBeBelated(oldSelStart, oldSelEnd, newSelStart, newSelEnd)) {
                ic.cursorUpdated(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
            }
        }
    }

    override fun isGestureHandlingAvailable(): Boolean = false
    override fun onEvent(event: Event) {}
    override fun onStartBatchInput() {}
    override fun onUpdateBatchInput(batchPointers: InputPointers?) {}
    override fun onEndBatchInput(batchPointers: InputPointers?) {}
    override fun onCancelBatchInput() {}
    override fun onCancelInput() {}
    override fun onFinishSlidingInput() {}
    override fun onCustomRequest(requestCode: Int): Boolean = false
    override fun onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?) {}
    override fun onMoveDeletePointer(steps: Int) {}
    override fun onUpWithDeletePointerActive() {}
    override fun onUpWithPointerActive() {}
    override fun onSwipeLanguage(direction: Int) {}
    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {}
    override fun clearUserHistoryDictionaries() {}
    override fun requestSuggestionRefresh() {}
    override fun onLayoutUpdated(layout: KeyboardLayoutSetV2) { }

    override val textContext: TextContext = TextContext(
        beforeCursor = ic?.getTextBeforeCursor(Constants.VOICE_INPUT_CONTEXT_SIZE, 0),
        afterCursor = ic?.getTextAfterCursor(Constants.VOICE_INPUT_CONTEXT_SIZE, 0)
    )

    private var isFinished = false
    private var partialText = ""
    override fun updatePartial(text: String) {
        if (isFinished) {
            android.util.Log.w("ActionInputTxn", "updatePartial dropped — transaction finished: [$text]")
            return
        }
        if (helper.context.getSetting(VOICE_SIMULTANEOUS_TYPING)) {
            partialText = text
            // ponytail: v1 suppresses live composing partials; add a multi-writer merge later.
            return
        }
        if (ic == null) {
            android.util.Log.w("ActionInputTxn", "updatePartial dropped — null InputConnection: [$text]")
            return
        }
        android.util.Log.d("ActionInputTxn", "updatePartial text=[$text] icType=${ic.javaClass.simpleName}")
        helper.requestCursorUpdate()
        partialText = text
        ic?.setComposingText(
            partialText,
            1
        )

        (ic as? InputConnectionInternalComposingWrapper)?.send()
    }

    override fun commit(text: String) {
        if (isFinished) return
        helper.requestCursorUpdate()
        isFinished = true
        if (helper.context.getSetting(VOICE_SIMULTANEOUS_TYPING)) {
            // ponytail: finalize the shared composing span only for simultaneous typing.
            ic?.finishComposingText()
        }
        ic?.commitText(
            text,
            1
        )
        helper.endInputTransaction(this)
        (ic as? InputConnectionInternalComposingWrapper)?.send()
    }

    override fun cancel() {
        helper.requestCursorUpdate()
        if (helper.context.getSetting(VOICE_SIMULTANEOUS_TYPING)) {
            helper.endInputTransaction(this)
        } else {
            commit(partialText)
        }
        (ic as? InputConnectionInternalComposingWrapper)?.send()
    }

    fun ensureFinished() {
        isFinished = true
    }
}
