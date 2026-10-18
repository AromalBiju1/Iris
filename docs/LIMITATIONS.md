# Iris limitations & privacy notes (honest state)

## Private tab isolation (§7/§8.3)
- Implemented with androidx.webkit MULTI_PROFILE (`ProfileStore`, profile `iris-private`).
- If the installed WebView engine does not support `WebViewFeature.MULTI_PROFILE`,
  private tabs are **not offered** (no fake incognito). Verified: `WebViewCompat.setProfile`.
- When the last private tab closes, the `iris-private` profile is deleted
  (cookies/cache of that session are gone).

## Tab suspension (§7)
- Max 3 live WebViews. Suspending a tab destroys its WebView; on return only
  URL + title are restored. Form input, JS state, and scroll position are lost.
  This is documented UX, not a bug.

## Cleartext policy (§8.5)
- Base config: HTTPS only. Cleartext allowed ONLY for the user-approved SearXNG
  endpoints listed in `res/xml/network_security_config.xml`
  (Tailscale/100.110.99.98, LAN/192.168.31.65, loopback).
- Adding a new http endpoint: edit that file (root-owned service IPs are static)
  or use an https reverse proxy. There is no in-app toggle to weaken this.

## Search empty vs outage (§5.2)
- A provider that answers with zero results yields "No results found".
- A provider that errors/timeouts fails over to the next one; if all enabled
  providers fail, the message lists which provider did what.
- Known edge: if DuckDuckGo Lite serves a bot-challenge page, its parser sees
  zero results, so it is reported as "empty" instead of "failed".

## Exit & clear data
- Clears cookies (global CookieManager — normal profile), cache, history,
  tab session, private profile. Bookmarks are kept by design.
