# M3Cord
Material 3 Expressive Discord client (Kotlin + Compose).

Build (needs JDK 17 + Android SDK 35):
1. `gradle wrapper --gradle-version 8.9`  (no wrapper jar included)
2. `./gradlew assembleDebug`
3. APK: app/build/outputs/apk/debug/app-debug.apk

If a dependency version fails to resolve, bump it to the nearest available one.
Material3 must be 1.4.0 or newer (Expressive APIs).
