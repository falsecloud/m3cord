# M3Cord
Material 3 Expressive Discord client (Kotlin + Compose).

Build (needs JDK 17 + Android SDK 35):
1. `gradle wrapper --gradle-version 8.9`  (no wrapper jar included)
2. `./gradlew assembleDebug`
3. APK: app/build/outputs/apk/debug/app-debug.apk

If a dependency version fails to resolve, bump it to the nearest available one.
Material3 must be 1.4.0 or newer (Expressive APIs).


## What's new
- **Active now** (Friends tab): a carousel of friends who are playing / listening / streaming, using the same Material 3
  multi-browse carousel pattern as Orion Store's "Recommended" section (masked rounded cards, copy fades as a card shrinks).
  Needs presence activities from the gateway (`Active.kt`, `Gateway.kt`); game art falls back to the app icon.
- **Emoji + GIF panel**: button next to the chat input opens a half-screen panel (`Emoji.kt`).
  - GIFs tab: Discord GIF search, trending, and your account's favorite GIFs (read/written through
    `/users/@me/settings-proto/2`, see `Proto.kt`). Heart any GIF in the panel, or long-press a message with a GIF -> "Favorite this GIF".
  - Emojis tab: custom emojis from every server you're in + all built-in Unicode emojis, with search and jump chips.
    Without Nitro, custom emojis are inserted as image links, like the official app.
