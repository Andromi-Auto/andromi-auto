# Andromi Auto: project notes for Claude

Read this first. It carries everything decided in earlier sessions so the user does not have to
repeat it.

## Who you are working with
- The user has no coding knowledge. Explain in plain language, give exact click-by-click steps,
  and do all coding yourself. They use Windows, the GitHub website and test on their own Xiaomi
  phone and Xiaomi EV, then report back with screenshots.
- They want a modern, native-feeling look: Material 3 / Material You with Monet (wallpaper) colours.
- They also own the separate project **Xiaomi Cockpit** (`pagnchanak/xiaomi-cockpit`, private).
  Do not mix the two: Andromi Auto is its own app and its own repository.

## What the app is
- Repository: `Andromi-Auto/andromi-auto` (public, fork of
  `andreknieriem/open-headunit`, AGPL-3.0). Work on `development` (every push builds the
  `development` pre-release); `main` is for stable releases.
- Andromi Auto = Open Headunit reduced to **Self Mode**: Android Auto runs on the phone itself and
  this app shows it; HyperOS "Xiaomi Interconnectivity" mirrors the app window to the car screen,
  where the user pins it. It is for the user's own use (and possibly a few others).
- Install id `applicationId = com.baidu.searchbox` (car allow-list; override with `-PappId=`).
  Cannot coexist with the real Baidu app or with Xiaomi Cockpit (same id). Kotlin namespace and
  packages stay `com.andrerinas.openheadunit` (do not rename).
- `versionCode = 2,000,000,000 + GITHUB_RUN_NUMBER`, so every build installs over the last one
  and stays above the real Baidu app. Never lower the base.

## Building, signing, releases
- GitHub Actions `.github/workflows/build-debug-apk.yml` ("Build APKs") on push to `development`
  or `main`: assembles `githubDebug` + `githubRelease`, deletes old artifacts (quota), uploads new
  ones for 7 days, and attaches `Andromi-Auto-debug.apk` / `Andromi-Auto-release.apk` to a release:
  - `development`: the `development` pre-release, deleted and recreated on every push.
  - `main`: a new stable release `v<versionName>-build<run number>`, marked latest.
  - Run by hand on another branch: its `test-<branch>` pre-release.
  The user installs test builds from the `development` pre-release.
- Signing: one key for debug and release, from repo secrets `ANDROMI_KEYSTORE_B64` (base64
  PKCS12, alias `andromi`) and `ANDROMI_KEY_PASSWORD`. The workflow writes it to
  `app/andromi.keystore` (gitignored). Never commit the keystore or password. Without the secrets
  the build falls back to the normal debug key.
- `.github/workflows/android-ci.yml` ("Android CI"): unit tests (`testGithubDebugUnitTest`) and
  the connection lifecycle harness (`app/src/test/host-connection/run.py`, Python + Kotlin
  compiler). The harness copies production methods (e.g. `SelfLauncherManager.start`) into a
  fixture with stand-ins; a new class used there needs a stand-in in `SelfLaunchFixture.kt`.
  Tests also enforce rules, e.g. toasts must go through `ToastUtils` (`ToastUsageTest`).
- Local builds in a cloud session: install the Android SDK to `/opt/android-sdk`
  (cmdline-tools, `platforms;android-36`, `build-tools;36.0.0`, `ndk;29.0.14206865`,
  `cmake;3.22.1`), `sdk.dir` in `local.properties` (gitignored). Maven Central rate-limits the
  sandbox (HTTP 429): use an init script `~/.gradle/init.d/mirror.gradle` that rewrites
  `repo.maven.apache.org` to `https://maven-central.storage-download.googleapis.com/maven2/`.
  Always run `./gradlew :app:assembleGithubDebug :app:testGithubDebugUnitTest` before pushing.
- Check CI from the session with the public API:
  `curl -s https://api.github.com/repos/Andromi-Auto/andromi-auto/actions/runs?per_page=4`.

## Changes made on top of Open Headunit (where to find them)
- **Self Mode only** (`utils/Settings.kt`): `connectionModes` always `{SELF}`, so USB/wireless UI
  and settings hide themselves. `HomeUiHelper` hides USB/WiFi buttons. USB-attach auto launch
  removed from the manifest. Onboarding skips the connection, automation and vehicle steps.
- **Settings screen** (`main/SettingsFragment.kt`, `res/layout/fragment_settings.xml`):
  - Material 3 large collapsing title, pill search bar, one list (no Basic/Advanced tabs), a
    floating Save button (`settingsSaveFab`) that shows only when something changed. Reset is the
    last row.
  - `REMOVED_ROWS` + `withoutRemovedRows()` hide everything not for Self Mode (USB, wireless,
    car launcher, floating button, GPS/fake speed, keymap/rotary, HUD mirroring, update check and
    support of the original author, video debug tests...). Keep anything that affects Android
    Auto itself.
  - `SettingsStatusAdapter` (top cards): "Android Auto picture" from `utils/VideoStreamInfo.kt`
    (what was offered, which config the phone started) and the Automatic start card.
