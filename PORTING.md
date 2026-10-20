# Mi TV work status

The user asked us not to assume or implement an OS version until they reconfirm it. This work deliberately keeps the existing SDK requirements and renderer API compatibility unchanged.

## Completed without assuming an OS

- Runtime performance and data-source toggles, with conservative defaults.
- Smaller recommendation downloads, staging textures, card textures and prepared-image cache; no preload of other tabs in lightweight mode.
- Static shader fallback, immediate spring transitions and low-frequency clock screensaver in lightweight mode.
- Explicit installed-package selection and separate optional debloating/restore actions.
- Pre-disabled package ownership protection; per-package progress persistence; allowlist validation at execution time.
- Launcher-only activation, preserved screensaver setting, recorded-package-only boot enforcement.
- Home-process and whole-app PSS metrics, plus available system memory.
- Read-only device inventory helper, JVM policy tests and Android build CI.

## Deferred pending OS confirmation

- Lowering min SDK / dex minimum (currently 31); changing target SDK.
- Replacing RenderNode/RecordingCanvas paths or isolating newer shader APIs for older systems.
- OS-specific HOME role commands and restoration fallbacks.
- Testing ADB transport, boot/background restrictions and job behavior on the target firmware.

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
- Nine JVM cases covering optional package disable/restore, pre-disabled packages, missing packages, protected/unapproved packages and external re-enabling.
- No TV, emulator UI run or target-device benchmark was available.

## Operational notes

- Existing installations from upstream: first switch to Original with the old build before updating, so old automatic debloat state is restored before adopting the opt-in policy.
- Keep your original signing keystore for updates. Development/CI builds create a key when none exists; do not overwrite an installed app's key with an unrelated build key.
- Recommendation and weather caches remain on disk when toggled off; no new requests are started. Requests already in flight may complete. Turning recommendations off hides cards and releases the prepared-image cache.
- Weather being off hides its cards and stops refreshes; previously cached sunset data may still determine the day/night palette.
- The on-screen package list is intentionally restricted to reviewed candidates. An empty list is not an error and never justifies disabling arbitrary system apps.
