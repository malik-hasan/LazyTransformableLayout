A Compose lazy layout which supports lazy UI loading with transform gestures, i.e. zoom, rotation, and panning in any direction.

## Terminology

The API and its documentation use these terms:

| Term | Meaning | Units |
|---|---|---|
| **Layout space** | The space the `Positionable` items' bounds are in. `layoutBounds`, `centerOffset` and the bounds passed to `snapToBounds`/`animateToBounds` are in layout space. | dp |
| **Viewport** | The area the `LazyTransformableLayout` occupies on screen. Points and distances in the viewport, such as a `panDelta` or `Pivot.Viewport`, are measured from its top-left corner, like pointer positions. | px |
| **Content area** | The part of the viewport inside the layout's `contentPadding`. The `centerOffset` is placed at its center, and fitting and clamping use it, while items still draw underneath the padding. | px |

## Large images

The `lazy-transformable-layout-tiled-image` artifact adds `tiledImage`, which shows a raster too large to hold in memory. It loads only the tiles near the viewport, at a resolution matching the zoom scale, and overlaps them so no seams appear.

```kotlin
val imageSource = remember(bytes) { ImageSource.fromByteArray(bytes) }
val state = rememberImageTileSource(imageSource)
if (state is ImageTileSourceState.Ready) {
    val image = rememberTiledImageState(state.source, layoutState)

    LazyTransformableLayout(layoutState) {
        tiledImage(image)
    }
}
```

`rememberImageTileSource` decodes encoded images through [ZoomImage](https://github.com/panpf/zoomimage), and accepts an `ImageSource.Factory` to load the image asynchronously. Use `DrawTileSource` for large canvases and already-loaded bitmaps. Implement `TileSource` for anything else, such as a tile server. The overlap assumes opaque content.

## Trackpad pinch on desktop (macOS)

AWT doesn't pass trackpad pinch gestures to Compose Desktop, so the layout listens for them through the JDK's internal `com.apple.eawt.event` API. To enable pinch-to-zoom, export that package to your app:

```kotlin
compose.desktop {
    application {
        jvmArgs += "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED"
    }
}
```

Without the flag, or on other operating systems, pinch is ignored and everything else works as usual.

## Platform differences on desktop and web

Trackpad and mouse wheel input varies between operating systems and browsers on JVM and Wasm. Trackpad zoom has been tested on macOS in Firefox, Safari and the desktop JVM, but other platforms may behave differently or need extra handling. If zooming feels wrong or doesn't work on your platform, feel free to [raise an issue](https://github.com/malik-hasan/LazyTransformableLayout/issues).

Trackpad pinch doesn't work on the desktop JVM on Linux. This is a known limitation: AWT doesn't receive pinch gestures from X11 or Wayland, so it can't be fixed in this library. Two-finger scroll zoom still works.

Two-finger double tap to zoom out doesn't work on a macOS trackpad on the desktop JVM or web while Smart Zoom is enabled. This is a known limitation: macOS turns the gesture into a Smart Zoom event, which AWT doesn't pass to Compose Desktop and browsers don't reliably expose to pages. To use the gesture, turn off Smart Zoom in System Settings > Trackpad > Scroll & Zoom.
