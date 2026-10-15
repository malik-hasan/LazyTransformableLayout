package oats.mobile.lazytransformablelayout

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.test.ext.junit.runners.AndroidJUnit4
import oats.mobile.lazytransformablelayout.model.Item
import oats.mobile.lazytransformablelayout.model.Parallelogram
import oats.mobile.lazytransformablelayout.model.Positionable
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ContentDeviceBenchmark {

    private class PlainPositionable(override val bounds: DpRect) : Positionable

    private class StatePositionable(initialBounds: DpRect) : Positionable {
        override var bounds by mutableStateOf(initialBounds)
    }

    private val density = Density(2.75f)

    private fun viewport(center: Offset, w: Float, h: Float, angleDeg: Float): Parallelogram {
        val a = Math.toRadians(angleDeg.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        fun pt(x: Float, y: Float) = Offset(center.x + x * c - y * s, center.y + x * s + y * c)
        return Parallelogram(pt(-w / 2, -h / 2), pt(w / 2, -h / 2), pt(w / 2, h / 2), pt(-w / 2, h / 2))
    }

    private inline fun time(iterations: Int, block: () -> Int): Pair<Double, Int> {
        var sink = 0
        repeat(iterations * 3) { sink += block() }
        val start = System.nanoTime()
        repeat(iterations) { sink += block() }
        return (System.nanoTime() - start) / iterations / 1e6 to sink
    }

    private fun flatCache(positionables: List<Positionable>) = with(density) {
        FloatArray(positionables.size * 4).also { bounds ->
            positionables.fastForEachIndexed { i, positionable ->
                positionable.bounds.run {
                    bounds[i * 4] = left.toPx()
                    bounds[i * 4 + 1] = top.toPx()
                    bounds[i * 4 + 2] = right.toPx()
                    bounds[i * 4 + 3] = bottom.toPx()
                }
            }
        }
    }

    private fun scanFlatCache(
        bounds: FloatArray,
        zIndices: FloatArray,
        minItemDimension: Float,
        viewportBounds: Parallelogram
    ): Int {
        val out = mutableListOf<Item>()
        for (i in zIndices.indices) {
            val left = bounds[i * 4]
            val top = bounds[i * 4 + 1]
            val right = bounds[i * 4 + 2]
            val bottom = bounds[i * 4 + 3]
            if (maxOf(right - left, bottom - top) >= minItemDimension
                && viewportBounds.intersects(left, top, right, bottom)
            ) out += Item(i, Rect(left, top, right, bottom), zIndices[i])
        }
        return out.size
    }

    private fun scanLiveToRect(
        positionables: List<Positionable>,
        minItemDimension: Float,
        viewportBounds: Parallelogram
    ): Int = with(density) {
        val out = mutableListOf<Item>()
        positionables.fastForEachIndexed { index, positionable ->
            val bounds = positionable.bounds.toRect()
            if (bounds.maxDimension >= minItemDimension && viewportBounds.intersects(bounds))
                out += Item(index, bounds, positionable.zIndex)
        }
        out.size
    }

    private fun scanLiveAllocationFree(
        positionables: List<Positionable>,
        minItemDimension: Float,
        viewportBounds: Parallelogram
    ): Int = with(density) {
        val out = mutableListOf<Item>()
        positionables.fastForEachIndexed { index, positionable ->
            val bounds = positionable.bounds
            val left = bounds.left.toPx()
            val top = bounds.top.toPx()
            val right = bounds.right.toPx()
            val bottom = bounds.bottom.toPx()
            if (maxOf(right - left, bottom - top) >= minItemDimension
                && viewportBounds.intersects(left, top, right, bottom)
            ) out += Item(index, Rect(left, top, right, bottom), positionable.zIndex)
        }
        out.size
    }

    @Test
    fun benchmark() {
        val observer = SnapshotStateObserver { it() }.apply { start() }
        val observerScope = Any()
        fun observed(block: () -> Int): Int {
            var result = 0
            observer.observeReads(observerScope, {}) { result = block() }
            return result
        }

        try {
            for (n in listOf(1_000, 10_000, 100_000)) {
                val random = Random(n)
                val world = sqrt(n.toFloat()) * 150f * density.density
                val dpBounds = List(n) {
                    val x = random.nextFloat() * world / density.density
                    val y = random.nextFloat() * world / density.density
                    val w = 20f + random.nextFloat() * 180f
                    val h = 20f + random.nextFloat() * 180f
                    DpRect(x.dp, y.dp, (x + w).dp, (y + h).dp)
                }
                val plain = dpBounds.map(::PlainPositionable)
                val state = dpBounds.map(::StatePositionable)
                val iterations = if (n >= 100_000) 20 else 100

                val (contentBuildMs, _) = time(iterations / 2) {
                    Content { itemsIndexed(plain) { _, _ -> } }.itemCount
                }
                val (cacheBuildMs, _) = time(iterations / 2) { flatCache(plain).size }
                Log.i(TAG, "n=%-7d contentBuild=%.3fms flatCacheBuild=%.3fms".format(n, contentBuildMs, cacheBuildMs))

                val cache = flatCache(plain)
                val zIndices = FloatArray(n)

                val center = Offset(world / 2, world / 2)
                val zoomOut = world / 1080f
                val scenarios = listOf(
                    Triple("zoomed-in ", viewport(center, 1080f + 512f, 2400f + 512f, 30f), 0.5f),
                    Triple(
                        "zoomed-out",
                        viewport(center, (1080f + 512f) * zoomOut, (2400f + 512f) * zoomOut, 30f),
                        0.5f * zoomOut
                    )
                )
                for ((name, vp, minItemDimension) in scenarios) {
                    val results = listOf(
                        "flatCache" to time(iterations) { scanFlatCache(cache, zIndices, minItemDimension, vp) },
                        "liveToRect" to time(iterations) {
                            observed { scanLiveToRect(plain, minItemDimension, vp) }
                        },
                        "liveToRectState" to time(iterations) {
                            observed { scanLiveToRect(state, minItemDimension, vp) }
                        },
                        "liveAllocFree" to time(iterations) {
                            observed { scanLiveAllocationFree(plain, minItemDimension, vp) }
                        },
                        "liveAllocFreeState" to time(iterations) {
                            observed { scanLiveAllocationFree(state, minItemDimension, vp) }
                        }
                    )
                    val visible = results.map { (_, result) -> result.second / (iterations * 4) }.distinct()
                    Log.i(
                        TAG,
                        "n=%-7d %s visible=%s %s".format(
                            n,
                            name,
                            visible,
                            results.joinToString(" ") { (label, result) -> "%s=%.3fms".format(label, result.first) }
                        )
                    )
                }
            }
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    private companion object {
        const val TAG = "ContentBench"
    }
}
