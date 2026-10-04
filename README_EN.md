# Mi Market Purify

> LSPosed module for Xiaomi App Store purification and enhancement, built with AI-assisted refactoring based on `callng/NewFuckMarketAds`.

<p align="center">
  <a href="./README.md">简体中文</a> ·
  <a href="./README_EN.md">English</a>
</p>

- **License**: GPL-3.0
- **Target**: Xiaomi App Store (`com.xiaomi.market`), only effective within the app store process
- **Network behavior**: No background network requests, no telemetry. The only network call is the "Check for Updates" action on the About page, which voluntarily queries GitHub Releases (user-initiated, no data reporting).

---

## Features Overview

### Ad Purification
- Splash screen ads
- Ads/recommendation popups when switching foreground tabs
- Home feed ads (video / app recommendations, hot word bar)
- Search suggestions / search page / search result recommendations
- App upgrade page and download page recommendations
- App detail page ads, comment section and recommendation slots, packed recommendations, bottom multi-button promo bar, ad lists in browser download dialogs
- Ranking page ads / promotional cards
- Home top bar cloud-controlled promo slots ("Watch Drama", "Short Drama", etc., including newly added promo slots under different names)
- Cloud-controlled activity entry to the left of the search box
- "Claim Fruit" welfare activity entry
- Home page floating ads
- **Back floating ads**: Block "Return to Toutiao" and similar floating windows that appear when pressing back
- **Home page dialog promotions**: Block Dialog promotions shown when entering the home page
- **Install-after recommendations**: Block "Users Also Liked" / "People Also Liked" recommendation popups that appear after clicking install (covers all recommendation scenarios: home page install, detail page install-then-back, search guide page, etc.)

### UI Cleanup
- App security check view
- "My" page: App recommendations & promotions, official app management entry, cleanup & uninstall, personal info area (avatar/nickname/messages/favorites), upgrade card orchard background
- Detail page "Featured"
- Bottom bar customization: Select which tabs to keep, hide the rest
- Upgrade history page recommendations (selected recommendations / popular downloads / "Others also installed")
- Search page "Also Watching" ("People who searched xxx are also watching")
- Cloud-controlled promo sub-tabs at the top of home / ranking pages
- "Upgrade All" button and auto-update switch on the update page
- Store "Upgrade Reminder" dialog (independently toggleable)
- **Push floating notifications**: Block MiPush floating notifications and game promotion floating windows
- **Upgrade floating card**: Block floating upgrade prompts shown when a new version is detected
- **Block background silent download**: Prevents the store from automatically downloading app updates in the background, saving data and battery
- **Long-press to open plugin**: Long-press the top download button (`DownloadWithCheckin`) to jump to the plugin's main page

### Feature Enhancements
- **Floating bottom bar**: Replace the docked native bottom bar with a Compose-drawn floating rounded capsule, with page content extending beneath
  - Icons copied from the store's own Drawables, taps forwarded to the native TabView — **navigation and analytics semantics remain unchanged**
  - Optional iOS-style liquid selection highlight (stretching capsule + landing overshoot + icon elastic scaling), 3D liquid effect
  - Optional label text display
  - Customizable: bar background color, selected background color, text/icon highlight color, corner radius, bottom margin
- **Force enable Super Island download** (ignores server-side grayscale, independently toggleable)
- **Detail fixes**: Show non-genuine apps, unhide updates, etc.
- **Tab injection**: Injects an "Update" entry into the native bottom bar that goes directly to the update page (independent of the floating bar, persists even when floating is off); pending update count displayed on the "Update" tab
- **Tab deep cleanup**: Hidden tabs are not only hidden but also skip data loading, saving data and memory
- **Restart App Store**: A "⟳" restart button on the top-right corner of the main page and all sub-pages, one-tap stop and restart of the store process

### Dark Mode Support
- Module UI (main page / settings / about / search) fully supports system dark mode
- `SettingItem` title text explicitly uses `onSurface` color, ensuring white text in dark mode
- Privacy policy WebView forced to follow system dark mode (`isAlgorithmicDarkeningAllowed` on API 33+, `FORCE_DARK_ON` on API 29-32)

### Stability (Protective safety net, not controlled by individual switches, only gated by the master switch)
- Intercepts the store's self-destruct mechanism that deletes itself after consecutive crashes
- Breaks rollback deadlock and resets crash counters
- Automatic config restoration after backup and rollback
- All hook points wrapped in try-catch; single point failure does not affect other features

---

## UI and Interaction

