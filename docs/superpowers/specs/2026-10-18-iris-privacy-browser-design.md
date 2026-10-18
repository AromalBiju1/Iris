# Iris — Lean Privacy Browser for Android: Design

Date: 2026-10-18
Status: Approved design, pending implementation plan
Repo: `git@github.com:AromalBiju1/Iris.git` (SSH remote `origin`)

## 1. Purpose

A lean, privacy-first Android web browser where search is the front door.
Search runs through a priority fallback chain so it works **anywhere**,
including when the user's laptop (which hosts a private SearXNG instance) is
offline:

1. **Self-hosted SearXNG** (primary, when reachable) — user owns it, best privacy.
2. **DuckDuckGo Lite** (fallback, always available) — web-only backend, no
   dependency on any user infrastructure.

No accounts, no telemetry, no analytics, no crash reporting. Nothing leaves
the device except page loads and search queries.

## 2. Environment context (this workstation)

- SearXNG: native install, runs at `127.0.0.1:8888`, JSON API enabled
  (`/search?q=&format=json` verified 200 OK). Currently bound to
  `127.0.0.1` only — one-line config change needed to expose it.
- Host interfaces: LAN `192.168.31.65`, Tailscale `100.110.99.98`.
- Android SDK at `~/Android/Sdk` (build-tools, cmake, cmdline-tools,
  emulator), `adb` and OpenJDK 17 present. Gradle via wrapper.

### SearXNG exposure task (laptop side, part of implementation)

Rebind SearXNG to listen on LAN + Tailscale interfaces (not just loopback):

- Edit `/home/elyssia/opt/searxng/searxng/searx/settings.yml`
  `server.bind_address` → `"0.0.0.0"` (LAN is trusted home Wi-Fi; Tailscale
  is encrypted + authenticated).
- Restart the SearXNG service.
- The app will probe multiple candidate URLs in order; exact values ship as
  user-editable defaults in settings.

## 3. Architecture

```
┌─ BrowserActivity (Compose UI, tab strip)
│    └─ WebView per tab (cached, navigable)
├─ SearchActivity (results list)
│    └─ SearchRouter → providers in priority order:
│         1. Self-hosted SearXNG (JSON API; URL list; 2s health probe)
│            candidate URLs: http://100.110.99.98:8888  (Tailscale)
│                           http://192.168.31.65:8888  (LAN)
│                           http://127.0.0.1:8888     (USB/adb reverse)
│         2. DuckDuckGo Lite (https://lite.duckduckgo.com/lite/?q= scraped)
└─ LocalStore (SQLite: bookmarks, history, settings — local only)
```

### Components

| Unit | Does | Depends on |
|---|---|---|
| `SearchRouter` | Runs probe + chain, returns normalized results | providers |
| `SearXngProvider` | Queries SearXNG JSON API, parses | OkHttp |
| `DdgLiteProvider` | Fetches + scrapes DDG Lite HTML | OkHttp |
| `ResultNormalizer` | Maps both schemas → `SearchResult(url, title, snippet)` | — |
| `TabManager` | Owns cached WebViews per tab | WebView |
| `LocalStore` | Local persistence | SQLite |
| `HealthProbe` | 2s reachability check per candidate URL | OkHttp |

Search results are normalized to one model:

```
SearchResult(url: String, title: String, snippet: String, engine: Engine)
```

The UI never knows which engine answered except through a small "via
DuckDuckGo" / "via SearXNG" chip next to results (transparency requirement).

## 4. Browser behavior & privacy hardening

- **Tabs**: simple Compose tab strip; cached WebView per tab; restore tabs on
  relaunch (normal mode only).
- **URL bar**: input detected as URL (scheme or dots) → navigate; else →
  search via SearchRouter.
- **Incognito mode** (single toggle): no history or cookie persistence, cookies +
  cache wiped on close, tabs not restored.
- **Clear on exit**: ON by default for normal mode (cookie/cache wipe).
- Third-party cookies blocked. JavaScript allowed by default (site
  compatibility), per-session quick toggle. Do-Not-Track header sent.
- WebView secure defaults: content access denied for file scheme, no
  universal-from-file, no geolocation prompts granted silently.

## 5. Error handling

- **SearXNG unreachable** → instant silent failover to DDG; "via DuckDuckGo"
  chip makes it visibly transparent.
- **No connectivity at all** → No-connection screen with retry.
- **Provider parse failure** (e.g. DDG changes Lite markup) → provider marked
  degraded for this session, chain falls through; failure logged to a local
  debug file. App keeps working, no crash.
- **Timeouts**: health probe 2s; SearXNG query 4s; DDG 5s.

## 6. Testing

- **Unit (JVM)**: SearchRouter chain order + fallback; URL-vs-search text
  detection; DDG Lite parser against checked-in HTML fixture;
  SearXNG JSON parser against checked-in fixture from the live instance.
- **Instrumented (emulator)**: app boot; search via `adb reverse tcp:8888`
  against the real local SearXNG (full end-to-end); DDG fallback with mocked
  connectivity.
- **Manual**: APK built here, installed on device/emulator, verified over
  Tailscale + LAN + airplane-mode fallback scenarios.

## 7. Deliverables

- Full Gradle project in repo root (`~/Desktop/Iris/`), pushed to `origin`.
- Debug + release APK under `app/build/outputs/` (direct install via
  `adb install` or file copy).
- README: architecture, search chain, settings (SearXNG URL list), build +
  install instructions.

## 8. Non-goals (v1)

- Downloads manager, filter-list adblock, Tor/VPN integration, dark reader,
  sync, extensions, public-SearXNG fallback lists (self-host + DDG only).
