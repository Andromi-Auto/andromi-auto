<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_playstore.png" width="128" alt="Andromi Auto icon">
</p>

<h1 align="center">Andromi Auto</h1>

<p align="center">
  Android Auto on the screen of a Xiaomi EV, straight from your Xiaomi phone.
</p>

---

Andromi Auto runs **Android Auto on your phone itself** ("Self Mode") and shows it in a window
that HyperOS mirrors to the car screen through Xiaomi Interconnectivity. You get the real Android
Auto (Google Maps, YouTube Music, WhatsApp and every other Android Auto app) on the car's
infotainment screen, pinned next to the car's own widgets.

It is a personal, community project and a modified version of
[Open Headunit](https://github.com/andreknieriem/open-headunit), cut down to Self Mode and
reworked for Xiaomi cars.

## Features

- **Self Mode only:** Android Auto runs on the phone and is shown by this app. No USB, wireless
  head-unit or dongle features, so the app stays simple.
- **Automatic start:** pick the car's Bluetooth device and Android Auto starts by itself when you
  get into the car, without the start screen.
- **Root setup for Android Auto 17.4+:** one tap re-enables Android Auto's startup receiver, so
  Self Mode works without the developer "headunit server". Without root the app shows the exact
  commands to run yourself.
- **Portrait and landscape:** one switch on the home screen. Portrait for the car's size 1 window,
  landscape for sizes 2 and 3.
- **Material You:** every screen uses Material 3 and your wallpaper colours (Monet), in light and
  dark mode.
- **Picture status:** Settings shows the size and codec Android Auto really sent last time.

## Requirements

- A Xiaomi or Redmi phone on HyperOS with Xiaomi Interconnectivity to the car (tested with the
  CN ROM).
- The **Android Auto** app installed on the phone.
- For automatic start on Android Auto 17.4 and newer: **root** (Magisk or KernelSU), or running
  one command as root once.
- The real Baidu app (`com.baidu.searchbox`) must **not** be installed (see below).

## Install

1. Download `Andromi-Auto-debug.apk` from the
   [test-main release page](../../releases/tag/test-main).
2. Install it on the phone and allow installing from this source if Android asks.
3. Open Andromi Auto, follow the setup and allow the permissions it asks for.
4. Tap **Start Android Auto** once to check that it works, then pin the app on the car screen.

Every new build is signed with the same key, so it installs over the previous one and keeps the
settings.

### Why the install id is `com.baidu.searchbox`

The car only lists apps whose package name is on Xiaomi's allow-list. The app therefore installs as
`com.baidu.searchbox`. That means it cannot be installed next to the real Baidu app or next to
another app that uses the same id (for example Xiaomi Cockpit), and it can never be on Google Play.

## Automatic start

Home screen → **Automatic start**:

1. **Root setup.** Tap *Set up with root* and allow the root prompt. The app runs:
   ```sh
   pm enable --user 0 com.google.android.projection.gearhead/com.google.android.apps.auto.wireless.setup.receiver.WirelessStartupReceiver
   appops set --user 0 com.baidu.searchbox SYSTEM_ALERT_WINDOW allow
   pm grant com.baidu.searchbox android.permission.BLUETOOTH_CONNECT
   pm grant com.baidu.searchbox android.permission.POST_NOTIFICATIONS
   ```
   Without root, copy the commands shown on that screen and run them in a root terminal or with
   `adb` as root; the app notices the change by itself.
2. **Car Bluetooth.** Choose the car from your paired devices.
3. **Open from the background.** Allowed by the root setup, or with the *Allow* button.
4. Turn on **Start Android Auto automatically**.

When it is on, connecting to the car (or opening the app) goes straight to the
"Android Auto is starting" screen. Press Back on that screen to reach the home screen and Settings.

## Picture quality

The picture goes through two video steps: Android Auto compresses its screen into a video, and
HyperOS compresses the window again to send it to the car. Choosing a very high resolution (4K)
does not make it sharper. Settings → **Android Auto picture** shows what was really used; 1080p or
1440p with H.265 is the sensible range. Work on a sharper picture is ongoing.

## Building

Builds run on GitHub Actions (`.github/workflows/build-debug-apk.yml`) on every push to `main`:

- debug and release APKs, attached to the prerelease **test-main** (and kept as artifacts for 7 days)
- signed with the project's own key from the repository secrets `ANDROMI_KEYSTORE_B64` and
  `ANDROMI_KEY_PASSWORD` (the key is not in the repository)

`.github/workflows/android-ci.yml` runs the unit tests and the connection test harness.

To build locally you need JDK 17+, the Android SDK (platform 36, build-tools 36) and NDK
29.0.14206865 with CMake 3.22.1:

```sh
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

Without the signing secrets a local build uses Android's normal debug key.

## Credits and licence

- Based on [Open Headunit](https://github.com/andreknieriem/open-headunit) by André Rinas and its
  contributors, itself based on [Headunit](https://github.com/mikereidis/headunit) by Michael Reid.
- Licensed under the **GNU Affero General Public License v3.0**, like Open Headunit. See
  [LICENSE](LICENSE) and [COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt](COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt).
  This repository is the source of every APK we hand out.
- Not affiliated with Xiaomi, Baidu or Google. Android Auto is a trademark of Google LLC.

## Disclaimer

Use at your own risk. Do not operate the phone or the car screen in a way that distracts you while
driving. The software is provided "as is", without warranty of any kind.
