# Suggested Commands

## Build & Run
- `./gradlew assembleDebug` — Build debug APK
- `./gradlew assembleRelease` — Build signed release APK
- `./gradlew installDebug` — Build and install on connected device/emulator

## Test
- `./gradlew test` — Run unit tests
- `./gradlew testDebugUnitTest` — Run debug unit tests
- `./gradlew testReleaseUnitTest` — Run release unit tests

## Lint & Clean
- `./gradlew lint` — Run Android lint
- `./gradlew clean` — Clean build artifacts

## CI
- Debug APK: pushed on push to main/fix**/feat** branches
- Release APK: triggered by GitHub Release publish event
- Debug keystore generated in CI via `keytool` (no checked-in keystore)
- Release signing via `scripts/ci_signing_setup.sh` + GitHub secrets

## Notes
- `./gradlew` wrapper is the only build entry point
- Debug keystore generated in CI via `keytool` (not checked in)
- Release version derived from Git tag (e.g., v2.2.2 → 2.2.2)
