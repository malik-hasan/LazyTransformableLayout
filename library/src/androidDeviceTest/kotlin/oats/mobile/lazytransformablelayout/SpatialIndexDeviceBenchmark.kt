package oats.mobile.lazytransformablelayout

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
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
class SpatialIndexDeviceBenchmark {

    private class P(override val bounds: DpRect) : Positionable

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

    @Test
    fun benchmark() {
        for (n in listOf(1_000, 10_000, 100_000)) {
            val random = Random(n)
            val world = sqrt(n.toFloat()) * 150f
            val positionables = List(n) {
                val x = random.nextFloat() * world
                val y = random.nextFloat() * world
                val w = 20f + random.nextFloat() * 180f
                val h = 20f + random.nextFloat() * 180f
                P(DpRect(x.dp, y.dp, (x + w).dp, (y + h).dp))
            }
            val iterations = if (n >= 100_000) 20 else 100

            val content = Content { itemsIndexed(positionables) { _, _ -> } }
            val (indexBuildMs, _) = time(iterations / 2) { SpatialIndex(content.intervals, 1f); 0 }
            val index = SpatialIndex(content.intervals, 1f)

            Log.i(TAG, "n=%-7d indexBuild=%.3fms".format(n, indexBuildMs))

            val center = Offset(world / 2, world / 2)
            val zoomOut = world / 1080f
            val scenarios = listOf(
                Triple("zoomed-in ", viewport(center, 1080f + 512f, 2400f + 512f, 30f), 0.5f),
                Triple("zoomed-out", viewport(center, (1080f + 512f) * zoomOut, (2400f + 512f) * zoomOut, 30f), 0.5f * zoomOut)
            )
            for ((name, vp, minItemDimension) in scenarios) {
                val (queryMs, visible) = time(iterations) {
                    val out = mutableListOf<Item>()
                    index.query(minItemDimension, vp, out)
                    out.size
                }
                Log.i(
                    TAG,
                    "n=%-7d %s visible=%-6d query=%.3fms".format(n, name, visible / (iterations * 4), queryMs)
                )
            }
        }
    }

    private companion object {
        const val TAG = "SpatialBench"
    }
}
