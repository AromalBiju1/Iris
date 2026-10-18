# IRIS — Privacy-First Hybrid Android Browser
**Engineering Specification & Implementation Contract**

**Version:** 2.0  
**Date:** 2026-10-18  
**Status:** Approved architecture — implementation pending  
**Repository:** `git@github.com:AromalBiju1/Iris.git`  
**Project directory:** `~/Desktop/Iris/`  
**Platform:** Android  
**Primary language:** Kotlin  
**UI:** Jetpack Compose + Material 3  
**Browser engine:** Android System WebView

---

# 1. Product Vision

Iris is a lightweight, privacy-first, search-oriented Android browser combining selected capabilities from established browsers without reproducing their architectural complexity.

Inspiration:

- **Brave:** privacy protections, tracker blocking, HTTPS-first browsing.
- **Firefox:** practical tab management, readable pages, browsing controls.
- **Vivaldi:** configurable UI and power-user settings.
- **Arc:** minimal, search-centric navigation.
- **DuckDuckGo Browser:** simple privacy controls and quick data clearing.

Iris must remain independently functional without accounts, proprietary APIs, paid services, laptop connectivity, or cloud AI.

### Core principles

1. Privacy by default.
2. Fast startup and responsive navigation.
3. Minimal background activity.
4. User-controlled behavior.
5. Offline capabilities where technically possible.
6. Small, understandable modules.
7. No forced accounts or synchronization.
8. No telemetry, analytics SDKs, advertising SDKs, or remote crash reporting.
9. Local-first storage and processing.
10. Optional features must not degrade the core browser.

**Important:** Iris does not claim complete anonymity. Search engines, destination websites, ISPs and network infrastructure may still observe traffic or identifying information. WebView's privacy characteristics depend partly on the installed WebView provider.

---

# 2. Implementation Priorities

Use three milestone tiers.

## P0 — Functional Browser (Mandatory)

- Android project builds and launches.
- Working WebView browsing.
- URL/search detection.
- Multiple tabs.
- Back, forward, reload, stop.
- Search result interface.
- SearXNG primary provider.
- DuckDuckGo Lite fallback.
- Search provider transparency.
- Bookmarks and history.
- Browser settings.
- No internet connectivity handling.
- Secure WebView configuration.
- Basic privacy controls.
- Debug and release APK generation.

## P1 — Hybrid Browser Features

- Domain-based tracker and ad blocking.
- Per-site controls.
- HTTPS-first navigation.
- Tab suspension and restoration.
- Reader mode.
- Local history/bookmark full-text search.
- Offline saved-page library.
- Search shortcuts.
- Customizable navigation layout.
- Genuine incognito isolation where supported.
- Data clearing controls.
- Lightweight browsing diagnostics.

## P2 — Optional Enhancements

- Tab groups.
- Gesture customization.
- Advanced cosmetic customization.
- Semantic bookmark search.
- Optional local AI page summarization.
- Optional local AI page question-answering.
- Extended offline search capabilities.

P2 must not block P0 or P1 delivery.

---

# 3. Technology Stack

| Layer | Technology |
|---|---|
| Programming language | Kotlin |
| UI | Jetpack Compose / Material 3 |
| Browser rendering | Android System WebView |
| WebView compatibility | AndroidX WebKit |
| HTTP networking | OkHttp |
| Concurrency | Kotlin Coroutines / Flow |
| Persistence | Room / SQLite |
| Preferences | Jetpack DataStore |
| Search parsing | Kotlin serialization or Moshi; Jsoup for HTML |
| Local text search | SQLite FTS5, where supported |
| Dependency injection | Manual constructor injection |
| Build | Gradle Kotlin DSL |
| Testing | JUnit, MockWebServer, Android instrumentation tests |

Use a compatible Android SDK target, Java 17 toolchain, and reproducible Gradle wrapper.

Select stable dependency versions after inspecting the locally installed SDK and the current official Android release documentation.

Avoid unnecessary libraries and complex dependency-injection frameworks.

Use a practical minimum SDK such as API 26 unless validation demonstrates a better requirement.

