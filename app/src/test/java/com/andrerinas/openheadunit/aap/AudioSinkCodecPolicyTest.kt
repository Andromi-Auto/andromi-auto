package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioSinkCodecPolicyTest {

    @Test
    fun `PCM is not AAC and both AAC types are`() {
        assertEquals(false, AudioSinkCodecPolicy.isAac(1))
        assertEquals(true, AudioSinkCodecPolicy.isAac(2))
        assertEquals(true, AudioSinkCodecPolicy.isAac(4))
    }

    @Test
    fun `a video codec or an unknown type answers nothing`() {
        // The caller falls back to the setting rather than guessing from a type it cannot read.
        assertNull(AudioSinkCodecPolicy.isAac(3))
        assertNull(AudioSinkCodecPolicy.isAac(7))
        assertNull(AudioSinkCodecPolicy.isAac(0))
    }
}
