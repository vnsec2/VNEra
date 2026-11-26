# Revera paired Wear OS smoke test (Windows)

These are operator instructions. The phone owner supplies and operates the handheld side; do not build, install, restart or change someone else's phone/AVD to run this test. A watch boot, a system pairing badge and a successful Revera Data Layer exchange are three separate checks.

## Choose matching endpoints

| Handheld build | Watch build from `base/` | Expected application ID | Signing |
| --- | --- | --- | --- |
| Ordinary debug | `./gradlew :wear:assembleDebug` | `app.revera.dev` | Same Android debug key on both, not merely two debug keys |
| Optimized dev (`-PoverDev`) | `./gradlew :wear:assembleRelease -PoverDev` | `app.revera.dev` | Same release key if configured; otherwise same debug key. A debug watch also works if its cert matches. |
| Ordinary release | `./gradlew :wear:assembleRelease` | `app.revera` | Same release key on both for published builds |
| Mix Lab (only if intentionally testing it) | Matching build type with `-PautomixLab` | `app.revera.automixlab` | Compare certificates as above |

Use a supported Gradle JDK (JDK 21 on this host) and set `ANDROID_HOME` to the installed SDK. Watch APKs are under `base/wear/build/outputs/apk/{debug,release}/`. Before installing, run `$ANDROID_HOME/build-tools/37.0.0/aapt.exe dump badging <apk>` and `$ANDROID_HOME/build-tools/37.0.0/apksigner.bat verify --print-certs <apk>` on the watch APK and the **exact** handheld artifact provided by its owner. Compare the package and signer SHA-256. A matching label or build type is not evidence of a match. Do not share signing keys or account/session data. Watch release reads the same `MYSPOTI_KEYSTORE*` / `MYSPOTI_KEY_*` inputs or `base/keystore/keystore.properties` as the handheld.

The previously installed `Orpheon_API35` is a **phone** AVD, `google_apis/x86_64`, not a Wear image and not a Google Play Store image. It is not the recommended first choice for Pair Wearable. A two-AVD test needs a compatible Android phone AVD with the **Google Play** image and a working companion app, plus a phone APK that actually runs on that image. Current Revera handheld release splits are ARM64/ARMv7 even for the universal APK, and the phone build excludes x86 native libraries. Do not assume a stock APK will run on an x86_64 phone image; have the phone owner verify any ARM translation and APK compatibility first. Prefer a compatible ARM64 physical Android handset with the intended Revera build already installed if no suitable phone emulator/artifact is available.

## Create and pair the watch

From Git Bash, if the watch image/AVD is not already present:

```bash
export ANDROID_HOME=C:/Users/User/AppData/Local/Android/Sdk
SDKCLI="$ANDROID_HOME/cmdline-tools/latest/bin"
"$SDKCLI/sdkmanager.bat" --list_installed
"$SDKCLI/sdkmanager.bat" 'system-images;android-34;android-wear;x86_64'
"$SDKCLI/avdmanager.bat" create avd -n Revera_Wear_API34 -k 'system-images;android-34;android-wear;x86_64' -d wear_round
"$ANDROID_HOME/emulator/emulator.exe" -list-avds
```

If asked for a custom hardware profile, answer `no`. The equivalent UI setup is Android Studio → Device Manager → Create device → Wear OS → Wear OS Round → Wear OS 5 / API 34 x86_64. Do not choose a phone system image. AEHD acceleration was available on this host. The watch APK must also be compatible with the emulator ABI; if installation returns `INSTALL_FAILED_NO_MATCHING_ABIS`, inspect APK `lib/` entries and use a dedicated x86_64-compatible test watch APK or compatible ARM watch hardware. Rust CDN transport is ARM-only here; the Kotlin CDN path is separate and this test does not validate emulator Widevine/native playback.

In Device Manager use **⋮ → Pair Wearable**, choose the other endpoint, and follow the pairing assistant and phone companion-app prompts. For a physical phone, follow the assistant's USB/ADB setup as its owner; for a virtual phone, use the Play Store image and install/update the companion app if required. Verify **View Details → Paired Devices** and complete watch setup before testing Revera. See [Android's pairing guide](https://developer.android.com/training/wearables/get-started/connect-phone). Simply starting both emulators, placing them on the same network, or toggling Bluetooth is not system pairing.

## Verify application traffic, then recovery

1. Have the phone owner launch the matching Revera build and sign in to Spotify. Use a fresh watch instance/snapshot for the first pass so cached playlists and a saved LAN secret cannot make a failing Data Layer look healthy.
2. Identify the **watch** serial with `adb devices -l` and, on that emulator serial only, confirm `adb -s <watch-serial> emu avd name` returns `Revera_Wear_API34` before doing any install. Install the watch APK with `adb -s <watch-serial> install -r <watch-apk>`. Open Revera and swipe to **Your music**. Expect Liked Songs and at least one known account playlist. Check watch logcat (`adb -s <watch-serial> logcat -d -s Revera:D`) for fresh `watch link: /orpheon/session via Data Layer` and `watch link: /orpheon/library via Data Layer` entries. The legacy `/orpheon/*` paths are the wire protocol, not the package name. Keep logs local and do not capture message bodies or credentials.
3. Tap a playlist and check the correct title/metadata. If playback works, separately verify position progress, pause/resume and audible output; an emulator DRM/audio failure does not invalidate an observed Data Layer response. If testing the extensions path, use a known harmless installed extension on the phone, tap **Phone extensions**, and check the copied count and `/orpheon/extensions via Data Layer`. The `/orpheon/tracks` request is currently used for extension-backed playback, not necessarily every playlist tap.
4. Negative check on a fresh watch snapshot and with the phone owner's approval: attempt without a reachable matching phone companion (e.g. phone Revera absent, or only the other package/channel installed). System pairing can still show connected, but there must be no successful session or library fetch. Restore the matching endpoint and use **Try again** for the library; reopen Revera as needed to retry the initial session. On a previously linked watch, a cached library is expected offline and is not a successful request.
5. Test **LAN fallback separately** only after successful Data Layer session bootstrap. Keep the phone app alive and both endpoints on a network with private-address and multicast reachability. Interrupt the Data Layer while preserving LAN access; Bluetooth off alone is not proof it is interrupted because Google can relay it. Request the library or sync extensions again and require a fresh `via Wi-Fi` log marker. Verify Data Layer recovery after restoring the link. If the AVD's virtual network cannot reach the phone's private address or mDNS advertisements, record LAN as untested and repeat on suitable same-LAN hardware. LAN is not a workaround for a package/signature mismatch on a fresh watch.

Record the AVD/image, package, certificate match, pairing state, fresh session/library results, extensions, and playback/LAN as separate outcomes.
