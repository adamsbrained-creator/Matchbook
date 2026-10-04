# Clubs (Jetpack Compose)

Port of "Clubs – app mockup v7".

Open this folder in Android Studio (Ladybug or newer), let Gradle sync, press Run.

- Portrait: bottom nav. Landscape: side rail + list pane + detail pane (as in the mockup).
- Data and customisation settings persist on the device.
- "Rounded" typeface: drop nunito.ttf into app/src/main/res/font and change
  `RoundedFont` in Ui.kt to `FontFamily(Font(R.font.nunito))`.