Do not introduce Flutter, React Native, Electron, a bundled Chromium engine, or an external application server.

---

# 4. High-Level Architecture

```text
IRIS APPLICATION
│
├── Presentation
│   ├── BrowserScreen
│   ├── SearchScreen
│   ├── TabSwitcher
│   ├── BookmarksScreen
│   ├── HistoryScreen
│   ├── OfflineLibraryScreen
│   └── SettingsScreen
│
├── Browser Core
│   ├── BrowserController
│   ├── WebViewFactory
│   ├── TabManager
│   ├── NavigationController
│   ├── SessionManager
│   └── ReaderController
│
├── Search Core
│   ├── SearchRouter
│   ├── SearchProvider
│   ├── SearXngProvider
│   ├── DdgLiteProvider
│   ├── SearchResultNormalizer
│   ├── HealthMonitor
│   └── QueryClassifier
│
├── Privacy Core
│   ├── RequestBlocker
│   ├── FilterListManager
│   ├── SitePermissionManager
│   ├── HttpsPolicy
│   ├── PrivateSessionManager
│   └── DataCleaner
│
├── Local Data
│   ├── BookmarkRepository
│   ├── HistoryRepository
│   ├── SettingsRepository
│   ├── SessionRepository
│   ├── OfflinePageRepository
│   └── LocalSearchIndex
│
└── Optional Extensions
    ├── LocalAIProvider
    └── SemanticSearch
```

Architecture requirements:

- Core logic should be independent of Compose screens.
- Search providers must implement one interface.
- Repository operations must not block the main thread.
- WebView objects must never be retained by an Activity-scoped ViewModel across Activity destruction.
- All optional modules must have enable/disable controls.
- No unnecessary periodic background workers.
- Browser state should survive ordinary configuration changes when practical.
- Network operations must be cancellable.

---

# 5. Search System

## 5.1 Search Provider Priority

Iris must use the following default search order:

1. Self-hosted SearXNG.
2. DuckDuckGo Lite.
3. Offline local index, only when no online provider can answer and matching locally indexed content is available.

Local search is not a substitute for live internet search.

### SearXNG URLs

User-editable defaults:

```text
http://100.110.99.98:8888
http://192.168.31.65:8888
http://127.0.0.1:8888
```

These are candidate endpoints, not guaranteed reachable addresses.

`127.0.0.1` on Android refers to the Android device or emulator. It reaches the laptop only if an explicit forwarding mechanism such as `adb reverse tcp:8888 tcp:8888` is active.

## 5.2 Search Provider Interface

Provide a common interface conceptually equivalent to:

```kotlin
interface SearchProvider {
    val engine: SearchEngine

    suspend fun search(
        query: String
    ): SearchResponse
}
```

Normalized results:

```kotlin
data class SearchResult(
    val url: String,
    val title: String,
    val snippet: String,
    val engine: SearchEngine
)
```

SearchResponse must distinguish:

- Success with results.
- Success with genuinely zero results.
- Connection timeout.
- Provider unavailable.
- Parse failure.
- Rate limiting.
- Backend failure.
- Cancellation.

Never interpret every empty response as an outage.

## 5.3 SearXNG Behavior

Use:

```text
GET /search?q=<encoded>&format=json
```

Requirements:

- Configurable endpoint order.
- Concurrent health checks with a shared two-second probe deadline.
- Prefer the highest-priority working endpoint.
- Cache the last healthy endpoint temporarily.
- Invalidate cached reachability on connectivity changes.
- Four-second search deadline.
- Validate response format before parsing.
- Reject malformed or dangerous result URLs.
- Do not send the same query to every endpoint unnecessarily.
- Do not submit private searches to fallback providers unless required by the user's chosen fallback policy.

A health check confirms basic service availability, not necessarily successful upstream searching.

## 5.4 DuckDuckGo Lite

Use HTTPS.

Initial endpoint candidate:

```text
https://lite.duckduckgo.com/lite/
```

Implement actual request handling according to the currently working Lite interface rather than assuming its HTML or query method is permanent.

Requirements:

