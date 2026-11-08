<div align="center">

<img src="assets/images/icon.png" alt="KClash" width="80">

# KClash

Native Android proxy client built with Kotlin, Jetpack Compose and the Mihomo core.

[English](README.md) · [简体中文](README_zh_CN.md)

[![Android](https://img.shields.io/badge/platform-Android-3ddc84?style=flat-square&logo=android&logoColor=white)](https://github.com/lonnnnnng/FlClash)
[![Release](https://img.shields.io/github/v/release/lonnnnnng/FlClash?filter=kclash-v*&style=flat-square&label=release)](https://github.com/lonnnnnng/FlClash/releases)
[![License](https://img.shields.io/github/license/lonnnnnng/FlClash?style=flat-square)](LICENSE)

[Download](https://github.com/lonnnnnng/FlClash/releases/latest) · [Build from source](#build-from-source) · [Report an issue](https://github.com/lonnnnnng/FlClash/issues)

</div>

KClash is the Android Kotlin mainline in this repository. It rebuilds the Android experience of [FlClash](https://github.com/chen08209/FlClash) as a native application. The Flutter project is a behavior and UI reference; Flutter application code is not part of the Kotlin runtime.

## Features

- **Mihomo proxy core** for rule, global and direct modes, proxy groups, node selection and delay tests.
- **Profile management** for subscription URLs, YAML files and custom profiles, with editing for rules, proxies, proxy groups, providers and supported profile settings.
- **Traffic and diagnostics** including live upload/download rates and history, connections, requests, DNS, logs and request-path details.
- **Android VPN and proxy controls** with VPN/TUN, local proxy mode, per-app proxy settings, Quick Settings tile and explicit automation actions.
- **Profile resources and backup** including local resources, scripts, file import/export and user-initiated local or WebDAV backup/restore.
- **No ads**, licensed under GPL-3.0.

## Platform and status

| Item | Support |
| --- | --- |
| Platform | Android phones |
| Minimum Android version | Android 7.0 (API 24) |
| Release ABI | `arm64-v8a` only |
| Application ID | `com.lonnnnnng.flclash` |
| Desktop, Android TV and other ABIs | Not included in this Kotlin release |

The Kotlin app is under active development. Core profile, proxy, VPN and diagnostics flows have automated and Redmi Note 8 Pro device coverage. Full Flutter visual parity, performance attribution on every device/ROM, low-API coverage and some system/OEM scenarios remain in progress. See [`docs/roadmap.md`](docs/roadmap.md) for scope and evidence. A passing fixture test does not imply that every device or production network has been verified.

## Download and install

Download the signed `KClash-0.1.0-android-arm64-v8a.apk` from [GitHub Releases](https://github.com/lonnnnnng/FlClash/releases/latest). The release supports ARM64 Android devices. Android may ask you to allow installation from the browser or file manager used to open the APK.

KClash has its own Android application ID and signing identity. It installs separately from the upstream Flutter app and does not migrate Flutter/Drift application data. Keep the release signing identity stable when upgrading KClash so Android can verify the update.

An existing KClash Debug installation uses a different certificate and cannot be updated with the release APK. Export the profiles you need before uninstalling it; uninstalling removes its app data. Later signed releases can update this release normally.

After installation, import a profile from a subscription URL or a YAML file. Starting VPN mode requires Android's system VPN consent. Subscription services and proxy infrastructure are supplied by the user; this project does not include proxy nodes or subscription access.

## Android automation

Tasker, MacroDroid and other Android automation apps can start the KClash activity with one of these actions:

```text
com.lonnnnnng.flclash.action.START
com.lonnnnnng.flclash.action.STOP
com.lonnnnnng.flclash.action.TOGGLE
```

For example, from a connected device:

```bash
adb -s <device-serial> shell am start -a com.lonnnnnng.flclash.action.TOGGLE
```

Only these explicit actions are accepted; VPN startup still follows Android's consent flow.

## Build from source

The Kotlin Android build uses Java 17, Go 1.27.1, Android SDK platform 37 and NDK `28.2.13676358`. Clone the Kotlin branch with its Mihomo submodule:

```bash
git clone --branch kotlin --recurse-submodules https://github.com/lonnnnnng/FlClash.git
cd FlClash
./gradlew :app:assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. Run JVM tests and checks with:

```bash
./gradlew test :app:lintDebug
```

Build an unsigned, minified arm64 release package with:

```bash
./gradlew :app:assembleRelease :app:lintRelease \
  -Ptarget-platform=android-arm64 \
  -PkclashVersionName=0.1.0 \
  -PkclashVersionCode=1000
```

The unsigned APK is written to `android/app/build/outputs/apk/release/app-release-unsigned.apk`. Official release builds are signed by GitHub Actions using the project's independent Android signing key; signing secrets and keystore files must never be committed.

## Project boundaries

- The Kotlin Android application is developed on the `kotlin` branch.
- The Flutter [FlClash repository](https://github.com/chen08209/FlClash) is used only as a functional and visual reference; its application commits are not merged into the Kotlin mainline.
- Mihomo changes follow the `chen08209/Clash.Meta` FlClash branch through the configured `lonnnnnng/mihomo` fork and its `kclash-fixes` branch.

## License and support

KClash is distributed under the [GNU GPL v3.0](LICENSE). Please report reproducible bugs and feature requests through [GitHub Issues](https://github.com/lonnnnnng/FlClash/issues). Include the app version, Android version and device model; remove subscription URLs, credentials and private profile contents from logs before sharing them.
