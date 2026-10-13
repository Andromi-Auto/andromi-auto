package com.andrerinas.openheadunit.utils

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

/**
 * What the keyboard's Enter key does in a text dialog. On a short, wide head-unit screen the
 * keyboard can cover the dialog's buttons, so Enter on the last field has to press OK itself.
 */
object TextDialogKeyPolicy {

    /** The IME action a field asks for: Next moves to the following field, Done presses OK. */
    fun imeAction(isLastField: Boolean): Int =
        if (isLastField) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT

    /**
     * True when this editor action should press OK. Most keyboards send Done; some, and hardware
     * keys, send no action and only an Enter key event, which counts once, on key down.
     */
    fun isSubmit(actionId: Int, keyCode: Int?, keyAction: Int?): Boolean = when (actionId) {
        EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEND -> true
        EditorInfo.IME_NULL -> keyCode == KeyEvent.KEYCODE_ENTER && keyAction == KeyEvent.ACTION_DOWN
        else -> false
    }
}
