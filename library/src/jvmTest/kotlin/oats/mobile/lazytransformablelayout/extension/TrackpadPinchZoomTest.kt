package oats.mobile.lazytransformablelayout.extension

import java.awt.Component
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrackpadPinchZoomTest {

    private val isMac = System.getProperty("os.name").contains("mac", ignoreCase = true)

    @Test
    fun `given the gesture package is exported, should return a remover`() {
        if (!isMac) return
        val remove = addMagnificationListener(JPanel()) {}
        assertNotNull(remove)
        remove()
    }

    @Test
    fun `given a listener on a panel, when a magnify is dispatched, should receive the magnification`() {
        if (!isMac) return
        val panel = JPanel()
        val received = mutableListOf<Double>()
        assertNotNull(addMagnificationListener(panel) { received += it })

        dispatchMagnify(panel, 0.25)

        assertEquals(listOf(0.25), received)
    }

    @Test
    fun `given a listener on a panel, when it is removed, should no longer receive magnifications`() {
        if (!isMac) return
        val panel = JPanel()
        val received = mutableListOf<Double>()
        val remove = assertNotNull(addMagnificationListener(panel) { received += it })

        remove()
        dispatchMagnify(panel, 0.25)

        assertTrue(received.isEmpty())
    }

    private fun dispatchMagnify(component: Component, magnification: Double) {
        val handler = Class.forName("$GESTURE_PACKAGE.GestureHandler")
        val event = Class.forName("$GESTURE_PACKAGE.MagnificationEvent")
            .getDeclaredConstructor(Double::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .newInstance(magnification)
        val notifier = handler.getDeclaredMethod("getNextNotifierForComponent", Component::class.java)
            .apply { isAccessible = true }
            .invoke(null, component)
            ?: return
        notifier.javaClass.getDeclaredMethod("recursivelyHandleMagnify", event.javaClass)
            .apply { isAccessible = true }
            .invoke(notifier, event)
    }

    private companion object {
        const val GESTURE_PACKAGE = "com.apple.eawt.event"
    }
}
