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

## Online catalog (added after 2.5)
"Find online" in the player editor and in the lineup slot editor (Online.kt). Needs the INTERNET permission (added to the manifest).
- TheSportsDB (free test key "3"): names, positions, cut-outs / renders / photos
- Wikimedia Commons: real photos labelled by year (free licences), newest first
- Fantasy Premier League (public, unofficial): official PL render, this-season stats, last 5 matches, past seasons. Premier League only
Pick a picture and it goes through the normal crop dialog and ImageStore, so it is saved locally and travels in backups.
Stats are shown, not stored, so the data format is unchanged.

## Lineup renders
A player with a render on the lineup pitch or bench is drawn as the bare render with a name label (no circle); transparent PNG
cut-outs look best. Without a render (or if the file can't be read) the numbered circle is used.

## Settings layout
Settings is a short hub (summary card + grouped rows: LOOK, PAGES, DATA, About), modelled on Paperback's settings. Each row opens its own
page (Screen.SettingsSubS in AppVM, pages in SettingsScreen.kt: look, style, text, nav, pages, blocks, cols, fields, backup, data, about).
To add a setting: put the control on the matching page and, if it should show as the row's value, update the value text in SettingsScreen().

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