- Five-second timeout.
- Dedicated HTML parser.
- Checked-in parser fixtures.
- HTML entity decoding.
- Redirect URL normalization.
- Safe link filtering.
- Graceful handling of CAPTCHA, rate limiting and HTML structure changes.

Do not bypass CAPTCHA or anti-bot protections.

Because Lite is an HTML interface rather than a guaranteed stable JSON API, its integration must remain replaceable.

## 5.5 Fallback Policy

Default behavior:

```text
USER QUERY
   |
   v
SEARXNG AVAILABLE?
   | YES
   v
SEARXNG SEARCH
   | FAILURE
   v
DUCKDUCKGO LITE
   | FAILURE
   v
LOCAL INDEX
   | NO MATCH
   v
NO-CONNECTION / SEARCH ERROR
```

Provide a privacy setting:

**Allow automatic fallback to DuckDuckGo: ON**

When OFF, never send a failed SearXNG search to DuckDuckGo.

Search UI must clearly identify the responding provider.

Avoid retry storms or repeated calls on every keystroke.

## 5.6 URL and Search Detection

Implement a deterministic classifier.

- Explicit `http://` or `https://`: navigate.
- Valid domains: navigate with HTTPS preferred.
- `localhost` or valid IP addresses with optional ports: navigate under explicit local-network policy.
- Multiple ordinary words: search.
- Unknown schemes: reject unless handled explicitly and safely.
- Bare strings without domain structure: search.

Handle IPv6, internationalized domains and URLs containing paths.

Do not assume that every string containing a period is a valid URL.

---

# 6. Browser Interface

## 6.1 Design Language

Use a minimal Material 3 interface.

Visual characteristics:

- Neutral background.
- Restrained accent color.
- Rounded address/search bar.
- Compact navigation controls.
- Clean typography.
- No unnecessary gradients.
- No excessive animation.
- Native light/dark theme support.
- Dynamic color optional.
- One-handed navigation.

Avoid the generic purple-gradient AI-generated design aesthetic.

## 6.2 Main Browser Screen

Default layout:

```text
┌──────────────────────────────┐
│                              │
│          WEB PAGE            │
│                              │
│                              │
│                              │
│                              │
├──────────────────────────────┤
│  ◀    ▶    ⟳    Tabs    ⋮   │
├──────────────────────────────┤
│  🔒 Search or enter URL      │
└──────────────────────────────┘
```

The actual UI must adapt to Android screen dimensions, system insets, keyboard visibility and landscape orientation.

Required actions:

- Navigate backward/forward.
- Reload or stop loading.
- Enter address or query.
- Open tab switcher.
- Access browser menu.
- Add bookmark.
- Share page through Android Sharesheet.
- Open page in external application only after appropriate user action.

Provide a configurable top or bottom address bar.

## 6.3 Search Screen

Must display:

- Search query.
- Search engine name.
- Result title.
- URL/domain.
- Snippet.
- Loading state.
- Error/retry state.
- Empty-results state.

Search results should open in the current tab by default, with long-press options for a new tab.

Use URL and output escaping to avoid treating retrieved content as trusted UI instructions.

## 6.4 New Tab Page

Keep it lightweight.

Required:

- Prominent search bar.
- Recent bookmarks or favorites.
- Optional recent browsing entries.
- Incognito access.
- Settings shortcut.

No network request simply to display the new tab page.

---

# 7. Tab Management

Implement a TabManager with:

- Stable tab identifiers.
- New tab.
- Close tab.
- Switch tab.
- Reopen recently closed tab.
- Restore normal browsing sessions.
- Per-tab title and current URL.
- Per-tab back/forward navigation.
- Per-tab load progress.
- Private versus normal session designation.

### Performance

Do not keep unlimited fully active WebViews in memory.

Use a configurable active-WebView limit, initially three.

Inactive tabs may be suspended, destroyed and reconstructed from stored navigation state.

Avoid implying that restoring a URL perfectly restores form state, JavaScript state or authenticated application state.

Persist safe tab metadata rather than WebView references.

Never restore private tabs.

Support tab suspension under memory pressure.

---

# 8. Privacy and Security

## 8.1 Baseline

