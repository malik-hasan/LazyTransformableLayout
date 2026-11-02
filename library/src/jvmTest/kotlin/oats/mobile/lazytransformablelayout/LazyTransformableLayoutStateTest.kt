package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import oats.mobile.lazytransformablelayout.extension.toLayoutAxes
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.extension.vertices
import oats.mobile.lazytransformablelayout.model.Parallelogram
import oats.mobile.lazytransformablelayout.model.Pivot
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LazyTransformableLayoutStateTest {

    private val viewportWidth = 500
    private val viewportHeight = 700
    private val layoutBounds = DpRect(0.dp, 0.dp, 10_000.dp, 6_000.dp)

    private fun state(
        bounds: DpRect = layoutBounds,
        rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
        zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
        initialZoomScale: Float = 1f
    ) = LazyTransformableLayoutState(
        initialLayoutBounds = bounds,
        initialRotationBounds = rotationBounds,
        initialZoomBounds = zoomBounds,
        initialZoomScale = initialZoomScale
    ).measured(viewportWidth, viewportHeight)

    private fun LazyTransformableLayoutState.snapToTranslation(
        translation: Offset,
        width: Int = viewportWidth,
        height: Int = viewportHeight
    ) {
        val center = (translation + Offset(width / 2f, height / 2f)).toLayoutAxes(zoomScale, rotationAngle)
        snapTo(centerOffset = DpOffset(center.x.dp, center.y.dp))
    }

    private fun DpOffset.toOffset() = Offset(x.value, y.value)

    private fun LazyTransformableLayoutState.layoutAt(position: Offset) = viewportToLayout(position).toOffset()

    private fun assertOffsetEquals(expected: Offset, actual: Offset, tolerance: Float = 0.05f) {
        assertEquals(expected.x, actual.x, tolerance, "x of $actual, expected $expected")
        assertEquals(expected.y, actual.y, tolerance, "y of $actual, expected $expected")
    }

    private fun LazyTransformableLayoutState.compositionRegion() = compositionBounds!!.second

    private fun LazyTransformableLayoutState.viewportBounds(buffer: Float) = Parallelogram(
        Rect(0f, 0f, viewportWidth.toFloat(), viewportHeight.toFloat()).inflate(buffer).vertices.map {
            layoutAt(it)
        }
    )

    @Test
    fun compositionBoundsAreTheInverseOfTheLayerTransform() {
        val random = Random(0)
        val buffer = 256f

        for (angle in listOf(0f, 30f, 90f, -135f, 200f)) {
            for (scale in listOf(0.2f, 1f, 3f)) {
                val state = state()
                state.snapTo(rotationAngle = angle, zoomScale = scale, centerOffset = DpOffset(3_000.dp, 2_000.dp))
                val region = state.compositionRegion()

                repeat(2_000) {
                    val point = Offset(
                        region.axisAlignedBoundingBox.left + random.nextFloat() * region.axisAlignedBoundingBox.width,
                        region.axisAlignedBoundingBox.top + random.nextFloat() * region.axisAlignedBoundingBox.height
                    )
                    val screen = point.transform(state.zoomScale, state.rotationAngle, state.translation)
                    val distanceToEdge = minOf(
                        abs(screen.x + buffer),
                        abs(screen.x - viewportWidth - buffer),
                        abs(screen.y + buffer),
                        abs(screen.y - viewportHeight - buffer)
                    )
                    if (distanceToEdge < 0.5f) return@repeat

                    val onScreen = screen.x in -buffer..viewportWidth + buffer
                            && screen.y in -buffer..viewportHeight + buffer
                    val half = 0.01f
                    val square = Parallelogram(
                        point + Offset(-half, -half),
                        point + Offset(half, -half),
                        point + Offset(half, half),
                        point + Offset(-half, half)
                    )
                    assertEquals(onScreen, square in region, "angle=$angle scale=$scale point=$point")
                }
            }
        }
    }

    @Test
    fun compositionBoundsContainTheRequeryMargin() {
        for (angle in listOf(0f, 45f, -100f)) {
            val state = state()
            state.snapTo(rotationAngle = angle, zoomScale = 1f, centerOffset = DpOffset(2_000.dp, 2_000.dp))
            assertTrue(state.viewportBounds(128f) in state.compositionRegion())
        }
    }

    @Test
    fun compositionBoundsOnlyChangeWhenTheViewportNearsTheirEdgeOrZoomsIn() {
        val state = state()
        state.snapTo(rotationAngle = 30f, zoomScale = 1f, centerOffset = DpOffset(2_000.dp, 2_000.dp))
        val initial = state.compositionBounds!!

        state.snapToTranslation(state.translation + Offset(30f, -30f))
        assertSame(initial, state.compositionBounds)

        state.snapBy(zoomFactor = 1.1f)
        assertSame(initial, state.compositionBounds)

        state.snapToTranslation(state.translation + Offset(300f, 0f))
        val panned = state.compositionBounds!!
        assertNotSame(initial, panned)

        state.snapBy(zoomFactor = 1.3f)
        assertNotSame(panned, state.compositionBounds)
    }

    private fun referencePanningBounds(rect: Rect, scale: Float, angle: Float): Parallelogram {
        val transformed = rect.vertices.map { it.transform(scale, angle) }

        var startIndex = 0
        var v0 = transformed[0]
        for (i in 1..3) {
            val v = transformed[i]
            if (v.x < v0.x || (v.x == v0.x && v.y < v0.y)) {
                v0 = v
                startIndex = i
            }
        }
        val v1 = transformed[(startIndex + 1) % 4]
        val v2 = transformed[(startIndex + 2) % 4]
        val v3 = transformed[(startIndex + 3) % 4]

        val left = v0.x
        val top = v1.y
        val right = (v2.x - viewportWidth).coerceAtLeast(left)
        val bottom = (v3.y - viewportHeight).coerceAtLeast(top)

        return Parallelogram(
            Offset(left, (v0.y - viewportHeight / 2f).coerceIn(top, bottom)),
            Offset((v1.x - viewportWidth / 2f).coerceIn(left, right), top),
            Offset(right, (v2.y - viewportHeight / 2f).coerceIn(top, bottom)),
            Offset((v3.x - viewportWidth / 2f).coerceIn(left, right), bottom)
        )
    }

    @Test
    fun panningBoundsMatchVertexComparison() {
        val random = Random(0)
        val rect = Rect(-3_000f, 500f, 7_000f, 6_500f)
        val state = state(DpRect(rect.left.dp, rect.top.dp, rect.right.dp, rect.bottom.dp))

        val angles = (-8..8).flatMap { quarter ->
            listOf(0f, 1e-4f, -1e-4f, 45f, 89.99f).map { quarter * 90f + it }
        } + List(200) { random.nextFloat() * 4_000f - 2_000f }

        for (angle in angles) {
            for (scale in listOf(0.3f, 1f, 4f)) {
                state.snapTo(rotationAngle = angle, zoomScale = scale)
                val reference = referencePanningBounds(rect, state.zoomScale, state.rotationAngle)

                repeat(50) {
                    val target = Offset(
                        random.nextFloat() * 60_000f - 30_000f,
                        random.nextFloat() * 60_000f - 30_000f
                    )
                    state.snapToTranslation(target)
                    val expected = reference.clamp(target)
                    val tolerance = 1e-3f * rect.maxDimension * state.zoomScale
                    assertTrue(
                        (state.translation - expected).getDistance() <= tolerance,
                        "angle=$angle scale=$scale target=$target: ${state.translation} != $expected"
                    )
                }
            }
        }
    }

    @Test
    fun collapsedPanningBoundsSpanTheFreeAxis() {
        val width = 800
        val height = 450
        val rect = Rect(0f, 0f, 10_000f, 6_000f)
        val state = LazyTransformableLayoutState(layoutBounds).measured(width, height)
            .apply { snapTo(rotationAngle = 30f, zoomScale = 1e-6f) }

        val transformed = rect.vertices.map { it.transform(state.zoomScale, state.rotationAngle) }
        val left = transformed.minOf { it.x }
        val top = transformed.minOf { it.y }
        val bottom = transformed.maxOf { it.y } - height
        assertTrue(bottom - top > 100f, "expected room to pan vertically, got ${bottom - top}")

        state.snapToTranslation(Offset(left, 1e6f), width, height)
        assertTrue((state.translation - Offset(left, bottom)).getDistance() < 1f, "${state.translation} != ${Offset(left, bottom)}")

        state.snapToTranslation(Offset(left, -1e6f), width, height)
        assertTrue((state.translation - Offset(left, top)).getDistance() < 1f, "${state.translation} != ${Offset(left, top)}")
    }

    @Test
    fun coveringTheViewportWinsOverMaxZoom() {
        val state = state(DpRect(0.dp, 0.dp, 300.dp, 300.dp), zoomBounds = 0.1f..1f)

        val coveringScale = maxOf(viewportWidth / 300f, viewportHeight / 300f)
        assertEquals(coveringScale, state.zoomScale)

        state.snapBy(zoomFactor = 2f)
        assertEquals(coveringScale, state.zoomScale)

        state.snapBy(zoomFactor = 0.5f)
        assertEquals(coveringScale, state.zoomScale)
    }

    @Test
    fun minimumZoomCoversTheViewportAtTheCurrentRotation() {
        val state = state()
        state.snapTo(zoomScale = 1e-9f)
        assertEquals(viewportHeight / 6_000f, state.zoomScale, 1e-5f)

        state.snapTo(rotationAngle = 90f, zoomScale = 1e-9f)
        assertEquals(viewportWidth / 6_000f, state.zoomScale, 1e-5f)
    }

    @Test
    fun centerOffsetDefaultsToTheCenterOfTheLayoutBounds() {
        assertEquals(DpOffset(5_000.dp, 3_000.dp), state().centerOffset)
    }

    @Test
    fun initialCenterOffsetIsClampedOnceMeasured() {
        val state = LazyTransformableLayoutState(layoutBounds, initialCenterOffset = DpOffset(-500.dp, 99_999.dp))
        assertEquals(DpOffset(-500.dp, 99_999.dp), state.centerOffset)

        state.measured(viewportWidth, viewportHeight)
        assertOffsetEquals(Offset(250f, 5_650f), state.centerOffset.toOffset())
    }

    @Test
    fun invalidBoundsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            LazyTransformableLayoutState(layoutBounds, initialRotationBounds = 10f..-10f)
        }
        assertFailsWith<IllegalArgumentException> {
            LazyTransformableLayoutState(layoutBounds, initialZoomBounds = 2f..1f)
        }
        assertFailsWith<IllegalArgumentException> {
            LazyTransformableLayoutState(layoutBounds, initialZoomBounds = 0f..1f)
        }
    }

    @Test
    fun conversionsAreUnspecifiedBeforeTheFirstMeasure() {
        val state = LazyTransformableLayoutState(layoutBounds)
        assertTrue(!state.layoutToViewport(DpOffset(1.dp, 1.dp)).isSpecified)
        assertEquals(DpOffset.Unspecified, state.viewportToLayout(Offset(1f, 1f)))
    }

    @Test
    fun viewportToLayoutReturnsADpOffset() {
        val state = state()
        val center = state.viewportToLayout(Offset(viewportWidth / 2f, viewportHeight / 2f))
        assertOffsetEquals(Offset(5_000f, 3_000f), center.toOffset())
    }

    @Test
    fun snapByHasNoEffectBeforeTheFirstMeasure() {
        val state = LazyTransformableLayoutState(layoutBounds)
        state.snapBy(rotationDelta = 10f, zoomFactor = 2f, panDelta = Offset(5f, 5f))
        assertEquals(0f, state.rotationAngle)
        assertEquals(1f, state.zoomScale)
        assertEquals(DpOffset(5_000.dp, 3_000.dp), state.centerOffset)
    }

    @Test
    fun theCenterOffsetIsAtTheCenterOfTheContentArea() {
        val padding = PaddingValues(start = 50.dp, top = 100.dp, end = 50.dp, bottom = 0.dp)
        val state = LazyTransformableLayoutState(layoutBounds).measured(viewportWidth, viewportHeight, padding)
        assertOffsetEquals(Offset(250f, 400f), state.layoutToViewport(state.centerOffset))
    }

    @Test
    fun contentPaddingShrinksTheAreaThatMustBeCovered() {
        val padding = PaddingValues(vertical = 100.dp)
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 1e-9f)
            .measured(viewportWidth, viewportHeight, padding)
        assertEquals(500f / 6_000f, state.zoomScale, 1e-5f)
    }

    @Test
    fun layoutAndViewportConversionsAreInverses() {
        val state = state()
        for (angle in listOf(0f, 30f, 90f, -135f)) {
            for (scale in listOf(0.5f, 1f, 4f)) {
                state.snapTo(rotationAngle = angle, zoomScale = scale)
                val position = DpOffset(5_100.dp, 2_900.dp)
                val viewportPosition = state.layoutToViewport(position)
                assertOffsetEquals(position.toOffset(), state.layoutAt(viewportPosition))
            }
        }
    }

    @Test
    fun snapToClampsEachValueToItsBounds() {
        val state = state(rotationBounds = -10f..10f, zoomBounds = 0.5f..4f)

        state.snapTo(rotationAngle = 5f, zoomScale = 2f, centerOffset = DpOffset(5_000.dp, 3_000.dp))
        assertEquals(5f, state.rotationAngle)
        assertEquals(2f, state.zoomScale)
        assertEquals(DpOffset(5_000.dp, 3_000.dp), state.centerOffset)

        state.snapTo(rotationAngle = 500f, zoomScale = 100f, centerOffset = DpOffset(-1_000.dp, -1_000.dp))
        assertEquals(10f, state.rotationAngle)
        assertEquals(4f, state.zoomScale)
        assertTrue(state.centerOffset.x.value > 0f && state.centerOffset.y.value > 0f)
    }

    @Test
    fun snapToKeepsValuesThatAreNull() {
        val state = state()
        state.snapTo(rotationAngle = 20f, zoomScale = 2f, centerOffset = DpOffset(4_000.dp, 2_500.dp))

        state.snapTo(zoomScale = 3f)

        assertEquals(20f, state.rotationAngle)
        assertEquals(3f, state.zoomScale)
        assertEquals(DpOffset(4_000.dp, 2_500.dp), state.centerOffset)
    }

    @Test
    fun snapToClampsTheCenterToThePanningBounds() {
        val state = state()
        state.snapTo(centerOffset = DpOffset(-100.dp, 99_999.dp))
        assertOffsetEquals(Offset(250f, 5_650f), state.centerOffset.toOffset())
    }

    @Test
    fun updateBoundsClampsTheStateToTheNewBounds() {
        val state = state()
        state.snapTo(rotationAngle = 40f, zoomScale = 3f, centerOffset = DpOffset(9_000.dp, 5_000.dp))

        state.updateBounds(
            layoutBounds = DpRect(0.dp, 0.dp, 2_000.dp, 2_000.dp),
            rotationBounds = -10f..10f,
            zoomBounds = 0.5f..2f
        )

        assertEquals(10f, state.rotationAngle)
        assertEquals(2f, state.zoomScale)
        val center = state.centerOffset.toOffset()
        assertTrue(center.x <= 2_000f && center.y <= 2_000f, "center $center is outside the new layout bounds")
    }

    @Test
    fun updateBoundsKeepsBoundsThatAreNull() {
        val state = state(rotationBounds = -10f..10f)

        state.updateBounds(zoomBounds = 1f..2f)
        state.snapTo(rotationAngle = 50f, zoomScale = 9f)

        assertEquals(10f, state.rotationAngle)
        assertEquals(2f, state.zoomScale)
    }

    @Test
    fun snapByZoomsAboutAViewportPivot() {
        val state = state()
        val pivot = Offset(100f, 200f)
        val before = state.layoutAt(pivot)

        state.snapBy(pivot = Pivot.Viewport(pivot), rotationDelta = 20f, zoomFactor = 1.5f)

        assertEquals(20f, state.rotationAngle, 1e-4f)
        assertEquals(1.5f, state.zoomScale, 1e-4f)
        assertOffsetEquals(before, state.layoutAt(pivot))
        assertOffsetEquals(pivot, state.layoutToViewport(DpOffset(before.x.dp, before.y.dp)))
    }

    @Test
    fun snapByZoomsAboutALayoutPivotWhereItCurrentlyIs() {
        val state = state()
        val position = DpOffset(5_100.dp, 3_100.dp)
        val before = state.layoutToViewport(position)

        state.snapBy(pivot = Pivot.Layout(position), rotationDelta = -30f, zoomFactor = 2f)

        assertOffsetEquals(before, state.layoutToViewport(position))
    }

    @Test
    fun snapByAboutTheContentCenterKeepsTheCenterOffset() {
        val state = state()
        val center = state.centerOffset

        state.snapBy(rotationDelta = 45f, zoomFactor = 2f)

        assertOffsetEquals(center.toOffset(), state.centerOffset.toOffset())
        assertEquals(45f, state.rotationAngle, 1e-4f)
    }

    @Test
    fun snapByPansTheContentOnScreenByThePanDelta() {
        val state = state()
        val position = DpOffset(5_000.dp, 3_000.dp)
        val before = state.layoutToViewport(position)

        state.snapBy(panDelta = Offset(30f, -40f))

        assertOffsetEquals(before + Offset(30f, -40f), state.layoutToViewport(position))
    }

    @Test
    fun snapByPansInViewportAxesWhenRotated() {
        val state = state()
        state.snapTo(rotationAngle = 90f)
        val position = state.centerOffset
        val before = state.layoutToViewport(position)

        state.snapBy(panDelta = Offset(10f, 0f))

        assertOffsetEquals(before + Offset(10f, 0f), state.layoutToViewport(position))
    }

    @Test
    fun snapByClampsPanToTheLayoutBounds() {
        val state = state()

        state.snapBy(panDelta = Offset(1e6f, 1e6f))

        assertOffsetEquals(Offset(250f, 350f), state.centerOffset.toOffset())
    }

    @Test
    fun snapByClampsRotationAndZoomToTheirBounds() {
        val state = state(rotationBounds = -10f..10f, zoomBounds = 0.5f..3f)

        state.snapBy(rotationDelta = 100f, zoomFactor = 100f)
        assertEquals(10f, state.rotationAngle)
        assertEquals(3f, state.zoomScale)

        state.snapBy(rotationDelta = -100f, zoomFactor = 0.001f)
        assertEquals(-10f, state.rotationAngle)
        assertEquals(0.5f, state.zoomScale)
    }

    @Test
    fun snapToBoundsCentersAndFitsTheBounds() {
        val state = state()

        state.snapToBounds(DpRect(1_000.dp, 1_000.dp, 1_400.dp, 1_200.dp), margin = 10.dp)

        assertEquals(1.2f, state.zoomScale, 1e-4f)
    }

    @Ignore
    @Test
    fun snapToBoundsCentersOnTheBoundsCenter() {
        val state = state()

        state.snapToBounds(DpRect(1_000.dp, 1_000.dp, 1_400.dp, 1_200.dp), margin = 10.dp)

        assertOffsetEquals(Offset(1_200f, 1_100f), state.centerOffset.toOffset())
    }

    @Test
    fun snapToBoundsFitsAtTheTargetRotation() {
        val state = state()

        state.snapToBounds(DpRect(1_000.dp, 1_000.dp, 1_400.dp, 1_200.dp), rotationAngle = 90f, margin = 10.dp)

        assertEquals(90f, state.rotationAngle)
        assertEquals(1.7f, state.zoomScale, 1e-4f)
    }

    @Test
    fun snapToBoundsBeforeTheFirstMeasureFitsOnceMeasured() {
        val state = LazyTransformableLayoutState(layoutBounds)

        state.snapToBounds(DpRect(0.dp, 0.dp, 400.dp, 200.dp), margin = 10.dp)
        assertEquals(DpOffset(200.dp, 100.dp), state.centerOffset)

        state.measured(viewportWidth, viewportHeight)
        assertEquals(1.2f, state.zoomScale, 1e-4f)
        assertOffsetEquals(Offset(250f / 1.2f, 350f / 1.2f), state.centerOffset.toOffset())
    }

    @Test
    fun savedStateRestoresTheSameState() {
        val state = state(rotationBounds = -90f..90f, zoomBounds = 0.2f..8f)
        state.snapTo(rotationAngle = 25f, zoomScale = 2.5f, centerOffset = DpOffset(4_000.dp, 2_500.dp))
        val saver = LazyTransformableLayoutState.saver(
            panFlingDecay = androidx.compose.animation.core.FloatExponentialDecaySpec(),
            rotationFlingDecay = androidx.compose.animation.core.FloatExponentialDecaySpec(),
            zoomFlingDecay = androidx.compose.animation.core.FloatExponentialDecaySpec()
        )

        val saved = with(saver) { SaverScope { true }.save(state) }!!
        val restored = saver.restore(saved)!!

        assertEquals(saved, with(saver) { SaverScope { true }.save(restored) })
        assertEquals(state.rotationAngle, restored.rotationAngle)
        assertEquals(state.zoomScale, restored.zoomScale)
        assertEquals(state.centerOffset, restored.centerOffset)
    }
}
