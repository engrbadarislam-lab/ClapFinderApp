# ClapFinder

Clap to find your phone. ClapFinder listens in the background and, when it hears a clap,
makes the phone **ring, flash the torch and vibrate** — even on silent — so you can locate it.

Kotlin + **Jetpack Compose** (single activity, Navigation Compose), `compileSdk`/`targetSdk` **36**, `minSdk` **27**, Material 3.

## Open & run

**Android Studio (recommended)**
1. *File ▸ Open…* and select this `ClapFinder` folder.
2. Let Gradle sync (it downloads the Android Gradle Plugin + AndroidX from Google's Maven).
3. Pick a device/emulator (Android 7.0+) and press **Run**.

**Command line**
```bash
./gradlew assembleDebug        # APK -> app/build/outputs/apk/debug/
./gradlew installDebug         # build + install on a connected device
```
A `local.properties` pointing at your SDK is created automatically by Android Studio; for CLI
builds set `sdk.dir=/path/to/Android/Sdk` (or the `ANDROID_HOME` env var).

## Screen flow

Splash → 3 onboarding screens → Main → (permission sheet on first activate) → listening.
From Main you can open **Sound** and **Setting**.

## How it works

- **`ClapDetectionService`** — a foreground service (type `microphone`) with an ongoing
  notification and a partial wake lock, so detection survives the screen turning off.
- **`ClapDetector`** — reads 16 kHz mono PCM from `AudioRecord`, tracks a slow-moving noise
  floor and fires on a sharp transient above it. Sensitivity (Very High / High / Average) just
  scales the trigger threshold. A refractory window stops a single clap double-firing.
- **On detection** it plays the chosen alert, strobes the torch (`AlertController`), vibrates,
  and shows a **`FinderOverlay`** banner over other apps. All for the chosen duration
  (5 s / 15 s / 30 s / 1 min).
- **Schedule** — set quiet hours in Settings; the service stays alive but ignores claps inside
  the window (handles windows that cross midnight).

## Permissions requested

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | listen for claps |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | keep listening in the background (Android 14+) |
| `POST_NOTIFICATIONS` | ongoing + alert notifications (Android 13+) |
| `SYSTEM_ALERT_WINDOW` | draw the "Finding your phone" banner over other apps |
| `VIBRATE`, `WAKE_LOCK` | alert + reliable background detection |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | ask the OS not to doze the listener |

The activate button runs the whole chain: permission sheet → runtime mic/notification dialog →
overlay screen (optional) → battery-optimisation prompt (optional) → start listening.

## Alert sounds

To keep the APK tiny there are **no bundled audio files** — the nine alert sounds
(Steam Air Horn, Baby Laugh, Boom, Dog, Cat, Church, Door Bell, Horn, Drum) are **synthesised at
runtime** in `util/SoundSynth.kt` via `AudioTrack`.

**To use real recordings instead:** drop `.mp3`/`.ogg` files into `app/src/main/res/raw/`, map
each `SoundItem` to its resource id, and swap `SoundSynth` for a `MediaPlayer` (looping until
`stop()`), keeping the same `play(item, volume, durationMs)` / `stop()` signatures.

## Project layout

```
app/src/main/java/com/clapfinder/app/
├─ ClapApp.kt                 Application; notification channels; registers the app-open manager
├─ model/SoundItem.kt         the nine alert sounds
├─ util/                      Prefs, SoundSynth, AlertController, PermissionHelper   (unchanged)
├─ audio/                     ClapDetector, WhistleDetector                          (unchanged)
├─ service/                   ClapDetectionService, FinderOverlay (+ overlay_finder.xml) (unchanged)
├─ push/                      FCM service                                            (unchanged)
├─ ads/                       AdManager, AdConfig, AdGate, ConsentManager, AppOpenAdManager,
│                             AdComposables (banner / native), WelcomeBackOverlay, …
├─ data/billing/PremiumManager.kt   stub (isPremium = false) until billing is added
└─ ui/
   ├─ MainActivity.kt         the only Activity: WelcomeBackHost { NavHost }
   ├─ navigation/             routes: splash → onboarding → home → sound / settings / whistle / how_to
   ├─ screens/                one Composable per old Activity + ExitAppDialog
   ├─ components/             shared rows, activate ring + pulse, slider, permission chain + sheet
   └─ theme/                  colours (same as colors.xml), text style, easing
```

## Ads

| Where | Ad | Remote Config key |
|---|---|---|
| Splash | Google consent (UMP) → MobileAds init, no ad | — |
| Onboarding "Skip" / "Get Started" | interstitial via `AdGate` (loader first) | `ads_onboarding_inter` |
| Home: settings icon, How to use, Sound, Whistle Finder, More Setting | interstitial via `AdGate` on every tap | `ads_click_inter` |
| Home / Setting / Whistle bottom | adaptive banner | `ads_home_banner`, `ads_settings_banner`, `ads_whistle_banner` |
| Returning to the app | "Welcome Back" overlay + app-open ad | `ads_app_open` |

Production unit IDs live in `res/values/ads.xml` (debug builds always use Google test IDs).
`ads_master_enabled` switches everything off.

## Tuning notes

- If detection is too eager/lazy on a given device, adjust the `trigger` multipliers and the
  absolute floor in `ClapDetector`.
- App icon is an adaptive vector (`res/drawable/ic_launcher_fg.xml` on a green background); swap
  in your own foreground/background to rebrand.
