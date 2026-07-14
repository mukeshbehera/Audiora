# Navigation & Onboarding Flow

## Navigation Architecture
- `NavHost` with `startDestination = Screen.Library.route` (after structural fix: splash is OUTSIDE NavHost)
- Bottom nav: 5 tabs (Library, Create, Edit, Search, Settings)
- Bottom nav visibility: exclusion list (`showBottomNav = currentRoute !in {"splash", "welcome", "onboarding_folders"} && !currentRoute.startsWith("player/")`)

## Onboarding Flow (outside NavHost)
- `MainAppContainer` uses `when(phase)` block:
  - `"splash"` → `SplashScreen` (2.2s animation, reads DataStore)
  - `"welcome"` → `WelcomeScreen` (first-time welcome)
  - `"onboarding"` → `OnboardingFoldersScreen` (folder picker)
  - `"done"` → `MainAppShell` (NavHost + bottom bar)
- Splash/welcome/onboarding have NO Box/Scaffold/overlay — prevents bottom-bar flash

## Transition Animations
- Tab-to-tab: instant (`fadeIn(tween(0))`)
- Push navigation: `fadeIn(220ms) + scaleIn(0.96f)`
- Uses `targetState.destination.route?.substringBefore("?") in Screen.tabRouteStrings` to detect tab routes

## Notification Tap Navigation
- `pendingPlayerNavigation` State in `MainActivity`
- `LaunchedEffect` in `MainAppShell` navigates to `player/{bookId}` when set
- Uses `popUpTo(0) { inclusive = true }` to clear stack before navigating
