# Matchbook 2.0

Track clubs, leagues, tables, fixtures and stats. Jetpack Compose app.

Open in Android Studio, let Gradle sync, press Run. GitHub Actions builds the APK via
.github/workflows/android.yml (artifact: matchbook-apk).

## Upgrading from 1.x
Same app id (com.matchbook.app), so 2.0 installs over 1.x and keeps your data. Saved data and settings are
migrated automatically (see Store.settingsFromJson / Store.fromJson).

## Backups
Settings > Backup and restore. A backup is one ZIP (data.json + images/). data.json carries a format number
(DATA_FORMAT in Models.kt). New versions keep reading old formats; a backup from a newer format is refused
with a clear message. When you change the JSON layout: bump DATA_FORMAT and add a migration step.

## Optional fonts
Drop into app/src/main/res/font (lowercase names, picked up automatically):
manrope.ttf (default typeface, a variable font works), anton.ttf (Condensed headings), inter.ttf, nunito.ttf (Rounded).
Without them the app uses system fonts.

## Icon and launch screen
Adaptive vector icon (res/drawable/ic_launcher_*.xml) with a monochrome layer. System launch screen via
core-splashscreen (Theme.Matchbook.Starting), then an animated Compose launch screen (Splash.kt, switch off in Settings).