- No analytics.
- No accounts.
- No telemetry.
- No remote crash reports.
- No advertising SDKs.
- No browsing-history uploads.
- No query logging in production.
- No external configuration fetches without user-visible purpose.
- No insecure JavaScript-to-native interfaces for arbitrary webpages.
- Release builds must disable WebView debugging.
- Avoid exporting internal Android components.
- Validate incoming intents.

## 8.2 WebView Security

Configure:

- JavaScript enabled by default for site compatibility.
- Third-party cookies blocked.
- File access disabled where unnecessary.
- Universal file URL access disabled.
- File content access disabled where unnecessary.
- Safe Browsing enabled when supported.
- Mixed-content loading blocked by default.
- Popups disabled unless explicitly handled.
- Unexpected permission requests denied.
- Camera/microphone access denied until user approval.
- Geolocation denied until explicit user approval.
- External URL schemes validated before dispatch.
- Dangerous SSL certificate errors must not be silently bypassed.

Store site permissions locally and allow revocation.

Do not disable Safe Browsing merely to claim that the app has no contact with third-party services. Document any platform-managed security checks and give users an informed choice where technically supported.

## 8.3 Incognito

Private browsing must not write entries to normal history, bookmarks, restored tabs or ordinary app diagnostics.

Prefer isolated WebView profiles through AndroidX WebKit feature detection.

Requirements:

- Check multi-profile support at runtime.
- Separate cookies, website storage and cache from normal mode.
- Destroy all private WebViews before deleting their profile.
- Ensure private storage cleanup on normal closure.
- Perform stale private-profile cleanup at next startup after unexpected termination.
- Never delete normal-mode cookies when closing private mode.

AndroidX profile deletion can be asynchronous and has lifecycle constraints. Handle deletion failures and document any remaining limitations.

If reliable isolation is unsupported, **disable private mode with an explanation rather than displaying a misleading incognito guarantee**.

Incognito is not anonymity and cannot prevent websites, employers, ISPs or network intermediaries from observing network activity.

## 8.4 Clear on Exit

Default: ON for cookies and browsing cache in normal mode.

Allow independent configuration for:

- Cookies.
- Cache.
- Browsing history.
- Stored site data.
- Search history.
- Open tab session.

Bookmarks must not be deleted unless explicitly selected.

Define exit as an explicit Close Iris action, not merely switching apps or losing foreground focus.

Do not promise guaranteed cleanup after force-stop or system termination; perform pending cleanup on the next launch where feasible.

## 8.5 HTTPS-First

- Attempt HTTPS for ordinary navigations.
- Permit explicit HTTP exceptions.
- Clearly distinguish HTTP from HTTPS.
- Do not silently suppress certificate warnings.
- Support HTTP to user-approved local SearXNG endpoints.
- Avoid globally permitting cleartext traffic when a narrower configuration is feasible.

Because Android network-security configuration has host-scoping limitations, verify how custom IP addresses and user-added local HTTP endpoints are supported.

Document any required exception or compromise instead of silently opening cleartext access to arbitrary destinations.

---

# 9. Tracker and Advertisement Blocking

Implement a local request-filtering module inspired by privacy browsers.

Scope for P1:

- Domain-based blocklists.
- First-party/third-party request classification.
- Per-site blocking toggle.
- Local rule storage.
- User-visible blocked-request count.
- Manual list updates.
- Optional periodic list updates, disabled by default.

Use an existing permissively licensed filter-list source with correct attribution and licensing.

Do not bundle a full unsupported adblock rule interpreter while claiming complete compatibility.

WebView interception has limitations involving redirects, service workers, resource types and browser-internal requests.

Document actual coverage.

Do not block essential first-party functionality indiscriminately.

Allow per-site disablement where blocking causes website breakage.

---

# 10. Reader Mode

Provide a distraction-free reading view for supported articles.

Requirements:

- Extract article title and body.
- Remove irrelevant navigation elements.
- Adjustable font size.
- Light/dark reading background.
- Return to original webpage.
- Optional save for offline reading.

Use an established readability-style extraction approach or a carefully scoped HTML parser.

