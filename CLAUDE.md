# Rules

## Comments

- Only add comments when strictly necessary.
- Add KDoc for public APIs only.

## Tests

- Name tests `given X, when Y, should Z`, in backticks. The `when Y` part is optional, e.g. `given a bounded layout, should clamp the zoom scale`.
- Kotlin/Native and Wasm reject commas in test names, so in `commonTest` drop them: `given X when Y should Z`.
