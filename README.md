# Matchbook 2.5

Track clubs, leagues, tables, fixtures, squads, lineups and tactics. Jetpack Compose app.

Open in Android Studio, let Gradle sync, press Run. GitHub Actions builds the APK via
.github/workflows (artifact: matchbook-apk).

## What's new in 2.5
- Squad per club, lineup builder (16 formations, drag to position or swap, bench) and a tactics mode (arrows, duties, team instructions)
- Coral interface as an alternative to the Glass look (Settings > Appearance > Interface style, or the "Coral" preset)
- Eleven extra navigation bars (Settings > Navigation); the 2.0 "Docked" option was removed
- Pitch app icon, refreshed icon set, Pitch launch screen
- Data format 3 (squads, lineups, tactics); 2.0 data and backups still load

## Upgrading
Same app id (com.matchbook.app): 2.5 installs over 2.0/1.x and keeps your data. Settings and data are migrated
automatically (Store.settingsFromJson / Store.fromJson). Make a backup first (Settings > Backup and restore).

## Backups
One ZIP (data.json + images/). data.json carries a format number (DATA_FORMAT in Models.kt, now 3). New versions read
older formats; a backup from a newer format is refused with a clear message. When you change the JSON layout:
bump DATA_FORMAT and add a migration step.

## Optional fonts
Drop into app/src/main/res/font (lowercase names, picked up automatically):
manrope.ttf (Glass default), outfit.ttf (Coral), anton.ttf (Condensed headings), inter.ttf, nunito.ttf (Rounded).
Variable fonts work. Without them the app uses system fonts.
