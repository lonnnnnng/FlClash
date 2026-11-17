# KClash 0.1.6

Maintenance release for the Kotlin Android application.

- Adds device acceptance coverage for the existing age-encrypted subscription flow, from download and key retry through profile import, core apply, and a proxied HTTPS request.
- Completes focused device verification for TUN setup failure and recovery boundaries. The Mihomo core revision is unchanged.
- Updates the app to versionCode `1006`. The package remains arm64-only and supports Android 7.0 (API 24) and later.

The age subscription scenario was verified on POCO F2 Pro/API 36 in proxy-only mode. VPN/TUN startup, scheduled Worker timing, low-API devices, and other OEMs are outside that acceptance run. The broader startup/auto-selection TLS investigation remains open.

The signed package is `KClash-0.1.6-android-arm64-v8a.apk`. KClash keeps the existing release signing identity and package ID `com.lonnnnnng.flclash`.
