package oats.mobile.lazytransformablelayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Constraints
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.extension.vertices
import oats.mobile.lazytransformablelayout.model.Parallelogram
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LazyTransformableLayoutStateTest {

    private val viewportWidth = 1080
    private val viewportHeight = 2000

    private fun state(layoutBounds: Rect = Rect(0f, 0f, 10_000f, 6_000f)) =
        LazyTransformableLayoutState(layoutBounds).apply {
            acceptConstraints(Constraints.fixed(viewportWidth, viewportHeight))
        }

    private fun LazyTransformableLayoutState.viewportBounds(buffer: Float): Parallelogram {
        val (offset, angle, scale) = this
        return Parallelogram(
            Rect(0f, 0f, viewportWidth.toFloat(), viewportHeight.toFloat()).inflate(buffer).vertices.map {
                (it + offset).transform(scale = 1 / scale, angle = -angle)
            }
        )
    }

    @Test
    fun compositionBoundsAreTheInverseOfTheLayerTransform() {
        val random = Random(0)
        val buffer = 256f

        for (angle in listOf(0f, 30f, 90f, -135f, 200f)) {
            for (scale in listOf(0.2f, 1f, 3f)) {
                val state = state()
                state.set(angle = angle, scale = scale, offset = Offset(1_000f, 1_000f))
                val (offset, actualAngle, actualScale) = state
                val region = state.compositionBounds!!.first

                repeat(2_000) {
                    val point = Offset(
                        region.bounds.left + random.nextFloat() * region.bounds.width,
                        region.bounds.top + random.nextFloat() * region.bounds.height
                    )
                    // where the layer blocks draw this layout point on screen
                    val screen = point.transform(actualScale, actualAngle) - offset
                    val distanceToEdge = minOf(
                        abs(screen.x + buffer),
                        abs(screen.x - viewportWidth - buffer),
                        abs(screen.y + buffer),
                        abs(screen.y - viewportHeight - buffer)
                    )
                    if (distanceToEdge < 0.5f) return@repeat

                    val onScreen = screen.x in -buffer..viewportWidth + buffer
                            && screen.y in -buffer..viewportHeight + buffer
                    // a tiny square around the point, well within the skipped distance to the edge
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
            state.set(angle = angle, scale = 1f, offset = Offset(2_000f, 2_000f))
            assertTrue(state.viewportBounds(128f) in state.compositionBounds!!.first)
        }
    }

    /** panningBounds as it was built before startIndex was derived from the angle */
    private fun referencePanningBounds(layoutBounds: Rect, scale: Float, angle: Float): Parallelogram {
        val transformed = layoutBounds.vertices.map { it.transform(scale, angle) }

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
        val layoutBounds = Rect(-3_000f, 500f, 7_000f, 6_500f)
        val state = state(layoutBounds)

        val angles = (-8..8).flatMap { quarter ->
            listOf(0f, 1e-4f, -1e-4f, 45f, 89.99f).map { quarter * 90f + it }
        } + List(200) { random.nextFloat() * 4_000f - 2_000f }

        for (angle in angles) {
            for (scale in listOf(0.3f, 1f, 4f)) {
                state.set(angle = angle, scale = scale)
                val reference = referencePanningBounds(layoutBounds, state.scale, state.angle)

                repeat(50) {
                    val target = Offset(
                        random.nextFloat() * 60_000f - 30_000f,
                        random.nextFloat() * 60_000f - 30_000f
                    )
                    state.set(offset = target)
                    val expected = reference.clamp(target)
                    val tolerance = 1e-3f * layoutBounds.maxDimension * state.scale
                    assertTrue(
                        (state.offset - expected).getDistance() <= tolerance,
                        "angle=$angle scale=$scale target=$target: ${state.offset} != $expected"
                    )
                }
            }
        }
    }

    @Test
    fun collapsedPanningBoundsSpanTheFreeAxis() {
        // a landscape viewport rotated 30 degrees: at min zoom its width fills the rotated layout's width,
        // so horizontal panning collapses but vertical panning is still free
        val width = 2_000
        val height = 1_080
        val layoutBounds = Rect(0f, 0f, 10_000f, 6_000f)
        val state = LazyTransformableLayoutState(layoutBounds).apply {
            acceptConstraints(Constraints.fixed(width, height))
            set(angle = 30f, scale = 1e-6f)
        }

        val transformed = layoutBounds.vertices.map { it.transform(state.scale, state.angle) }
        val left = transformed.minOf { it.x }
        val top = transformed.minOf { it.y }
        val bottom = transformed.maxOf { it.y } - height
        assertTrue(bottom - top > 100f, "expected room to pan vertically, got ${bottom - top}")

        state.set(offset = Offset(left, 1e6f))
        assertTrue((state.offset - Offset(left, bottom)).getDistance() < 1f, "${state.offset} != ${Offset(left, bottom)}")

        state.set(offset = Offset(left, -1e6f))
        assertTrue((state.offset - Offset(left, top)).getDistance() < 1f, "${state.offset} != ${Offset(left, top)}")
    }

    @Test
    fun compositionBoundsOnlyChangeWhenTheViewportNearsTheirEdgeOrZoomsIn() {
        val state = state()
        state.set(angle = 30f, scale = 1f, offset = Offset(2_000f, 2_000f))
        val initial = state.compositionBounds!!

        // well inside the 128px requery margin
        state.set(offset = state.offset + Offset(50f, -50f))
        assertSame(initial, state.compositionBounds)

        // zooming in about the viewport center, less than the requery factor
        val center = Offset(viewportWidth / 2f, viewportHeight / 2f)
        state.transform(rotationDelta = 0f, zoomFactor = 1.1f, panDelta = Offset.Zero, centroid = center)
        assertSame(initial, state.compositionBounds)

        // past the requery margin
        state.set(offset = state.offset + Offset(300f, 0f))
        val panned = state.compositionBounds!!
        assertNotSame(initial, panned)

        // zooming in past the requery factor
        state.transform(rotationDelta = 0f, zoomFactor = 1.3f, panDelta = Offset.Zero, centroid = center)
        assertNotSame(panned, state.compositionBounds)
    }
}
