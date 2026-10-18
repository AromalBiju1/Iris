# Iris — lean privacy browser (Android)

Search front door with a privacy fallback chain:

1. your self-hosted SearXNG (probes Tailscale -> LAN -> loopback, 2s health check),
2. DuckDuckGo Lite (always available, works when your laptop is off).

Privacy: no accounts, no telemetry, no analytics; third-party cookies blocked;
cookies/cache cleared on exit; DNT header sent on every page load; incognito
wipes everything on close.

## Build

- JDK 17 + Android SDK (see `gradle.properties` `sdk.dir`).
- `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- `./gradlew assembleRelease` -> installable debug-signed `app-release.apk`.

## Install on a device

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

(USB debugging required; a phone connected via USB is what v1 was tested on.)

## Point it at your SearXNG

Defaults live in `search/SearchSettings.kt`, probe order:
Tailscale `100.110.99.98:8888` -> LAN `192.168.31.65:8888` -> loopback
`127.0.0.1:8888` (USB tunnel). Host-side setup + revert:
`docs/searxng-host-setup.md`.

## How the search chain works

`search/SearchRouter.kt`:

- probes each provider (2s cap) and skips unreachable ones instantly,
- degrades a provider for the rest of the session on hard failure
  (e.g. markup breakage),
- throws `NoResultsException` only if everything fails (UI shows Retry).

The UI always shows which engine answered — "via SearXNG" / "via DuckDuckGo".

## Tests

- Unit (JVM + Robolectric): parsers (fixtures from live SearXNG + achived DDG
  Lite page), router fallback/degradation, URL detection, local store.
- Instrumented (device via adb): boot test + real self-hosted SearXNG
  end-to-end through `adb reverse tcp:8888 tcp:8888`.

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```