- **Home screen** (`main/HomeFragment.kt`, `res/layout/fragment_home.xml`,
  `res/layout-land/fragment_home.xml`): Material 3, icon + name, big Start Android Auto button,
  Automatic start card, Screen card (portrait/landscape segmented switch, saves
  `Settings.screenOrientation` and turns the screen at once), Settings button. No Exit button.
  Old styling helpers are switched off (`ANDROMI_MATERIAL_HOME` in `HomeFragment`/`HomeUiHelper`).
- **Orientation:** Open Headunit's `screenOrientation` setting is used as is. The user switches
  manually. The Screen card says: use Portrait to pin the app on the car screen, then switch
  to Landscape for Android Auto (no per-size texts). Two attempts at automatic
  orientation were reverted on the user's request; do not reintroduce them unless asked.
- **Automatic start** (`main/AutoStartSetupActivity.kt`, `utils/RootSetup.kt`,
  `main/AndromiAutoStart.kt`):
  - Root setup re-enables Android Auto's `WirelessStartupReceiver` (`pm enable --user 0 ...`),
    allows SYSTEM_ALERT_WINDOW and grants BLUETOOTH_CONNECT / POST_NOTIFICATIONS through libsu.
    The receiver state is read without root, so a manual setup is detected too.
  - With the receiver enabled, `SelfLauncherManager` uses `SelfLauncherRoot` (our 5288 wireless
    server + receiver broadcast), so no developer headunit server is needed on AA 17.4+.
  - Car Bluetooth uses the existing auto-start BT trigger (`AutoStartReceiver`, only while
    `autoStartSelfMode` is on). When on, opening the app or a BT connect skips the splash and the
    start screen and shows only the "Android Auto is starting" overlay (`MainActivity`).
- **Look:** `DynamicColors.applyToActivitiesIfAvailable` in `App.onCreate`, re-applied in
  `MainActivity` after its `setTheme`. Full-screen dialog fragments with their own theme
  (`QuickSettingsFragment`, `AppDrawerFragment`) wrap their inflater with DynamicColors.
  Material Components styles were replaced by Material3 ones everywhere; fixed colours replaced by
  theme roles; switches are `MaterialSwitch`. `DarkAlertDialog` is a plain M3 dialog overlay.
- **No back arrows** on settings screens (user's choice): back is the phone's back gesture. Only
  the overlays over Android Auto (Quick Settings, app drawer) keep their close/back button.
- **Full screen everywhere:** every activity applies `SystemUI.apply(...)` with the user's
  fullscreen mode in onCreate/onResume and again in `onWindowFocusChanged` (dialogs bring the
  bars back). A new screen must do the same.
- **Name and icon:** "Andromi Auto" in every language; Open Headunit's rename notice is off.
  Launcher icon = the Android Auto mark in Xiaomi orange (`#F26100` left / `#FF6900` right halves)
  over the white arrow (`#F2F2F2` / `#E3E3E3`) on white (`drawable/ic_launcher_foreground_andromi.xml`,
  monochrome themed icon, legacy webp mipmaps). About screen credits Open Headunit and links this
  repository (AGPL).
- **Update check** (Settings > Check for updates; `utils/AndromiUpdater.kt`,
  `utils/AndromiUpdatePolicy.kt`): stable releases only (`releases/latest`, never pre-releases).
  Builds compare by run number (tag `-build<N>` vs versionCode - 2,000,000,000). Against the
  60/hour API limit: the API ETag is cached (a 304 is free), and on a refusal the tag is read from
  the `github.com/<repo>/releases/latest` redirect. Downloads `Andromi-Auto-release.apk` to the
  cache and opens the system installer (REQUEST_INSTALL_PACKAGES, github flavor manifest only).
- **Video fallback:** `utils/VideoFallbackPolicy.kt` offers 1080p when 1440p/4K is refused
  (was 720p).

## Open topics
- **Picture sharpness:** Android Auto's picture looks soft even at 4K. Analysis so far: AA encodes
  a video (the head unit cannot ask for a bitrate), the app scales it to its window, HyperOS
  re-encodes the window for the car. Phone screenshots at 1080p-equivalent and 4K looked the same.
  Next steps discussed (not built yet): read the "Android Auto picture" card to see what AA really
  sends; "exact pixel match" (resolution + margins so AA's canvas equals the window pixels, no
  scaling); prefer H.265 at 1080p/1440p; possibly lower DPI for bigger text.
- Unverified: pinning to the car screen in landscape; automatic start end to end in the car.
- Known car window sizes (from Xiaomi Cockpit work, may differ): size 1 about 1450x2368 px,
  size 2 about 1822x1450, size 3 about 2320x1450.

## Working rules
- Keep explanations short and non-technical; say exactly what to click and what to send back
  (screenshots, the Settings "Android Auto picture" card, or Settings > Debug > Export logs).
- Ask before large changes in direction; the user decides look and behaviour.
- Commit with clear messages and push to `development`; wait for both workflows to be green
  before telling the user a build is ready. Merge into `main` only when the user asks for a
  stable release.
- Keep AGPL obligations: the repository stays public, credits stay in README/About/LICENSE.
- Never put model names in commits or files.
