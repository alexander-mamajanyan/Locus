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

Firebase is used for email/password authentication; the app's social and math
posts, user profiles, likes, comments, reposts, saves, and follows are stored
in Firestore. Messages remain local to the current app session.

## What's here

- **Home** — a feed of `MathBuild`s rendered live on `Canvas` (not images).
  Preset function and circle cards have sliders that redraw them in real time;
  custom formula builds keep their expression fixed. Posts and like counts sync
  through Firestore; comments open a live thread with likes and replies, and
  users can repost or save posts. The feed starts empty until users publish,
  and search can find registered users to follow. Reposts do not yet show a
  separate bubble in the feed.
- **Create playground** — keep function graphs, display-only math equations,
  geometry, and text in separate boxes on one canvas. The math keypad helps
  write equations such as `x^2 = 2*x`; the Graph tool separately plots
  functions such as `x^2 - 2*x` and `sin(x)`. Drag boxes to arrange the plane,
  then use the handles to resize or rotate them. Create containers for related
  elements or select existing boxes and group them; move, resize, or rotate a
  container to transform its contents together. Add points, segments, and
  circles inside a geometry box. Arithmetic, Geometry, and Other tool tabs sit
  above the app navigation. Function graphs show small dots at
  intercepts, visible local extrema, and inflection points; tap a dot to
  highlight it and read its coordinates. Resize objects freely from their
  corner handles. Post the whole canvas to the feed.
- **Messages** — a conversation list and a thread view with a math-mode
  toggle (`Σ`) that live-previews `y=x^2` style input, plus bubbles that can
  carry a real mini live-rendered build.
- **Profile** — works for you and other registered users (tap a username in
  the feed or search). Edit your username, bio, and profile photo; switch
  between your posts and reposts; and open your saved posts privately. Settings
  include password reset and sign out. Follow/unfollow and follower counts sync
  through Firestore. Grid of builds renders the same live `Canvas` thumbnails
  as the feed.

The math renderer (`ui/components/MathCanvas.kt`) is the one place the
playground, feed, messages, and profile thumbnails draw from, so a "build"
means the same live thing everywhere.

- **Dynamic color (Material You)** — on Android 12+, the whole app's colors
  are pulled live from the phone's wallpaper/system theme, same as Google's
  own apps. On older Android versions (or with `dynamicColor = false`) it
  falls back to the custom "notebook" (light) / "chalkboard" (dark) palette.
  See `ui/theme/Theme.kt`.
- **Launcher icon** — a simple adaptive icon (navy background, a ring with
  one marked point — a literal "locus"). Works on Android 8.0+ (API 26+),
  which is effectively all real devices; on the rare API 24/25 device it'll
  fall back to a system default icon since no legacy PNG set was generated.
  Use Android Studio's Image Asset Studio (`File → New → Image Asset`) if
  you want full legacy coverage or a different design later.

- **Login & registration pages** — `ui/screens/auth/`. Email + password
  login, and sign-up with username / email / password / confirm, with inline
  validation, show/hide password, and keyboard-aware layout. `LocusRoot.kt`
  shows them on launch; set `REQUIRE_LOGIN = false` there to skip them while
  developing other screens.
- **Firebase (Auth + Firestore)** — the SDK is wired in (`app/build.gradle.kts`
  + the `google-services` plugin). Email/password login and registration use
  Firebase Auth; registration stores the chosen username as the Firebase
  user's display name. Posts, profiles, likes, comments, comment likes/replies,
  reposts, saves, and follows use Firestore. Social actions and count updates
  are atomic. Profile photos are uploaded to Firebase Storage.

## Firebase setup (do this once, per machine that builds the app)

1. Create a project at console.firebase.google.com
2. Add an Android app to it with package name **exactly** `com.locus.app`
3. Download the `google-services.json` it gives you and put it in `app/google-services.json` (next to `app/build.gradle.kts`, not inside `app/src`)
4. In the console: Build → Authentication → enable Email/Password sign-in
5. Create a database in Build → Firestore Database.
6. Enable Firebase Storage and create a default bucket.
7. Install the Firebase CLI, select your project, then deploy the included
   Firestore and Storage rules from this folder:
   `firebase deploy --only firestore:rules,storage --project YOUR_FIREBASE_PROJECT_ID`

Without step 3, the build fails immediately with "File google-services.json
is missing" — that's expected, not a bug in the code.

`google-services.json` is safe to commit for a client app like this (the
values in it aren't secret the way a server API key is; real security comes
from Firestore's security rules, not from hiding this file) — but if you'd
rather keep it out of a public repo, add `app/google-services.json` to
`.gitignore` and share the file with teammates some other way.

## What's intentionally stubbed (this is a *base* app)

- **Messages are session-only.** Conversation messages are not persisted to
  Firestore yet.
- **No custom fonts.** `ui/theme/Type.kt` uses system serif/sans/mono as
  stand-ins for Fraunces / IBM Plex Sans / IBM Plex Mono. Drop `.ttf` files
  into `res/font` and swap the `FontFamily` values to match the web version
  exactly.
- **Chat "math mode" is regex-based**, not real LaTeX/MathJax rendering
  (`ui/util/Utils.kt#mathify`) — good enough for a demo, not a real typesetting engine.

## Natural next steps

- Persist messages and add repost bubbles to the feed
- Hilt for dependency injection once there's a real repository to inject
- Push notifications for likes/comments/messages
