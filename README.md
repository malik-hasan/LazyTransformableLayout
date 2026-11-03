A Compose lazy layout which supports lazy UI loading with transform gestures, i.e. zoom, rotation, and panning in any direction.

## Terminology

The API and its documentation use these terms:

| Term | Meaning | Units |
|---|---|---|
| **Layout space** | The space the `Positionable` items' bounds are in. `layoutBounds`, `centerOffset` and the bounds passed to `snapToBounds`/`animateToBounds` are in layout space. | dp |
| **Viewport** | The area the `LazyTransformableLayout` occupies on screen. Points and distances in the viewport, such as a `panDelta` or `Pivot.Viewport`, are measured from its top-left corner, like pointer positions. | px |
| **Content area** | The part of the viewport inside the layout's `contentPadding`. The `centerOffset` is placed at its center, and fitting and clamping use it, while items still draw underneath the padding. | px |

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