The module's main page, sub-settings pages, About page, **Search page**, and the **floating bottom bar** injected into the app store are all built with **Compose + MiuiX** (`top.yukonga.miuix.kmp`), visually aligned with HyperOS / MiuiX design language:

- **Theme**: Unified `MiuixTheme` usage, dark/light follows system (`darkColorScheme()` / `lightColorScheme()`), colors from `MiuixTheme.colorScheme`, matching the native store's frosted glass;
- **Dark mode**: All text uses explicit theme colors (`onSurface` / `onSurfaceVariantSummary`), no longer relying on XML theme defaults, ensuring text is clearly readable in dark mode; Privacy policy WebView automatically follows system dark mode;
- **Grouped cards**: Controls in the same group are placed in one rounded container, **no divider lines within groups**, only whitespace separation;
- **Full-row clickable**: Tap title or empty space to toggle switch / enter sub-page, touch target minimum 48dp;
- **Status expression**: Module availability indicated by top status card (active green / inactive orange), not by dimming controls; when master switch is off, feature rows turn gray and become non-clickable;
- **Entry summaries are computed**: The "N enabled / N hidden / filter off" text on the right side of main page and sub-page entry rows **auto-recalculates** when returning from sub-pages (subscribes to `refreshSignal`, refreshes on `onResume`);
- **Search**: Search bar at the top of the main page, clicking opens a dedicated search page with real-time keyword filtering of all features, categorized recommendation grid at the bottom (rotational recommendation per category, up to 10 cards);
- **Discover**: Randomly recommends 3 feature switches from different categories on the main page, auto-refreshes every 15 seconds, independently toggleable;
- **Restart button**: A "⟳" restart button on the top-right corner of the main page and all sub-pages, one-tap stop and restart of the store process;
- Supports edge-to-edge, system bar insets fill the fixed header top and content area bottom; main / About pages reserve space for the floating bar in scroll content to avoid bottom blank bar and last item occlusion.

> The app card on the About page links to the source repository; "Check for Updates" is available at the bottom.

---

## Main Page & Settings Structure

| Location | Content |
| --- | --- |
| Main · Search Bar | Click to open search page, keyword search across all features |
| Main · Discover | Randomly recommends 3 feature switches from different categories (auto-refreshes every 15s, toggleable) |
| Main · Master Switch | Turns off all features when disabled |
| Main · UI Settings | Ad purification (sub-page), bottom bar customization (sub-page), floating bar config (sub-page), "My" page cleanup (sub-page), other UI cleanup (sub-page) |
| Main · Advanced | Super Island download, detail fixes, upgrade reminder dialog |
| Main · Module | Hide launcher icon, random recommendations (toggle), debug mode |
| Search · Results | 2-column grid cards with category color bars, click to navigate to settings page |
| Search · Recommendations | Feature recommendation grid (rotational per category, up to 10 cards) |
| Sub · Ad Purification | Splash, foreground ads/recommendations, feed, search, upgrade/download, detail, rankings, fruit entry, activity entry, detail extras (promo bar), floating ad, back floating ad, home dialog, install-after recommendation |
| Sub · "My" Page | App recommendations, official entry, cleanup & uninstall, personal info, security check, card background, card expand, tab badge |
| Sub · Bottom Tabs | Tab filter (multi-select), deep cleanup (skip data loading for hidden tabs), update tab injection |
| Sub · Floating Bar Config | Enable floating bar, liquid highlight, 3D liquid, show labels, colors, corner radius, bottom margin |
| Sub · Other UI Cleanup | Detail "Featured", upgrade history, search "Also Watching", top bar promo, upgrade all button, auto-update switch, Push floating, upgrade float card, block background download, long-press to open plugin |
| About | App card (links to repo), feature summary, check for updates, license & credits |

- The multi-select for "Which tabs to keep" is placed directly below the "Filter bottom tabs" switch within the same card; the entire multi-select block is hidden when the switch is off;
- Main page and all sub-pages, About page share `SettingsBaseActivity` and `ui/components` component set (grouped cards, switch rows, nav rows, section headers, sliders, color pickers, checkbox rows), avoiding duplicate style drift.

### Check for Updates (About page)
- Clicking "Check for Updates" requests GitHub Releases on a background thread, results shown on the main thread in a **MiuiX-style dialog** (Material3 `AlertDialog` wrapped with `MiuixTheme`);
- New version found: Displays version number, size, and release notes. "Download and Install" uses in-app download; "Go to Release Page" opens the Release page as a fallback;
- During download, a **MiuiX progress dialog** appears: shows a ring + linear progress bar with percentage when `Content-Length` is known; degrades to indeterminate ("Fetching download info…") when the server doesn't return `Content-Length`. After download completes, automatically invokes the system installer via FileProvider;
- Already up-to-date or request failed: Lightweight Toast notification.