Sanitize extracted HTML before rendering.

Do not execute untrusted page scripts inside the reader view.

Reader mode must fail gracefully when a page cannot be extracted.

---

# 11. Offline Capabilities

Offline support is separated into three features.

## 11.1 Local Browser Data

Available without internet:

- Bookmarks.
- Browsing history.
- Browser settings.
- Saved session metadata.

## 11.2 Offline Search

Implement a local index covering:

- Bookmark titles and URLs.
- Browsing history titles and URLs.
- Extracted text from explicitly saved offline pages.

Use SQLite FTS5 when supported, with a documented fallback where required.

Never automatically download and archive every visited webpage.

## 11.3 Saved Pages

Allow the user to save supported pages for offline reading.

Requirements:

- Manual Save Offline action.
- Local metadata database.
- Application-private file storage.
- Storage usage display.
- Delete individual saved pages.
- Clear all saved pages.
- Search extracted page text.
- Strictly sanitize locally displayed content.

Text-first saved articles are sufficient for P1.

Do not promise perfect offline preservation of interactive websites, authentication, embedded video, complex scripts or externally hosted assets.

---

# 12. Search Shortcuts

Support built-in shortcuts:

```text
!yt    YouTube
!gh    GitHub
!wiki  Wikipedia
!ddg   DuckDuckGo
!sx    SearXNG
```

Shortcuts explicitly open the corresponding search service and may transmit the query to that service.

User-defined shortcuts are optional.

Keep the parsing implementation independent from the active search provider.

---

# 13. Customization

P1 controls:

- Top/bottom address bar.
- Light/dark/system theme.
- Dynamic color toggle.
- Search engine preference.
- SearXNG endpoint list.
- Privacy controls.
- JavaScript session toggle.
- Tab suspension setting.
- Clear-on-exit settings.
- Text scaling.

Preferences must persist locally.

Avoid deeply customizable UI frameworks in v1.

---

# 14. Local AI (P2, Optional)

Local AI is an enhancement, never a browser dependency.

Potential capabilities:

- Summarize current article.
- Answer questions from extracted page text.
- Find related saved pages.
- Search bookmarks semantically.

Requirements:

- Explicit user opt-in.
- No paid model API.
- No automatic upload of page contents.
- No automatic download of model weights.
- Model size and resource usage disclosed.
- Can operate through an on-device inference runtime if feasible.
- Optional user-configured local inference endpoint, such as a laptop running llama.cpp.
- Completely removable/disabled without affecting browsing.

Do not install heavy AI dependencies into the P0 browser.

Do not claim support for on-device models until memory, storage, battery and performance have been tested on real hardware.

---

# 15. Performance Requirements

Treat these as engineering targets, not guaranteed measurements.

| Metric | Target |
|---|---|
| Cold launch to interactive UI | Under 1.5 seconds on target device |
| Warm launch | Under 600 ms |
| Local tab switching | Under 150 ms when WebView remains active |
| SearXNG reachability deadline | 2 seconds |
| SearXNG query deadline | 4 seconds |
| DuckDuckGo query deadline | 5 seconds |
| Idle CPU | Near zero |
| Idle background polling | None |
| Active WebViews | Default maximum 3 |
| Background networking | Only explicit or opted-in work |

Measure on real hardware and emulator.

Separate application overhead from WebView renderer memory.

Optimize for smoothness, predictable resource usage, and reliable cleanup rather than arbitrary APK-size goals.

No polling loops when the app is idle.

No continuously running local AI model in the main browser process.

---

# 16. Local Data Model

Use Room entities for:

**Bookmark**
- ID
- URL
- title
- created time
- updated time
- optional folder

**HistoryEntry**
- ID
- URL
- title
- last visited time
- visit count

**TabSession**
- tab ID
- URL
- title
- position
- selected state
- session metadata

**OfflinePage**
- ID
- original URL
- title
- saved time
- local content path
- indexed extracted text
- size

**SitePermission**
- domain/origin
- permission type
- permission state
- update time

Use DataStore for app preferences, including provider URLs and fallback settings.

Do not store private browsing entries in these normal-mode repositories.

