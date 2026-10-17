# TS Gold Pulse Pro — Project Context

## Repository
- GitHub: https://github.com/dansaph13-jpg/PulsePro
- Local Termux path: `~/PulsePro`
- Main branch: `main`
- Android application ID / namespace: `id.tsgold.pulsepro`

## Latest recorded release
- Version: V16 / versionName `16.0`
- versionCode: `16`
- APK: `release/TS_Gold_Pulse_Pro_V16.apk`
- Build output before signing: `app/build/outputs/apk/release/app-release-unsigned.apk`
- APK signing key: `$HOME/.android/debug.keystore`
- Alias: `androiddebugkey`
- Known certificate SHA-256:
  `651410b0d539ed3f07b4f3bb2c27ac4c8c748cb8901b36044035b48b59ae1184`
- V16 was built successfully using `gradle assembleRelease -x lintVitalRelease`.
- Important: lintVitalRelease was skipped because the current targetSdk 32
  triggers ExpiredTargetSdkVersion. This does not mean all lint checks passed.
- V16 signature was verified and its certificate matched V15.
- Build success does not replace testing on the target phone.

## Build configuration recorded for V16
- Gradle installed in Termux: 9.8.1
- Java: 17
- compileSdk: 35
- minSdk: 29
- targetSdk: 32
- Dependency: OkHttp 4.12.0
- The repository root currently has no `gradlew`; use installed `gradle`
  unless the project wrapper is intentionally restored and verified.

## Important source files
- `app/src/main/java/id/ts/overlay/MainActivity.java`
  Main app screen and related price presentation.
- `app/src/main/java/id/ts/overlay/PriceOverlayService.java`
  Floating overlay, price display, clock, notification, and live-feed handling.
- `app/build.gradle`
  Application version, SDK settings, and dependencies.
- `release/`
  Sequentially named release APKs.

Read the current source before assuming any line numbers or implementation
details; this file records architecture and guardrails, not exact line numbers.

## Approved UI direction
- Compact, simple, premium terminal appearance.
- Gold outlines/dividers with dark green/navy surfaces.
- Main buy and sell prices side-by-side.
- Direction indicators directly below their corresponding prices.
- Price colors follow movement: green rising, red falling, gold unchanged.
- Do not add redundant labels or repeat prices in a second row.
- No `Rp` prefix in the compact price display.
- Use directional arrows without an extra plus/minus sign.
- Overlay right column: sell, spread, and seconds-only clock.
- Keep overlay compact; do not increase its height without approval.
- Notification format should remain compact and use the existing persistent
  notification ID 7 unless explicitly requested otherwise.

## Protected behavior
- Preserve the current Treasury price source and Pusher connection.
- Do not change subscriptions, channels, feed paths, or update behavior for
  a UI-only request.
- Do not add static promotional copy; show promo notifications only when a
  real promo trigger exists.
- Do not introduce a mini chart or countdown unless requested.

## Efficient update workflow
1. Read this file and `AGENTS.md`.
2. Check `git status --short`, branch, current version, and recent commit.
3. Inspect only the files and methods relevant to the requested change.
4. Make a narrow patch; avoid unrelated formatting and refactoring.
5. Run `git diff --check` and review the focused diff.
6. Build with the installed Gradle. For the current SDK configuration:
   `gradle assembleRelease -x lintVitalRelease`
   skips only the fatal lint task; do not represent this as a full lint pass.
7. Find the actual APK output path instead of assuming its filename.
8. Sign with the existing key, verify the signature, and compare its
   certificate fingerprint with the previous release.
9. Copy the signed APK to `release/TS_Gold_Pulse_Pro_V<N>.apk`, incrementing
   the version sequentially.
10. Check package ID, versionCode/versionName, APK size, and signature.
11. Test the changed behavior on the phone when possible.
12. Stage only the explicitly intended source, documentation, and release
    artifacts. Never include backups, temporary scripts, unrelated data,
    or generated logs.
13. Commit and push to `origin main` only after the checks above.

## V16 notes
- The release source changes focus on UI, price text formatting, colors,
  and the overlay's right-column layout.
- Clock in the overlay uses seconds-only format.
- The build succeeded with lintVitalRelease excluded.
- Treasury/Pusher changes were not part of the intended UI patch; review
  diffs if future changes touch the overlay service.
