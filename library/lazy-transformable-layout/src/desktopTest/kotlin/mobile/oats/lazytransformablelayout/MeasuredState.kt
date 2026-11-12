package mobile.oats.lazytransformablelayout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.setMeasuredLayout(
    state: LazyTransformableLayoutState,
    width: Int,
    height: Int,
    contentPadding: PaddingValues = PaddingValues(),
    density: Float = 1f,
    onScope: (CoroutineScope) -> Unit = {}
) {
    setContent {
        onScope(rememberCoroutineScope())
        CompositionLocalProvider(LocalDensity provides Density(density)) {
            Box(Modifier.size((width / density).dp, (height / density).dp)) {
                LazyTransformableLayout(
                    state = state,
                    modifier = Modifier.testTag("layout"),
                    contentPadding = contentPadding,
                    overscrollEffect = null
                ) {}
            }
        }
    }
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
internal fun LazyTransformableLayoutState.measured(
    width: Int,
    height: Int,
    contentPadding: PaddingValues = PaddingValues(),
    density: Float = 1f
) = apply {
    runComposeUiTest { setMeasuredLayout(this@measured, width, height, contentPadding, density) }
}
