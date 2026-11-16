# Media Timer

An Android sleep timer for media. Set a duration, and when time is up the app pauses whatever
is playing on the phone: music in Brave or Chrome, YouTube, Spotify, a podcast app, a video
player.

Built with React Native (Expo SDK 58, TypeScript) and a small Kotlin native module.

## Features

- Presets (10 / 20 / 30 / 45 / 60 / 90 min) and a custom duration from 1 to 600 minutes.
- The countdown keeps running when the app is closed or the screen is off.
- Countdown notification with **+5 min** and **Cancel** buttons.
- Optional: go to the home screen when time is up.
- "Pause media now" button to test that your player responds.

## How it stops other apps

Android does not let one app control another directly, so the timer uses two system-wide
mechanisms. Neither needs a special permission.

1. It sends a media **PAUSE** key event. Android routes it to the active media session, the
   same way a headset button does.
2. It takes **audio focus** permanently. The app that was playing loses focus and has to stop.

Any player that shows media controls in the notification shade responds to these.

## Permissions

| Permission | Why |
| --- | --- |
| Notifications | Shows the countdown. The timer still works if you deny it. |
| Display over other apps | Only for "Go to home screen". Android blocks screen changes from the background without it. Asked for when you turn the option on. |

## Build an APK

The `android/` folder is generated and not committed. Native code lives in
`modules/media-timer/android`.

### Option A: cloud build (no Android tooling on your PC)

Needs a free Expo account.

```bash
npx eas-cli@latest login
```

```bash
npx eas-cli@latest build --platform android --profile preview
```

The command prints a link to download the APK.

### Option B: local build

Needs JDK 17 and the Android SDK (`ANDROID_HOME` set), for example through Android Studio.

```bash
npx expo prebuild --platform android
```

```bash
cd android; .\gradlew.bat assembleRelease -PreactNativeArchitectures=arm64-v8a
```

The APK is written to `android/app/build/outputs/apk/release/app-release.apk`. It is signed
with the debug keystore, which is fine for installing on your own phone. The
`-PreactNativeArchitectures=arm64-v8a` flag builds only for 64-bit ARM phones, which covers
every recent phone; leave it out to build for all CPU types.

**Windows note:** libraries that ship generated C++ (for example
`react-native-safe-area-context`, `react-native-screens`) can fail this build with
`ninja: error: mkdir(...): No such file or directory`, because the generated paths exceed 260
characters. That is why this app reads the system bar sizes from its own native module
instead of using a safe-area library.

### Install on the phone

With USB debugging on and the phone connected:

```bash
adb install -r media-timer.apk
```

Or copy the APK to the phone and open it; Android asks you to allow installing from that
source.

## Develop

```bash
npx expo run:android
```

This app has native code, so it does not run in Expo Go.

## Project layout

```
App.tsx                         Timer screen
modules/media-timer/index.ts    Typed JS interface to the native module
modules/media-timer/android/    Kotlin: foreground service, media stop, home launcher
```

## Known limits

- An app that ignores both the pause key and audio focus keeps playing. This is rare.
- Some phones (Xiaomi, Huawei, Oppo, some Samsung settings) kill background apps
  aggressively. If the timer does not fire, exclude Media Timer from battery optimization.