Do not persist raw page content in ordinary history unless the user explicitly saves the page.

---

# 17. Network and Failure Handling

The browser must distinguish:

- Device has no network.
- Network connected but no internet.
- DNS failure.
- TLS/certificate error.
- Search provider timeout.
- Search provider malformed response.
- Search provider rate limit.
- Website navigation error.
- Local SearXNG reachable but failing upstream.
- Saved content available offline.

Use clear, short user-facing errors and retry actions.

Provider degradation must not crash the browser.

Logging:

- Debug build: limited local diagnostic events.
- Release build: no raw query logging.
- Never log authentication tokens, cookies, full browsing sessions or sensitive page contents.
- Logs must be bounded and clearable.

---

# 18. Workstation and SearXNG Integration

Existing development environment:

```text
OS: Linux
Android SDK: ~/Android/Sdk
Java: OpenJDK 17
SearXNG: 127.0.0.1:8888
LAN: 192.168.31.65
Tailscale: 100.110.99.98
```

Native SearXNG configuration:

```text
/home/elyssia/opt/searxng/searxng/searx/settings.yml
```

Implementation procedure:

1. Inspect current SearXNG service configuration.
2. Back up the original configuration.
3. Confirm whether rebinding is required.
4. Restrict access to trusted interfaces or source addresses.
5. Configure appropriate firewall controls.
6. Restart the existing service safely.
7. Test local connectivity.
8. Test LAN connectivity.
9. Test Tailscale connectivity.
10. Verify actual JSON search, not only an HTTP status code.

Binding to `0.0.0.0` exposes the service on all IPv4 interfaces. Do not treat it as automatically restricted to LAN and Tailscale.

Never expose the instance publicly without deliberate security configuration.

Keep laptop-side configuration changes separate from Android application commits unless scripts or documentation are explicitly required.

---

# 19. Tests

## 19.1 Unit Tests

Test:

- Search chain order.
- Timeout fallback.
- Search cancellation.
- SearXNG JSON parsing.
- DuckDuckGo Lite HTML parsing.
- Empty results versus provider failures.
- URL/search detection.
- Invalid URL rejection.
- Search shortcuts.
- History persistence.
- Bookmark behavior.
- Tab restoration metadata.
- Filter-list matching.
- Settings persistence.

Include representative fixtures and edge cases.

## 19.2 Instrumentation Tests

Test:

- App launch.
- First-run onboarding.
- Search screen navigation.
- Opening pages.
- Multiple tabs.
- Tab close/reopen.
- Normal history persistence.
- Private history exclusion.
- Private-profile availability handling.
- Permission denial.
- Cookie isolation where supported.
- Offline saved-page reading.
- Back navigation behavior.

## 19.3 Integration Tests

Use MockWebServer for repeatable search failure scenarios.

Additionally test the real local SearXNG instance through:

```bash
adb reverse tcp:8888 tcp:8888
```

Test DDG fallback using a controlled mock response; do not make public search availability a requirement for deterministic automated tests.

## 19.4 Manual Test Matrix

| Scenario | Expected behavior |
|---|---|
| Laptop online through Tailscale | SearXNG |
| Laptop online on LAN | SearXNG |
| Laptop offline, phone internet online | DuckDuckGo |
| Laptop online, SearXNG down | DuckDuckGo |
| All online providers unavailable | Local matches or error |
| Phone fully offline | Local browser features available |
| Private tabs closed | Private storage cleanup |
| App killed during private session | Startup recovery cleanup |
| Low-memory event | App survives or restores safely |
| Invalid URL | Safe rejection |
| HTTP website | Explicit insecure connection handling |
| Tracker-blocked site breaks | Per-site disable option |
| Device rotation | Usable restored interface |

---

# 20. Repository Structure

Preferred initial layout:

```text
Iris/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/.../iris/
│       │   │   ├── browser/
│       │   │   ├── search/
│       │   │   ├── privacy/
│       │   │   ├── data/
│       │   │   ├── offline/
│       │   │   ├── settings/
│       │   │   ├── ui/
│       │   │   └── MainActivity.kt
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       ├── test/
│       └── androidTest/
├── gradle/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PRIVACY.md
│   ├── TESTING.md
│   └── LIMITATIONS.md
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
└── gradlew
```

