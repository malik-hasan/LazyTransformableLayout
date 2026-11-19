package mobile.oats.lazytransformablelayout.extension

import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatDecayAnimationSpecTest {

    private val spec: FloatDecayAnimationSpec = FloatExponentialDecaySpec()

    @Test
    fun `given zero velocity when reading the value should return zero at any play time`() {
        listOf(0L, 100_000_000L, Long.MAX_VALUE / 2).forEach { playTime ->
            val value = spec.valueAt(playTime, 0f)
            assertFalse(value.isNaN(), "value at $playTime")
            assertEquals(0f, value, "value at $playTime")
        }
    }

    @Test
    fun `given zero velocity should report zero duration`() {
        assertEquals(0L, spec.durationNanos(0f))
    }

    @Test
    fun `given a nonzero velocity should report a positive duration`() {
        assertTrue(spec.durationNanos(2000f) > 0L)
        assertTrue(spec.durationNanos(-2000f) > 0L)
    }
}
