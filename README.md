# Matchbook

Track your clubs, leagues, tables and stats. Jetpack Compose app, port of "Clubs - app mockup v8".

Open in Android Studio, let Gradle sync, press Run. GitHub Actions builds the APK via .github/workflows/android.yml
(artifact: matchbook-apk).

Optional fonts (drop into app/src/main/res/font, lowercase names, picked up automatically):
anton.ttf (Condensed headings), inter.ttf (body), nunito.ttf (Rounded).
Without them the app falls back to the system condensed / default / sans fonts.

App icon: adaptive vector icon (res/drawable/ic_launcher_*.xml), with a monochrome layer for Android 13+ themed icons.