A single app module is acceptable initially.

Do not split into numerous Gradle modules without a measured need.

---

# 21. Agent Execution Instructions

The implementing agent must follow this order.

### Phase 1 — Environment Validation

- Inspect repository and working tree.
- Identify existing source files.
- Confirm remote configuration.
- Verify Android SDK and Java tools.
- Confirm available WebView and AndroidX APIs.
- Resolve dependency versions.
- Create a concrete implementation checklist.

### Phase 2 — Core Browser

Implement P0 UI, WebView navigation, tabs, persistence, and app configuration.

Build and test before proceeding.

### Phase 3 — Search Integration

Implement SearchProvider, SearchRouter, SearXNG, DuckDuckGo Lite and fallback behavior.

Add unit and integration tests.

### Phase 4 — Privacy Core

Implement WebView security, data-clearing controls, permission management, and supported private-profile isolation.

Do not fake unsupported privacy features.

### Phase 5 — Hybrid Features

Implement P1 features incrementally in independently testable units.

Prioritize request filtering, tab suspension, local search, reader mode and offline content.

### Phase 6 — Validation

- Compile debug APK.
- Execute unit tests.
- Execute available instrumented tests.
- Run static analysis.
- Validate networking and fallback cases.
- Inspect privacy-sensitive paths.
- Confirm actual WebView compatibility.
- Review crashes and memory leaks.

### Phase 7 — Release Preparation

Produce:

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
```

A release APK must be genuinely installable and signed. Do not claim an unsigned APK is ready for direct installation.

Use secure signing practices. Never commit private signing keys or passwords.

### Phase 8 — Documentation and Git

Update documentation for every implemented capability.

Use meaningful commits.

Push to `origin` only after successful checks and when repository access is available and authorized.

Never overwrite existing remote history with force-push.

---

# 22. Definition of Done

P0 is complete only when:

- App launches successfully.
- Normal webpage navigation works.
- Search queries return real normalized results.
- SearXNG works on at least one tested endpoint.
- DuckDuckGo fallback works when SearXNG fails.
- Results visibly identify their source.
- Tabs are functional.
- Bookmarks persist.
- Normal browsing history persists according to settings.
- App survives common lifecycle events.
- Offline mode displays a useful fallback state.
- Security baseline is implemented.
- Unit tests pass.
- A debug APK is produced.
- Known limitations are documented.

P1 is complete only when its individual features work and corresponding tests pass.

**Never represent planned, stubbed, mocked or untested functionality as complete.**

---

# 23. Non-Goals and Hard Constraints

Do not implement these without a separately approved scope change:

- Custom browser rendering engine.
- Full Chromium or Gecko fork.
- Remote user-account synchronization.
- Cloud-based AI assistant.
- Mandatory laptop connectivity.
- Mandatory Tailscale.
- Paid search API.
- Built-in VPN or Tor.
- Extension marketplace.
- Cryptocurrency or wallet integration.
- Aggressive background crawling.
- Browser-wide unrestricted JavaScript injection.
- Fake privacy guarantees.

Avoid introducing unnecessary complexity merely to match feature counts of established browsers.

---

# 24. Final Implementation Mandate

Build Iris as a real, usable Android browser, not a visual prototype.

**Priority order:**

1. Correctness.
2. Privacy and security.
3. Reliability.
4. Performance.
5. Usability.
6. Optional capabilities.
7. Cosmetic polish.

The agent must implement functioning features, test them, document platform limitations, and report honestly on what was verified.

Where Android WebView cannot safely support a requested feature, implement the nearest secure alternative or clearly mark the feature unsupported.

Do not silently replace real functionality with mock UI.

Do not claim a build, test, hardware check, APK installation or Git push succeeded without verifying it.

**Final expected outcome:** a lean, modern Android browser with independent web browsing, hybrid private search, useful local/offline functionality, practical privacy controls, modular architecture and no required cloud infrastructure.
