# Slug's Ergo Launcher

AI Disclosure. This app was entirely vibe coded, run at your own risk. 

An Android home launcher built for one-handed use. Every app lives on a quarter wheel anchored in the bottom corner of the screen, within easy thumb reach.


## How it works

- **Start button:** a quarter disc in the corner. Tap it to open the folder ring.
- **Folders:** Communication, Games, Internet, Media, Utilities, Settings, Other, Search / Recent. Sweep your thumb around the ring to scroll.
- **Apps:** tap a folder to open its apps on a second ring. The Back button shrinks to a quarter of the wheel and steps you out one level.
- **Move or uninstall:** press and hold an app. The folder ring comes back so you can pick a new folder, and a trash button appears on the opposite side of the screen.
- **Hidden folder:** press and hold Back on the folder ring.
- **Search / Recent:** shows recent apps and swaps the widgets for a search box. The settings button is here too.
- **Auto-sort:** new apps go to the folder that matches the category they declare. Folders with new apps show a green NEW badge.
- **Widgets:** press and hold either widget slot at the top to choose a widget.
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

Version 0.1. This is a first native build of the design, so expect rough edges.
