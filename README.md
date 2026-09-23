# Locus (Android base app)

A Kotlin + Jetpack Compose scaffold for the "Instagram for math" app — live,
remixable function/geometry "builds," a feed, a chat-style messenger with a
math mode, and profiles you can follow. This is the native counterpart to
the interactive HTML prototype from earlier in the chat, aimed at your AI
class deliverable.

## Opening it

1. Unzip this folder somewhere.
2. Android Studio (Koala/2024.1 or newer) → **Open** → select the
   `locus-android` folder.
3. Android Studio will notice the Gradle wrapper jar is missing and offer to
   generate it — accept that (or run `gradle wrapper` yourself once if you
   have Gradle installed). This is normal for a hand-written project; it
   isn't an error in the code.
4. Let Gradle sync, then Run on an emulator or device (minSdk 24 / Android
   7.0+).

There's no backend to configure and no API keys needed — everything runs
from in-memory sample data, so it should build and launch immediately.

## What's here

- **Home** — a feed of `MathBuild`s rendered live on `Canvas` (not images).
  Each card has a slider that redraws the function/circle in real time,
  plus working like/comment/repost/save actions.
- **Builder** — pick a function family (sine, linear, quadratic, circle),
  drag sliders, layer on circle/triangle/point shapes, write a caption, and
  post — it's prepended to the feed and to your profile grid immediately.
- **Messages** — a conversation list and a thread view with a math-mode
  toggle (`Σ`) that live-previews `y=x^2` style input, plus bubbles that can
  carry a real mini live-rendered build.
- **Profile** — works for both you and anyone else (tap a username in the
  feed). Follow/unfollow updates the stats live. Grid of builds renders the
  same live `Canvas` thumbnails as the feed.

The math renderer (`ui/components/MathCanvas.kt`) is the one place all four
screens draw from, so a "build" means the same live thing everywhere.

- **Dynamic color (Material You)** — on Android 12+, the whole app's colors
  are pulled live from the phone's wallpaper/system theme, same as Google's
  own apps. On older Android versions (or with `dynamicColor = false`) it
  falls back to the custom "notebook" (light) / "chalkboard" (dark) palette.
  See `ui/theme/Theme.kt`.

## What's intentionally stubbed (this is a *base* app)

- **No real backend.** `data/LocusRepository.kt` is a single in-memory
  object holding everything — nothing persists across app restarts. Swap it
  for Firestore/Room (keep this object's function signatures the same and
  the screens won't need to change).
- **No auth.** `currentUsername` is a hardcoded constant.
- **No custom fonts.** `ui/theme/Type.kt` uses system serif/sans/mono as
  stand-ins for Fraunces / IBM Plex Sans / IBM Plex Mono. Drop `.ttf` files
  into `res/font` and swap the `FontFamily` values to match the web version
  exactly.
- **No launcher icon.** The manifest doesn't reference one, so Android uses
  a system default — use Android Studio's Image Asset Studio
  (`File → New → Image Asset`) to generate a real one in a couple of clicks.
- **Shapes are decorative, not draggable.** Adding a shape in Builder places
  it at a fixed default position rather than letting you drag it — real
  drag support would use `Modifier.pointerInput` + `detectDragGestures` on
  the canvas.
- **Chat "math mode" is regex-based**, not real LaTeX/MathJax rendering
  (`ui/util/Utils.kt#mathify`) — good enough for a demo, not a real typesetting engine.

## Natural next steps

- Firebase Auth + Firestore (or Room for local-only) behind `LocusRepository`
- A real equation parser (e.g. `exp4j` or a hand-rolled recursive-descent
  parser) so Builder accepts arbitrary formulas, not just four presets
- Hilt for dependency injection once there's a real repository to inject
- Drag gestures for shape placement in Builder
- Push notifications for likes/comments/messages