---

## Installation & Activation

1. Device has **LSPosed (or compatible libxposed framework)** installed with root access;
2. Install this module APK, check-enable it in the framework's **module list**;
3. Check "App Store" (`com.xiaomi.market`) in the module's **scope**;
4. **Restart the app store** (also recommended to restart SystemUI to ensure injection takes effect).

The main page status card will show "Activated" with the current framework; if it shows "Module Not Activated", please check steps 2 and 3 and restart the app store.

### When Do Switches Take Effect
Switches read remote preferences in real-time on every hook invocation. With frameworks that support remote preferences (LSPosed), **restart is generally not needed**; on individual ROMs that cache snapshots, restarting the app store once is sufficient. When the master switch is off, all hooks are skipped entirely.

### Hide Launcher Icon
Only disables the desktop launcher's `activity-alias` (`.LauncherAlias`). `MainActivity` always remains enabled, so the framework's module list can still open the main page. Devices previously locked out by older versions will have their entry automatically restored on overlay install.

---

## Technical Details

- **Hook framework**: `libxposed` 101.0.0 + `ezXHelper`
- **UI stack**: Module app and floating bar both use **Jetpack Compose + MiuiX** (`top.yukonga.miuix.kmp` 0.9.4, including `miuix-blur-android` frosted glass)
  - Compose BOM for unified versions; `material3` for `AlertDialog` / `TextButton` etc.
  - Floating capsule shape depends on `io.github.kyant0:shapes` (provides `Capsule`)
- **Build**: AGP 9.x + Kotlin 2.4.20, JDK 21, `compileSdk 37` / `minSdk 33` / `targetSdk 36`
  - Release builds enable R8 full mode + `shrinkResources`, retain only `zh-rCN` / `en` resources to reduce size
  - Remote preferences fetched via cross-process binder, config written to target app SP, high-frequency hook paths use 500ms TTL cache to reduce overhead
- **Config sync**: libxposed remote preferences, module writes, hook reads, fixed group `settings`; falls back to reading target app's SP file when remote preferences unavailable
- **UI element hiding**: Uses **resource IDs** and **badge text** as anchors instead of hardcoded View layer/position; `View.onAttachedToWindow` full interception + main/detail page `onResume` tree rescan dual-path for reliable coverage
- **Recommendation blocking**: Hooks `ClientAIAdReRankEngine.compute()` at the recommendation engine level to return an empty list from the source, covering all recommendation scenarios (home install, detail install-then-back, search guide); supplemented by text keyword matching as fallback
- **Multi-version compatibility**: Hook logic is compatible across HyperOS / MIUI versions, avoiding hardcoded version numbers

### Directory Overview
```
app/src/main/java/com/mars/mimarketpurify/
├── MainHook.kt / apps/Market.kt      # Entry point & registration
├── Settings.kt / SettingsBaseActivity.kt / MainActivity.kt
├── SubSettingsActivity.kt            # Sub-settings pages (Compose)
├── SearchActivity.kt                 # Search page (Compose)
├── FeatureRegistry.kt                # Feature registry (shared by search & recommendations)
├── ui/MainScreen.kt / ui/components/ # Main page & shared Compose components
├── hooks/market/                     # Purification / enhancement hooks
│   ├── InstallRecommendBlocker.kt    # Block install-after recommendations
│   └── LongPressJumpToPlugin.kt      # Long-press to open plugin main page
├── util/                             # Floating bar host, native TabBar positioning, download, update check
└── init/                             # Xposed initialization, resource hiding, package registration
```

---

## Credits

- [callng/NewFuckMarketAds](https://github.com/callng/NewFuckMarketAds) — Original codebase
- [lisrain/NewFuckMarketAds_Fork](https://github.com/lisrain/NewFuckMarketAds_Fork) — Stability enhancements & Super Island
- [HowieHChen/XiaomiHelper](https://github.com/HowieHChen/XiaomiHelper) — App store rules
- [AritxOnly/HyperModifier](https://github.com/AritxOnly/HyperModifier) — Floating bottom bar logic & Compose + MiuiX integration approach

## Disclaimer

This project is solely an AI technology research outcome. Please do not use it for commercial purposes or in violation of platform rules. All risks arising from usage are borne by the user.
