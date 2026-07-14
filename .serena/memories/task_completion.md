# Task Completion Checklist

When a coding task is considered done, run these commands:

## Build Verification
- `./gradlew assembleDebug` — Must compile without errors

## Test
- `./gradlew testDebugUnitTest` — Run all unit tests

## Lint
- `./gradlew lint` — Run Android lint (address warnings where practical)

## Formatting
- No explicit formatter configured. Kotlin code style is `official` (gradle.properties). Follow existing code style.

## Notes
- No explicit formatter (ktlint, spotless) configured. Follow existing code style.
- No type checker beyond Kotlin compiler.
- Screenshot tests use Roborazzi (Compose).
