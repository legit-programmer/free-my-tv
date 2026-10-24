# Mi TV work status

The user confirmed **Android 9 (API 28)** on the 1 GB Mi TV 4A on 2026-10-24. The port lowers the installation and dex minimum to API 28; target SDK stays 33.

## Completed without assuming an OS

- Runtime performance and data-source toggles, with conservative defaults.
- Smaller recommendation downloads, staging textures, card textures and prepared-image cache; no preload of other tabs in lightweight mode.
- Static shader fallback, immediate spring transitions and low-frequency clock screensaver in lightweight mode.
- Explicit installed-package selection and separate optional debloating/restore actions.
- Pre-disabled package ownership protection; per-package progress persistence; allowlist validation at execution time.
- Launcher-only activation, preserved screensaver setting, recorded-package-only boot enforcement.
- Home-process and whole-app PSS metrics, plus available system memory.
- Read-only device inventory helper, JVM policy tests and Android build CI.

## Android 9 port

- Minimum SDK and dex API 28; version 1.1 / code 2. Compile SDK 34 and target SDK 33 remain unchanged.
- DisplayLayer caches drawing commands with Picture on API 28 and an isolated RenderNode backend on API 29+. No full-screen bitmap is added for the legacy clock/caption cache.
- ShaderProgram isolates AGSL classes behind an API 33 gate. Older systems retain gradient backgrounds, plain glass, text and app navigation, even with lightweight mode off.
- BitmapShader filtering's newer API is similarly isolated. Common sources are compiled separately against android-28/android.jar; only the three guarded graphics adapters require the modern compile SDK.
- HOME discovery resolves the real package-manager activity first. Role commands run only on API 29+. Chooser/error responses never count as an original launcher; activation stops before mutations if the original cannot be recorded.
- Launcher selection/restoration uses `cmd package set-home-activity --user 0`, available on Android 9. Memory reports accept the older TOTAL: format and query exact PIDs to avoid counting :home twice.
- Screensaver stop discards cached commands before releasing photo bitmaps.

## Deferred pending real device inventory

- Xiaomi/PatchWall package candidates and protected system components. Do not copy a generic online debloating list.
- Live TV package and remote shortcut mappings.
- On-device install, input/HDMI/CEC/remote/voice checks, restoration and reboot verification.
- Measured RAM and frame-time comparison. Build success is not proof of device compatibility or performance.

## Device validation after compatibility work

1. Record OS release/API level and package inventory using `bash tools/inspect-tv.sh` (read-only; no account or settings changes).
2. Test the launcher as an ordinary app first. Verify performance toggles and D-pad focus on the actual display.
3. Confirm the original launcher is recorded before activating replacement mode. Leave optional apps untouched.
4. Check Home, live TV, HDMI, settings, remote pairing and sleep/wake. Switch back to Original and check restoration.
5. Review candidate package identities. Scan and disable one known optional package, then restore it. Confirm a pre-disabled package stays disabled.
6. Compare available memory and PSS after the same idle interval, including both app processes, and measure navigation responsiveness. Repeat after reboot.

## Validation performed in development

- Android APK build using JDK 17 and Android SDK platform/build-tools 34.
- Nine debloat policy cases plus 16 compatibility cases covering HOME discovery, chooser/error responses, legacy role avoidance and old/new PSS formats.
- Shared sources compile against SDK 28. A separate instrumentation APK and CI emulator matrix exercise API 28 and 33 startup, navigation, settings/tuning, clock modes, recommendation fallback and HOME selection/restoration. See the PR checks for runtime results; physical TV validation and benchmarks remain pending.

## Operational notes

- Existing installations from upstream: first switch to Original with the old build before updating, so old automatic debloat state is restored before adopting the opt-in policy.
- Keep your original signing keystore for updates. Development/CI builds create a key when none exists; do not overwrite an installed app's key with an unrelated build key.
- Recommendation and weather caches remain on disk when toggled off; no new requests are started. Requests already in flight may complete. Turning recommendations off hides cards and releases the prepared-image cache.
- Weather being off hides its cards and stops refreshes; previously cached sunset data may still determine the day/night palette.
- The on-screen package list is intentionally restricted to reviewed candidates. An empty list is not an error and never justifies disabling arbitrary system apps.


## Cinematic Home validation

The default recommendations-off view now uses a static cinematic layout. Minimum SDK is now 28; target remains 33. On-device checks still required: 720p/1080p/4K legibility, long app names and 12-hour clocks, cold-start logos, shelf → shortcuts → shelf navigation, drawer and All Apps return, Home reset, recommendation toggle, pause/resume and idle CPU/PSS. The 250 ms idle callback checks state without invalidating unchanged layers; it is not a zero-wakeup event loop.
