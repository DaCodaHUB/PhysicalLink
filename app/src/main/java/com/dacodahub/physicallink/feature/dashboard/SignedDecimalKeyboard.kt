package com.dacodahub.physicallink.feature.dashboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/** Compose 1.10's Decimal hint omits SIGNED. Preserve the connection and all other IME options. */
internal fun signedDecimalInputType(inputType: Int): Int =
    if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER && inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0)
        inputType or InputType.TYPE_NUMBER_FLAG_SIGNED else inputType

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SignedDecimalKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = { request, nextHandler ->
        nextHandler.startInputMethod(object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                val connection = request.createInputConnection(outAttributes)
                outAttributes.inputType = signedDecimalInputType(outAttributes.inputType)
                return connection
            }
        })
    }, content = content)
}
