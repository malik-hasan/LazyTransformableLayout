package oats.mobile.lazytransformablelayout.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "LazyTransformableLayout Demo") {
        App()
    }
}
