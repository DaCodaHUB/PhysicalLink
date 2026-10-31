package com.dacodahub.physicallink.feature.dashboard

import android.text.InputType
import org.junit.*
import org.junit.Assert.*

class SignedDecimalKeyboardTest {
    @Test fun `decimal fields request signed decimal input while precision and text hints remain unchanged`() {
        val decimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val signed = signedDecimalInputType(decimal)
        assertEquals(decimal or InputType.TYPE_NUMBER_FLAG_SIGNED, signed)
        assertEquals(signed, signedDecimalInputType(signed))
        assertEquals(InputType.TYPE_CLASS_NUMBER, signedDecimalInputType(InputType.TYPE_CLASS_NUMBER))
        assertEquals(InputType.TYPE_CLASS_TEXT, signedDecimalInputType(InputType.TYPE_CLASS_TEXT))
    }
}
