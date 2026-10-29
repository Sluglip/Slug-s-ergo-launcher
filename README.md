# Slug's Ergo Launcher

AI Disclosure. This app was entirely vibe coded, run at your own risk. 

An Android home launcher built for one-handed use. Every app lives on a quarter wheel anchored in the bottom corner of the screen, within easy thumb reach.


## How it works

- **Start button:** a quarter disc in the corner. Tap it to open the folder ring.
- **Folders:** Favorites, Communication, Games, Internet, Media, Utilities, Other, Search / Recent, Launcher Settings. Sweep your thumb around the ring to scroll.
- **Apps:** tap a folder to open its apps on a second ring. The Back button shrinks to a quarter of the wheel and steps you out one level.
- **App menu:** press and hold any app for a small menu: add or remove Favorites, pin or unpin Quickstart, Move, Hide, Uninstall. Favorites and Quickstart are shortcuts, so the app stays in its own folder. Move brings the folder ring back so you can pick the new folder.
- **Quickstart:** pinned apps sit on the folder ring after a divider line. Keep sweeping past the folders to reach them.
- **Hidden folder:** press and hold Back on the folder ring.
- **Search / Recent:** shows recent apps and swaps the widgets for a search box. 
- **Auto-sort:** new apps go to the folder that matches the category they declare. Folders with new apps show a green NEW badge.
- **Widgets:** press and hold empty space anywhere on the home screen to add a widget. Hold a widget to edit it: drag to move, drag a corner to resize, tap the red X to remove, tap empty space to finish.
- **Skins:** in Launcher Settings pick a wheel skin (record, moon, cookie, pizza and more) or your own square picture. The skin turns as you scroll.
- **Launcher Settings:** the last entry on the folder ring. It has the comfort zone distance and the number of app rows (1 to 5), plus colors, sizes and timings.
- **Wallpaper:** your current system wallpaper shows behind everything.

## Get the app on your phone

1. Open the **Actions** tab of this repository.
2. Open the latest **Build APK** run. If there is none, choose **Build APK** on the left and press **Run workflow**.
3. When it finishes (about 5 minutes), scroll to **Artifacts** at the bottom and download **ergo-launcher-debug-apk**. It is a zip file containing `app-debug.apk`.
4. Unzip it on your phone and open `app-debug.apk`. Android will ask to allow installs from your browser or file manager. Allow it once.
5. Press the Home button and choose **Ergo Launcher** when Android asks which home app to use.

To go back to your old launcher: Settings, Apps, Default apps, Home app.

## Building it yourself

Open the project in Android Studio and press Run, or run `gradle assembleDebug` with JDK 17 and the Android SDK installed. The APK appears in `app/build/outputs/apk/debug/`.

## Status

Version 0.3. This is a first native build of the design, so expect rough edges.
