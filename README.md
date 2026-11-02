A Compose lazy layout which supports lazy UI loading with transform gestures, i.e. zoom, rotation, and panning in any direction.

## Terminology

The API and its documentation use these terms:

| Term | Meaning | Units |
|---|---|---|
| **Layout space** | The space the `Positionable` items' bounds are in. `layoutBounds`, `centerOffset` and the bounds passed to `snapToBounds`/`animateToBounds` are in layout space. | dp |
| **Viewport** | The area the `LazyTransformableLayout` occupies on screen. Points and distances in the viewport, such as a `panDelta` or `Pivot.Viewport`, are measured from its top-left corner, like pointer positions. | px |
| **Content area** | The part of the viewport inside the layout's `contentPadding`. The `centerOffset` is placed at its center, and fitting and clamping use it, while items still draw underneath the padding. | px |
