---
name: run-app
description: Use when asked to run, launch, install, screenshot, or manually verify the ReduxDemo Android app (the chronometers + universities demo) on an emulator/device.
---

# Running the ReduxDemo app

Launches the demo `:app` on an Android emulator and drives it with `adb`
(tap + screenshot). Verified working on macOS with an emulator already booted.

## Prerequisites (this machine)

- Android SDK at `~/Library/Android/sdk`. **`adb`/`emulator` are NOT on PATH** —
  export them first.
- `:app` depends on the **published** `com.florentmaufras:redux` from GitHub
  Packages, so a **`GITHUB_TOKEN` with `read:packages` must be in the env** or the
  build can't resolve redux. (`GITHUB_ACTOR` is used as the username.)

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

## 1. Ensure an emulator is running

```bash
adb devices                     # want a line like: emulator-5554   device
# if none is attached:
emulator -list-avds             # e.g. Medium_Phone_API_36.1
emulator @Medium_Phone_API_36.1 -no-boot-anim &   # backgrounds; boot takes ~30-60s
adb wait-for-device
```

## 2. Build, install, launch

```bash
./gradlew :app:installDebug --console=plain      # needs GITHUB_TOKEN (see above)
adb shell monkey -p com.florentmaufras.reduxdemo -c android.intent.category.LAUNCHER 1
sleep 4
```

- applicationId: `com.florentmaufras.reduxdemo`
- entry activity: `com.florentmaufras.reduxdemo.app.MainActivity`

## 3. Screenshot — and LOOK at it

```bash
adb exec-out screencap -p > /tmp/app.png
```

Then Read `/tmp/app.png`. A blank/black frame means it did not launch.

## 4. Drive it: tap by real coordinates

Screen is **1080x2400**. Don't guess — dump the UI, read a control's `bounds`,
tap its center:

```bash
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep 'text="Add"'   # -> bounds="[x1,y1][x2,y2]"
adb shell input tap <cx> <cy>                                    # cx=(x1+x2)/2, cy=(y1+y2)/2
```

## Demoing the chronometers

Layout: the **Chronometers** section is on top (buttons **Add / Play all /
Pause all / Reset all**); the **Universities** search is below (loads "Canada"
results automatically on launch via `OnAppear`).

- **Add** creates a chronometer that **auto-starts** and ticks once per second.
- Each row: **Pause/Play**, **Reset** (zero + stop), **Remove**.
- **Pause all / Play all / Reset all** broadcast to every chronometer.

Show it ticking:

```bash
adb shell input tap 138 159    # "Add"  (auto-starts a ticking chronometer)
adb exec-out screencap -p > /tmp/t0.png
sleep 4
adb exec-out screencap -p > /tmp/t1.png   # the mm:ss will have advanced ~4s
```

Show the parent broadcast: tap **Pause all** and confirm every row freezes and
its button flips Pause -> Play.

Reference coordinates on a 1080x2400 screen with a couple of chronometers added
(from a verified run): **Add ≈ (138,159)**, **Play all ≈ (367,159)**,
**Pause all ≈ (637,159)**, **Reset all ≈ (900,159)**. Prefer the `uiautomator
dump` method above — the layout shifts as rows are added/removed.
