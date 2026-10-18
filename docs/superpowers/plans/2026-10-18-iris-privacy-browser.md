# Iris — Lean Privacy Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lean, privacy-first Android browser ("Iris") whose search front door runs through a reachability-probed fallback chain: self-hosted SearXNG first, DuckDuckGo Lite always-on fallback.

**Architecture:** Kotlin + Jetpack Compose UI, system WebView for page rendering, OkHttp + kotlinx-serialization + Jsoup for provider fetch/parse, plain SQLite local storage. One `SearchRouter` normalizes provider output into `SearchResult`, marks hard-failed providers degraded for the session, and falls through the chain.

**Tech Stack:** Kotlin 2.2.20, AGP 8.13.0, Gradle 8.14.3 (wrapper), Compose BOM 2025.01.00, OkHttp 4.12.0, Jsoup 1.18.3, kotlinx-serialization-json 1.8.0, JUnit 4.13.2, Robolectric 4.14.1, Espresso + compose-ui-test (instrumented).

## Global Constraints

- Package: `dev.elyssia.iris` (verbatim; all paths use it).
- `minSdk 26`, `targetSdk 36`, `compileSdk 36` (SDK has platform 37 locally; 36 is max stable supported by pinned AGP 8.13.0 — do not raise in v1). JVM target 17 (host JDK 17.0.20.1).
- Search chain order fixed: SearXNG (self-hosted) → DuckDuckGo Lite. Timeouts: probe 2s, SearXNG query 4s, DDG 5s (verbatim in code).
- Permissions: INTERNET only. No accounts, telemetry, analytics, crash reporting — ever.
- Results transparency: UI shows a "via SearXNG" / "via DuckDuckGo" chip whenever results display.
- DNT: every WebView page load sends header `DNT: 1`.
- SearXNG candidate URLs, probe order (settings-persistable defaults): `http://100.110.99.98:8888` (Tailscale) → `http://192.168.31.65:8888` (LAN) → `http://127.0.0.1:8888` (USB/adb reverse/emulator).
- Router degradation semantics: probe failure is transient (do NOT mark degraded); thrown search failure marks the provider degraded for the remaining session; empty results falls through without degrading.
- Commit style: Conventional Commits (`feat:`, `test:`, `chore:`, `docs:`). Commit after every green test cycle.
- Android SDK at `~/Android/Sdk`; emulator image present: `system-images;android-34;google_apis;x86_64`.
- Chat logging: after finishing each task, append `ISO-timestamp: task-N done: <summary>` to `~/Desktop/Iris/chat.log` (gitignored).
- Repo root `~/Desktop/Iris/` — already git-init-ed, spec committed, `origin` = `git@github.com:AromalBiju1/Iris.git` (SSH).

## File Structure (final state)

```
Iris/
├── .gitignore
├── gradle.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/build.gradle.kts
├── app/src/main/AndroidManifest.xml
├── app/src/main/java/dev/elyssia/iris/
│   ├── MainActivity.kt              # hosts Search <-> Browser switching
│   ├── search/
│   │   ├── SearchResult.kt          # model + Engine enum + outcome/error types
│   │   ├── SearxngApi.kt            # @Serializable wire models
│   │   ├── SearchProvider.kt        # provider interface
│   │   ├── SearXngProvider.kt       # probe + JSON search (multi-URL failover)
│   │   ├── DdgLiteParser.kt         # pure Jsoup parser
│   │   ├── DdgLiteProvider.kt       # fetch + delegate parse
│   │   ├── SearchRouter.kt          # fallback chain + session degradation
│   │   ├── UrlDetector.kt           # URL vs search text
│   │   ├── SearchSettings.kt        # candidate URL list via LocalStore
│   │   └── SearchViewModel.kt       # ui state machine
│   ├── browser/
│   │   ├── TabState.kt
│   │   ├── TabManager.kt            # tab registry (ids, active, retitle)
│   │   ├── PrivacyWebView.kt        # hardening + incognito config + DNT
│   │   └── BrowserScreen.kt         # tabs + URL bar UI
│   ├── storage/
│   │   └── LocalStore.kt            # SQLite; history/bookmarks/settings/wipe
│   └── ui/
│       └── SearchScreen.kt          # search input + results + engine chip
├── app/src/test/java/dev/elyssia/iris/…   # unit tests (same packages)
├── app/src/test/resources/fixtures/       # searxng_test.json, ddg_lite.html
├── app/src/androidTest/java/dev/elyssia/iris/…  # boot + real-SearXNG E2E
└── docs/searxng-host-setup.md
```

Responsibility boundaries: providers know fetch+parse; router knows order/fallback/degradation; UI never touches HTTP; storage never touches network.

---

### Task 1: Toolchain + project scaffold

**Files:**
- Create: `.gitignore`, `gradle.properties`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/dev/elyssia/iris/MainActivity.kt`

**Interfaces:**
- Produces: buildable `app` module (`dev.elyssia.iris`), gradle wrapper, all test infra levels prewired.

- [ ] **Step 1: Bootstrap Gradle (no global gradle)**

```bash
mkdir -p ~/opt && cd ~/opt
curl -sSLO https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
unzip -oq gradle-8.14.3-bin.zip
export PATH=~/opt/gradle-8.14.3/bin:$PATH
cd ~/Desktop/Iris && gradle wrapper --gradle-version 8.14.3
```

Expected: `gradlew` + `gradle/wrapper/` in repo root.

- [ ] **Step 2: Write `gradle/libs.versions.toml` (exact file)**

```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.20"
composeBom = "2025.01.00"
activityCompose = "1.10.1"
lifecycle = "2.8.7"
coreKtx = "1.15.0"
core = "1.6.2"
junitExt = "1.2.1"
espresso = "3.6.1"
junit = "4.13.2"
robolectric = "4.14.1"
okhttp = "4.12.0"
jsoup = "1.18.3"
serialization = "1.8.0"
coroutines = "1.10.1"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
test-core = { group = "androidx.test", name = "core", version.ref = "core" }
test-junit-ext = { group = "androidx.test.ext", name = "junit", version.ref = "junitExt" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 3: Write root `settings.gradle.kts` + `build.gradle.kts` (exact files)**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Iris"
include(":app")
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 4: Write `app/build.gradle.kts` (exact file)**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.elyssia.iris"
    compileSdk = 36
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    defaultConfig {
        applicationId = "dev.elyssia.iris"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)

    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
```

- [ ] **Step 5: Write `.gitignore` + `gradle.properties` (exact files)**

```
# .gitignore
.gradle/
build/
local.properties
.idea/
*.iml
chat.log
smoke.png
```

```properties
# gradle.properties — host SDK path (personal repo convenience)
sdk.dir=/home/elyssia/Android/Sdk
org.gradle.jvmargs=-Xmx2g
```

- [ ] **Step 6: Write `app/src/main/AndroidManifest.xml` (exact file)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:label="Iris"
        android:icon="@android:drawable/ic_menu_manage"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 7: Write `MainActivity.kt` placeholder (exact file; replaced in Task 7)**

```kotlin
package dev.elyssia.iris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Iris") }
    }
}
```

- [ ] **Step 8: Build**

Run: `cd ~/Desktop/Iris && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`.
(If AGP 8.13.0 is not resolvable, fall back to the closest released 8.1x and record the pinned swap in chat.log; re-verify `assembleDebug`.)

- [ ] **Step 9: Commit + log**

```bash
git add -A && git commit -m "chore: scaffold Gradle project (Kotlin 2.x, AGP 8.13, Compose)"
echo "$(date -Iseconds) task-1 done: scaffold builds" >> ~/Desktop/Iris/chat.log
```

---

### Task 2: Fixtures + SearchResult model + SearXNG parser

**Files:**
- Create: `app/src/test/resources/fixtures/searxng_test.json`
- Create: `app/src/main/java/dev/elyssia/iris/search/SearchResult.kt`
- Create: `app/src/main/java/dev/elyssia/iris/search/SearxngApi.kt`
- Test: `app/src/test/java/dev/elyssia/iris/search/SearXngParserTest.kt`

**Interfaces:**
- Produces (used by all later tasks):
  - `enum class Engine { SEARXNG, DUCKDUCKGO }`
  - `data class SearchResult(url: String, title: String, snippet: String, engine: Engine)`
  - `data class SearchOutcome(engine: Engine, results: List<SearchResult>)`
  - `class ProviderFailure(val providerEngine: Engine, cause: Throwable) : Exception`
  - `class NoResultsException(val errors: List<Throwable>) : Exception`
  - `@Serializable data class SearxngResponse(results: List<SearxngResult>)`, `@Serializable data class SearxngResult(url, title, content, engine, img_src)` with `fun toSearchResult(): SearchResult`.

- [ ] **Step 1: Capture the fixture from the live local instance**

```bash
mkdir -p ~/Desktop/Iris/app/src/test/resources/fixtures
curl -s "http://127.0.0.1:8888/search?q=test&format=json" \
  > ~/Desktop/Iris/app/src/test/resources/fixtures/searxng_test.json
head -c 200 ~/Desktop/Iris/app/src/test/resources/fixtures/searxng_test.json
```

Expected: file starts with `{` and contains `"results":[…]`. Keep the file as captured (no trimming needed).

- [ ] **Step 2: Write the failing test (exact file)**

```kotlin
package dev.elyssia.iris.search

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearXngParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesFixtureIntoSearchResults() {
        val body = javaClass.getResourceAsStream("/fixtures/searxng_test.json")!!
            .bufferedReader().readText()
        val response = json.decodeFromString<SearxngResponse>(body)
        assertTrue(response.results.isNotEmpty())
        val first = response.results.first()
        assertTrue(first.url.startsWith("http"))
        assertEquals(Engine.SEARXNG, first.toSearchResult().engine)
    }
}
```

- [ ] **Step 3: Run — expect compile failure (types missing)**

Run: `cd ~/Desktop/Iris && ./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.search.SearXngParserTest"`
Expected: FAIL — `SearxngResponse`/`Engine` unresolved.

- [ ] **Step 4: Write models (exact files)**

```kotlin
// search/SearchResult.kt
package dev.elyssia.iris.search

enum class Engine { SEARXNG, DUCKDUCKGO }

data class SearchResult(
    val url: String,
    val title: String,
    val snippet: String,
    val engine: Engine,
)

data class SearchOutcome(val engine: Engine, val results: List<SearchResult>)

class ProviderFailure(
    val providerEngine: Engine,
    cause: Throwable,
) : Exception("provider ${providerEngine.name} failed: ${cause.message}", cause)

class NoResultsException(
    val errors: List<Throwable>,
) : Exception("all providers failed (${errors.size} errors)")
```

```kotlin
// search/SearxngApi.kt
package dev.elyssia.iris.search

import kotlinx.serialization.Serializable

@Serializable
data class SearxngResponse(val results: List<SearxngResult> = emptyList())

@Serializable
data class SearxngResult(
    val url: String,
    val title: String = "",
    val content: String = "",
    val engine: String = "",
    val img_src: String? = null,
) {
    fun toSearchResult() = SearchResult(
        url = url,
        title = title.ifBlank { url },
        snippet = content,
        engine = Engine.SEARXNG,
    )
}
```

- [ ] **Step 5: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.search.SearXngParserTest"`
Expected: PASS.

- [ ] **Step 6: Commit + log**

```bash
git add -A && git commit -m "feat: search result model + SearXNG JSON parser with live fixture"
echo "$(date -Iseconds) task-2 done: searxng parser" >> ~/Desktop/Iris/chat.log
```

---

### Task 3: DuckDuckGo Lite parser (Jsoup, fixture-tested)

**Files:**
- Create: `app/src/main/java/dev/elyssia/iris/search/DdgLiteParser.kt`
- Create: `app/src/test/resources/fixtures/ddg_lite.html`
- Test: `app/src/test/java/dev/elyssia/iris/search/DdgLiteParserTest.kt`

**Interfaces:**
- Produces: `object DdgLiteParser { fun parse(html: String, limit: Int = 30): List<SearchResult> }`.
- Consumes: `SearchResult`, `Engine` (Task 2). (Provider class comes in Task 4 — do NOT create one here; module must compile standalone at the end of this task.)

- [ ] **Step 1: Capture the HTML fixture from the live web**

```bash
curl -s -A "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0" \
  "https://lite.duckduckgo.com/lite/?q=test" \
  > ~/Desktop/Iris/app/src/test/resources/fixtures/ddg_lite.html
grep -c "result-link" ~/Desktop/Iris/app/src/test/resources/fixtures/ddg_lite.html
```

Expected: count >= 1. If the live markup differs (no `result-link`), adapt `DdgLiteParser.select` to whatever anchor/snippet classes the live page uses, and encode that in the fixture — the parser must match the live page, the test asserts correctness against the fixture.

- [ ] **Step 2: Write the failing parser test (exact file)**

```kotlin
package dev.elyssia.iris.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdgLiteParserTest {
    private fun fixture(): String =
        javaClass.getResourceAsStream("/fixtures/ddg_lite.html")!!
            .bufferedReader().readText()

    @Test
    fun parsesFixture() {
        val results = DdgLiteParser.parse(fixture(), limit = 30)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.url.startsWith("http") && it.engine == Engine.DUCKDUCKGO })
        assertTrue(results.all { it.title.isNotBlank() })
    }

    @Test
    fun respectsLimit() {
        val results = DdgLiteParser.parse(fixture(), limit = 2)
        assertEquals(2, results.size)
    }
}
```

- [ ] **Step 3: Run — expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.search.DdgLiteParserTest"`
Expected: FAIL — `DdgLiteParser` unresolved.

- [ ] **Step 4: Write parser (exact file)**

```kotlin
// search/DdgLiteParser.kt
package dev.elyssia.iris.search

import org.jsoup.Jsoup

object DdgLiteParser {
    fun parse(html: String, limit: Int = 30): List<SearchResult> {
        val doc = Jsoup.parse(html)
        val anchors = doc.select("a.result-link")
        val snippets = doc.select("td.result-snippet")
        return anchors.indices.take(limit).mapNotNull { i ->
            val a = anchors[i]
            val url = a.absUrl("href")
            if (!url.startsWith("http")) return@mapNotNull null
            SearchResult(
                url = url,
                title = a.text().ifBlank { url },
                snippet = snippets.getOrNull(i)?.text().orEmpty(),
                engine = Engine.DUCKDUCKGO,
            )
        }
    }
}
```

- [ ] **Step 5: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.search.DdgLiteParserTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit + log**

```bash
git add -A && git commit -m "feat: DuckDuckGo Lite Jsoup parser with fixture"
echo "$(date -Iseconds) task-3 done: ddg parser" >> ~/Desktop/Iris/chat.log
```

---

### Task 4: SearchProvider interface + both providers + SearchRouter

**Files:**
- Create: `app/src/main/java/dev/elyssia/iris/search/SearchProvider.kt`
- Create: `app/src/main/java/dev/elyssia/iris/search/SearXngProvider.kt`
- Create: `app/src/main/java/dev/elyssia/iris/search/DdgLiteProvider.kt`
- Create: `app/src/main/java/dev/elyssia/iris/search/SearchRouter.kt`
- Test: `app/src/test/java/dev/elyssia/iris/search/SearchRouterTest.kt`

**Interfaces:**
- Produces:
  - `interface SearchProvider { val engine: Engine; suspend fun probe(timeoutMs: Long = 2_000L): Boolean; suspend fun search(query: String, limit: Int = 30): List<SearchResult> }`
  - `class SearXngProvider(client: OkHttpClient, urlProvider: () -> List<String>) : SearchProvider`
  - `class DdgLiteProvider(client: OkHttpClient) : SearchProvider`
  - `class SearchRouter(providers: List<SearchProvider>, probeTimeoutMs: Long = 2_000L)` with `var onError: ((Engine, Throwable) -> Unit)?` and `suspend fun search(query: String, limit: Int = 30): SearchOutcome`.
- Consumes: Task 2 types; Task 3 `DdgLiteParser`.

- [ ] **Step 1: Write the failing router test (exact file)**

```kotlin
package dev.elyssia.iris.search

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRouterTest {

    private fun provider(
        engine: Engine,
        probeResult: Boolean,
        results: List<SearchResult>,
        throwOnSearch: Boolean = false,
    ) = object : SearchProvider {
        override val engine = engine
        override suspend fun probe(timeoutMs: Long) = probeResult
        override suspend fun search(query: String, limit: Int): List<SearchResult> {
            if (throwOnSearch) throw ProviderFailure(engine, IllegalStateException("boom"))
            return results
        }
    }

    private fun result(engine: Engine) = SearchResult("https://x", "t", "s", engine)

    @Test
    fun primaryHealthyWins() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, true, listOf(result(Engine.SEARXNG))),
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        assertEquals(Engine.SEARXNG, router.search("q").engine)
    }

    @Test
    fun fallsBackWhenProbeFails() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, false, emptyList()),
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        assertEquals(Engine.DUCKDUCKGO, router.search("q").engine)
    }

    @Test
    fun fallsBackOnSearchFailure() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, true, emptyList(), throwOnSearch = true),
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        assertEquals(Engine.DUCKDUCKGO, router.search("q").engine)
    }

    @Test
    fun emptyResultsFallThrough() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, true, emptyList()),
            provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO))),
        ))
        assertEquals(Engine.DUCKDUCKGO, router.search("q").engine)
    }

    @Test
    fun allFailThrowsNoResults() = runTest {
        val router = SearchRouter(listOf(
            provider(Engine.SEARXNG, false, emptyList()),
            provider(Engine.DUCKDUCKGO, false, emptyList()),
        ))
        try {
            router.search("q")
            throw AssertionError("expected NoResultsException")
        } catch (e: NoResultsException) {
            assertEquals(2, e.errors.size)
        }
    }

    @Test
    fun searchFailureDegradesForSession() = runTest {
        var calls = 0
        val flaky = object : SearchProvider {
            override val engine = Engine.SEARXNG
            override suspend fun probe(timeoutMs: Long) = true
            override suspend fun search(query: String, limit: Int): List<SearchResult> {
                calls++
                throw ProviderFailure(engine, IllegalStateException("broken"))
            }
        }
        val router = SearchRouter(listOf(flaky, provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO)))))
        router.search("a")
        router.search("b")
        assertEquals(1, calls)
    }

    @Test
    fun probeFailureDoesNotDegrade() = runTest {
        var probes = 0
        val cold = object : SearchProvider {
            override val engine = Engine.SEARXNG
            override suspend fun probe(timeoutMs: Long): Boolean { probes++; return false }
            override suspend fun search(query: String, limit: Int): List<SearchResult> {
                throw ProviderFailure(engine, IllegalStateException("should not be reached"))
            }
        }
        val router = SearchRouter(listOf(cold, provider(Engine.DUCKDUCKGO, true, listOf(result(Engine.DUCKDUCKGO)))))
        router.search("a")
        router.search("b")
        assertEquals(2, probes)
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.search.SearchRouterTest"`
Expected: FAIL — `SearchProvider`/`SearchRouter` unresolved.

- [ ] **Step 3: Write interface + provider + router (exact files)**

```kotlin
// search/SearchProvider.kt
package dev.elyssia.iris.search

interface SearchProvider {
    val engine: Engine
    suspend fun probe(timeoutMs: Long = 2_000L): Boolean
    suspend fun search(query: String, limit: Int = 30): List<SearchResult>
}
```

```kotlin
// search/SearXngProvider.kt
package dev.elyssia.iris.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SearXngProvider(
    private val client: OkHttpClient,
    private val urlProvider: () -> List<String>,
) : SearchProvider {

    override val engine: Engine = Engine.SEARXNG

    override suspend fun probe(timeoutMs: Long): Boolean {
        val urls = urlProvider()
        if (urls.isEmpty()) return false
        val probeClient = client.newBuilder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        return withContext(Dispatchers.IO) {
            urls.any { url ->
                try {
                    probeClient.newCall(Request.Builder().url(url + PROBE).build())
                        .execute().use { it.code in 200..299 }
                } catch (_: Exception) { false }
            }
        }
    }

    override suspend fun search(query: String, limit: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val urls = urlProvider().ifEmpty { listOf(DEFAULT_URL) }
        val searchClient = client.newBuilder()
            .readTimeout(4_000L, TimeUnit.MILLISECONDS)
            .build()
        var lastFailure: ProviderFailure? = null
        for (url in urls) {
            try {
                val http = url + "/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json"
                searchClient.newCall(Request.Builder().url(http).build())
                    .execute().use { r ->
                        if (!r.isSuccessful) {
                            throw ProviderFailure(engine, IllegalStateException("HTTP ${r.code}"))
                        }
                        val body = r.body?.string()
                            ?: throw ProviderFailure(engine, IllegalStateException("empty body"))
                        json.decodeFromString<SearxngResponse>(body)
                            .results
                            .take(limit)
                            .map { it.toSearchResult() }
                    }
            } catch (e: Exception) {
                lastFailure = ProviderFailure(engine, e)
            }
        }
        throw lastFailure ?: ProviderFailure(engine, IllegalStateException("no urls attempted"))
    }

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8888"
        private const val PROBE = "/search?q=x&format=json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
```

```kotlin
// search/DdgLiteProvider.kt
package dev.elyssia.iris.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DdgLiteProvider(private val client: OkHttpClient) : SearchProvider {
    override val engine: Engine = Engine.DUCKDUCKGO

    override suspend fun probe(timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(BASE).header("User-Agent", UA).build())
                .execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    override suspend fun search(query: String, limit: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val searchClient = client.newBuilder()
            .readTimeout(5_000L, TimeUnit.MILLISECONDS)
            .build()
        try {
            val http = BASE + "?q=" + URLEncoder.encode(query, "UTF-8")
            searchClient.newCall(Request.Builder().url(http).header("User-Agent", UA).build())
                .execute().use { r ->
                    if (!r.isSuccessful) {
                        throw ProviderFailure(engine, IllegalStateException("HTTP ${r.code}"))
                    }
                    val body = r.body?.string()
                        ?: throw ProviderFailure(engine, IllegalStateException("empty body"))
                    DdgLiteParser.parse(body, limit)
                }
        } catch (e: Exception) {
            throw ProviderFailure(engine, e)
        }
    }

    companion object {
        const val BASE = "https://lite.duckduckgo.com/lite/"
        const val UA = "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0"
    }
}
```

```kotlin
// search/SearchRouter.kt
package dev.elyssia.iris.search

class SearchRouter(
    private val providers: List<SearchProvider>,
    private val probeTimeoutMs: Long = 2_000L,
) {
    private val degradedForSession = mutableSetOf<Engine>()
    var onError: ((Engine, Throwable) -> Unit)? = null

    suspend fun search(query: String, limit: Int = 30): SearchOutcome {
        val errors = mutableListOf<Throwable>()
        for (provider in providers) {
            if (provider.engine in degradedForSession) continue
            if (!provider.probe(probeTimeoutMs)) {
                errors.add(ProviderFailure(provider.engine, IllegalStateException("probe failed")))
                continue
            }
            try {
                val results = provider.search(query, limit)
                if (results.isNotEmpty()) {
                    return SearchOutcome(provider.engine, results)
                }
                errors.add(ProviderFailure(provider.engine, IllegalStateException("empty results")))
            } catch (t: Throwable) {
                degradedForSession.add(provider.engine)
                onError?.invoke(provider.engine, t)
                errors.add(ProviderFailure(provider.engine, t))
            }
        }
        throw NoResultsException(errors)
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.search.SearchRouterTest" --tests "dev.elyssia.iris.search.SearXngParserTest" --tests "dev.elyssia.iris.search.DdgLiteParserTest"`
Expected: all PASS (7 router + 1 + 2).

- [ ] **Step 5: Commit + log**

```bash
git add -A && git commit -m "feat: providers + SearchRouter fallback chain with session degradation"
echo "$(date -Iseconds) task-4 done: router" >> ~/Desktop/Iris/chat.log
```

---

### Task 5: URL-vs-search detector

**Files:**
- Create: `app/src/main/java/dev/elyssia/iris/search/UrlDetector.kt`
- Test: `app/src/test/java/dev/elyssia/iris/search/UrlDetectorTest.kt`

**Interfaces:**
- Produces: `object UrlDetector { fun detect(input: String): String? }` — navigable URL or `null` (= run through SearchRouter).
- Consumes: nothing.

- [ ] **Step 1: Write the failing test (exact file)**

```kotlin
package dev.elyssia.iris.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlDetectorTest {
    @Test
    fun detectsPlainUrl() = assertEquals("https://example.org", UrlDetector.detect("example.org"))

    @Test
    fun keepsScheme() = assertEquals("http://foo.bar/baz", UrlDetector.detect("http://foo.bar/baz"))

    @Test
    fun keepsHttps() = assertEquals("https://a.b/c?q=2", UrlDetector.detect("https://a.b/c?q=2"))

    @Test
    fun searchWordsAreNull() = assertNull(UrlDetector.detect("how to cook rice"))

    @Test
    fun blankIsNull() = assertNull(UrlDetector.detect("   "))

    @Test
    fun hostWithPortCountsAsUrl() = assertEquals("https://127.0.0.1:8888", UrlDetector.detect("127.0.0.1:8888"))
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.search.UrlDetectorTest"`
Expected: FAIL — `UrlDetector` unresolved.

- [ ] **Step 3: Write implementation (exact file)**

```kotlin
// search/UrlDetector.kt
package dev.elyssia.iris.search

object UrlDetector {
    private val WITH_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+$")
    private val DOTTED = Regex("^\\S+\\.\\S+$")

    fun detect(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (WITH_SCHEME.matches(trimmed)) return trimmed
        return if (DOTTED.matches(trimmed)) "https://" + trimmed.trimEnd('/') else null
    }
}
```

- [ ] **Step 4: Run — expect PASS (6/6; if `hostWithPortCountsAsUrl` fails, the fix is: allow any non-space containing a dot — adjust only within `DOTTED`/`WITH_SCHEME`, keep public behavior per tests)**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.search.UrlDetectorTest"`
Expected: PASS.

- [ ] **Step 5: Commit + log**

```bash
git add -A && git commit -m "feat: URL-vs-search-text detector"
echo "$(date -Iseconds) task-5 done: url detector" >> ~/Desktop/Iris/chat.log
```

---

### Task 6: LocalStore + SearchSettings (Robolectric)

**Files:**
- Create: `app/src/main/java/dev/elyssia/iris/storage/LocalStore.kt`
- Create: `app/src/main/java/dev/elyssia/iris/search/SearchSettings.kt`
- Test: `app/src/test/java/dev/elyssia/iris/storage/LocalStoreTest.kt`

**Interfaces:**
- Produces:
  - `data class HistoryEntry(url: String, title: String, at: Long)`, `data class Bookmark(url: String, title: String)`
  - `class LocalStore(context: Context)` with: `addHistory(url, title, at = System.currentTimeMillis())`, `listHistory(limit = 100): List<HistoryEntry>`, `clearHistory()`, `upsertBookmark(url, title)`, `deleteBookmark(url)`, `listBookmarks(): List<Bookmark>`, `wipeAll()`, `getSetting(key): String?`, `setSetting(key, value)`.
  - `class SearchSettings(store: LocalStore)` with `candidateUrls(): List<String>` (defaults = Global Constraints order), `setCandidateUrls(urls: List<String>)`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing test (exact file)**

```kotlin
package dev.elyssia.iris.storage

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalStoreTest {
    private lateinit var store: LocalStore

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.deleteDatabase("iris.db")
        store = LocalStore(context)
    }

    @Test
    fun historyRoundTrip() {
        store.addHistory("https://a", "A", at = 1000)
        store.addHistory("https://b", "B", at = 2000)
        val list = store.listHistory()
        assertEquals(listOf(2000, 1000), list.map { it.at })
    }

    @Test
    fun dedupesByAddress() {
        store.addHistory("https://a", "A", at = 1000)
        store.addHistory("https://a", "A2", at = 2000)
        assertEquals(1, store.listHistory().size)
        assertEquals("A2", store.listHistory().first().title)
    }

    @Test
    fun bookmarksUpsertAndDelete() {
        store.upsertBookmark("https://b", "B")
        store.upsertBookmark("https://b", "B2")
        assertEquals(listOf("B2"), store.listBookmarks().map { it.title })
        store.deleteBookmark("https://b")
        assertTrue(store.listBookmarks().isEmpty())
    }

    @Test
    fun clearHistoryOnlyAffectsHistory() {
        store.addHistory("https://a", "A")
        store.upsertBookmark("https://b", "B")
        store.clearHistory()
        assertTrue(store.listHistory().isEmpty())
        assertEquals(1, store.listBookmarks().size)
    }

    @Test
    fun wipeAllClearsEverything() {
        store.addHistory("https://a", "A")
        store.upsertBookmark("https://b", "B")
        store.wipeAll()
        assertTrue(store.listHistory().isEmpty() && store.listBookmarks().isEmpty())
    }

    @Test
    fun settingsRoundTrip() {
        store.setSetting("k", "v1")
        assertEquals("v1", store.getSetting("k"))
        store.setSetting("k", "v2")
        assertEquals("v2", store.getSetting("k"))
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.storage.LocalStoreTest"`
Expected: FAIL — `LocalStore` unresolved.

- [ ] **Step 3: Write implementation (both exact files)**

```kotlin
// storage/LocalStore.kt
package dev.elyssia.iris.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase

data class HistoryEntry(val url: String, val title: String, val at: Long)
data class Bookmark(val url: String, val title: String)

class LocalStore(context: Context) {
    val db: SQLiteDatabase = context.openOrCreateDatabase("iris.db", Context.MODE_PRIVATE, null).also {
        it.execSQL("CREATE TABLE IF NOT EXISTS history (url TEXT PRIMARY KEY, title TEXT, at INTEGER)")
        it.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (url TEXT PRIMARY KEY, title TEXT)")
        it.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
    }

    fun addHistory(url: String, title: String, at: Long = System.currentTimeMillis()) {
        db.execSQL("INSERT OR REPLACE INTO history (url, title, at) VALUES (?, ?, ?)", arrayOf(url, title, at))
    }

    fun listHistory(limit: Int = 100): List<HistoryEntry> {
        val out = mutableListOf<HistoryEntry>()
        db.rawQuery("SELECT url, title, at FROM history ORDER BY at DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) out += HistoryEntry(c.getString(0), c.getString(1), c.getLong(2))
        }
        return out
    }

    fun clearHistory() = db.execSQL("DELETE FROM history")

    fun upsertBookmark(url: String, title: String) =
        db.execSQL("INSERT OR REPLACE INTO bookmarks (url, title) VALUES (?, ?)", arrayOf(url, title))

    fun deleteBookmark(url: String) = db.execSQL("DELETE FROM bookmarks WHERE url = ?", arrayOf(url))

    fun listBookmarks(): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        db.rawQuery("SELECT url, title FROM bookmarks ORDER BY title", null).use { c ->
            while (c.moveToNext()) out += Bookmark(c.getString(0), c.getString(1))
        }
        return out
    }

    fun wipeAll() {
        clearHistory()
        db.execSQL("DELETE FROM bookmarks")
    }

    fun getSetting(key: String): String? {
        var out: String? = null
        db.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) out = c.getString(0)
        }
        return out
    }

    fun setSetting(key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", arrayOf(key, value))
    }
}
```

```kotlin
// search/SearchSettings.kt
package dev.elyssia.iris.search

import dev.elyssia.iris.storage.LocalStore

class SearchSettings(store: LocalStore) {
    fun candidateUrls(): List<String> =
        store.getSetting("searx_urls")?.split("\n")
            ?.filter { it.isNotBlank() }?.map { it.trim() }
            ?: DEFAULT_CANDIDATES

    fun setCandidateUrls(urls: List<String>) = store.setSetting("searx_urls", urls.joinToString("\n"))

    companion object {
        val DEFAULT_CANDIDATES = listOf(
            "http://100.110.99.98:8888",
            "http://192.168.31.65:8888",
            "http://127.0.0.1:8888",
        )
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.storage.LocalStoreTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit + log**

```bash
git add -A && git commit -m "feat: LocalStore (history/bookmarks/settings) + SearchSettings candidate URLs"
echo "$(date -Iseconds) task-6 done: local store" >> ~/Desktop/Iris/chat.log
```

---

### Task 7: SearchViewModel + SearchScreen + MainActivity wiring

**Files:**
- Create: `app/src/main/java/dev/elyssia/iris/search/SearchViewModel.kt`
- Create: `app/src/main/java/dev/elyssia/iris/ui/SearchScreen.kt`
- Modify: `app/src/main/java/dev/elyssia/iris/MainActivity.kt` (replace placeholder)

**Interfaces:**
- Produces:
  - `sealed interface SearchUiState { object Idle; data class Loading(query: String); data class Results(engine: Engine, items: List<SearchResult>); data class Error(message: String, query: String) }`
  - `fun defaultRouter(urls: List<String>): SearchRouter`
  - `class SearchViewModel(store: LocalStore, routerFactory: (List<String>) -> SearchRouter = ::defaultRouter)` with `val uiState: StateFlow<SearchUiState>`, `fun submit(query: String)`.
  - `@Composable fun SearchScreen(uiState: SearchUiState, onQuerySubmit: (String) -> Unit, onOpenUrl: (String) -> Unit)`
- Consumes: `SearchRouter.search` (Task 4), `UrlDetector` (Task 5), `LocalStore` + `SearchSettings` (Task 6), `Result`/`Engine` (Task 2).

- [ ] **Step 1: Write ViewModel (exact file)**

```kotlin
// search/SearchViewModel.kt
package dev.elyssia.iris.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.elyssia.iris.storage.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed interface SearchUiState {
    object Idle : SearchUiState
    data class Loading(val query: String) : SearchUiState
    data class Results(val engine: Engine, val items: List<SearchResult>) : SearchUiState
    data class Error(val message: String, val query: String) : SearchUiState
}

fun defaultRouter(urls: List<String>): SearchRouter = SearchRouter(
    providers = listOf(
        SearXngProvider(OkHttpClient(), urlProvider = { urls }),
        DdgLiteProvider(OkHttpClient()),
    ),
)

class SearchViewModel(
    store: LocalStore,
    routerFactory: (List<String>) -> SearchRouter = ::defaultRouter,
) : ViewModel() {
    private val router: SearchRouter = routerFactory(SearchSettings(store).candidateUrls())
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    fun submit(query: String) {
        if (query.isBlank()) return
        _uiState.value = SearchUiState.Loading(query)
        viewModelScope.launch {
            try {
                val outcome = router.search(query)
                _uiState.value = SearchUiState.Results(outcome.engine, outcome.results)
            } catch (e: NoResultsException) {
                _uiState.value = SearchUiState.Error(
                    "search unavailable — " + (e.errors.lastOrNull()?.message ?: "no providers reachable"),
                    query,
                )
            } catch (t: Throwable) {
                _uiState.value = SearchUiState.Error(t.message ?: "unexpected error", query)
            }
        }
    }
}
```

- [ ] **Step 2: Write SearchScreen (exact file)**

```kotlin
// ui/SearchScreen.kt
package dev.elyssia.iris.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.elyssia.iris.search.Engine
import dev.elyssia.iris.search.SearchResult
import dev.elyssia.iris.search.SearchUiState

private fun engineLabel(engine: Engine) = when (engine) {
    Engine.SEARXNG -> "SearXNG"
    Engine.DUCKDUCKGO -> "DuckDuckGo"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQuerySubmit: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Search privately…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onQuerySubmit(text); text = "" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Search") }
        Spacer(Modifier.height(8.dp))
        when (val state = uiState) {
            is SearchUiState.Idle -> {}
            is SearchUiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is SearchUiState.Error -> Column {
                Text("search unavailable — " + state.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onQuerySubmit(state.query) }) { Text("Retry") }
            }
            is SearchUiState.Results -> {
                Text("via " + engineLabel(state.engine), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                LazyColumn {
                    items(state.items, key = { it.url }) { r: SearchResult ->
                        ElevatedCard(
                            onClick = { onOpenUrl(r.url) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                                Text(r.url, style = MaterialTheme.typography.bodySmall)
                                if (r.snippet.isNotBlank()) {
                                    Text(r.snippet, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Write MainActivity (exact file — final for this task; Browser placeholder swapped in Task 8)**

```kotlin
// MainActivity.kt
package dev.elyssia.iris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.elyssia.iris.search.SearchViewModel
import dev.elyssia.iris.search.UrlDetector
import dev.elyssia.iris.storage.LocalStore
import dev.elyssia.iris.ui.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        setContent { AppRoot(store) }
    }
}

@Composable
fun AppRoot(store: LocalStore) {
    val vm: SearchViewModel = viewModel(initializer = { SearchViewModel(store) })
    val state by vm.uiState.collectAsState()
    var browsingUrl: String? by remember { mutableStateOf<String?>(null) }
    val currentUrl = browsingUrl
    if (currentUrl == null) {
        SearchScreen(
            uiState = state,
            onQuerySubmit = {
                val url = UrlDetector.detect(it)
                if (url != null) {
                    browsingUrl = url
                } else {
                    vm.submit(it)
                }
            },
            onOpenUrl = { browsingUrl = it },
        )
    } else {
        Text("browser placeholder: $currentUrl")
    }
}
```

- [ ] **Step 4: Build + tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: all unit tests PASS + `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit + log**

```bash
git add -A && git commit -m "feat: search UI with engine chip + retry, ViewModel, MainActivity wiring"
echo "$(date -Iseconds) task-7 done: search ui" >> ~/Desktop/Iris/chat.log
```

---

### Task 8: Browser — tabs, privacy WebView (DNT), incognito clear-on-exit

**Files:**
- Create: `app/src/main/java/dev/elyssia/iris/browser/TabState.kt`
- Create: `app/src/main/java/dev/elyssia/iris/browser/TabManager.kt`
- Create: `app/src/main/java/dev/elyssia/iris/browser/PrivacyWebView.kt`
- Create: `app/src/main/java/dev/elyssia/iris/browser/BrowserScreen.kt`
- Modify: `app/src/main/java/dev/elyssia/iris/MainActivity.kt` (final wiring)
- Test: `app/src/test/java/dev/elyssia/iris/browser/TabManagerTest.kt`

**Interfaces:**
- Produces:
  - `class TabState(id: Int, url: String?, title: String)`
  - `class TabManager { val tabs: List<TabState>; fun newTab(url: String? = null): TabState; fun close(tab: TabState); fun active(): TabState?; fun select(tab: TabState); fun openedUrl(tab: TabState, url: String); fun retitle(tab: TabState, title: String) }`
  - `object PrivacyWebView { fun configure(webView: WebView, incognito: Boolean); fun clearAll(webView: WebView, incognito: Boolean) }`
  - `@Composable fun BrowserScreen(initialUrl: String, incognito: Boolean = false, onExit: () -> Unit, onUrlRequested: (String) -> Unit)`
- Consumes: `UrlDetector` (Task 5) only from the *activity* level; `BrowserScreen` itself never routes.

- [ ] **Step 1: Write the failing test (exact file)**

```kotlin
package dev.elyssia.iris.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagerTest {
    @Test
    fun newTabStartsWithNullUrlWhenUnspecified() {
        val tm = TabManager()
        assertEquals(0, tm.tabs.size)
        val t = tm.newTab()
        assertEquals(null, t.url)
        assertEquals(1, tm.tabs.size)
    }

    @Test
    fun idsAreUnique() {
        val tm = TabManager()
        val a = tm.newTab(); val b = tm.newTab()
        assertTrue(a.id != b.id)
    }

    @Test
    fun closeRemovesTab() {
        val tm = TabManager()
        val t = tm.newTab("https://a")
        tm.close(t)
        assertEquals(0, tm.tabs.size)
    }

    @Test
    fun retitlePropagates() {
        val tm = TabManager()
        val t = tm.newTab("https://a")
        tm.retitle(t, "Some Page")
        assertEquals(listOf("Some Page"), tm.tabs.map { it.title })
    }

    @Test
    fun activeTracksSelection() {
        val tm = TabManager()
        val a = tm.newTab("https://a"); tm.newTab("https://b")
        tm.select(a)
        assertEquals("https://a", tm.active()?.url)
        tm.close(tm.tabs.first { it.id != a.id })
        assertEquals("https://a", tm.active()?.url)
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.browser.TabManagerTest"`
Expected: FAIL — unresolved types.

- [ ] **Step 3: Write model + manager + privacy config (exact files)**

```kotlin
// browser/TabState.kt
package dev.elyssia.iris.browser

class TabState(val id: Int, val url: String?, val title: String)
```

```kotlin
// browser/TabManager.kt
package dev.elyssia.iris.browser

class TabManager {
    private val _tabs = mutableListOf<TabState>()
    val tabs: List<TabState> get() = _tabs.toList()
    private var counter = 0
    private var activeIndex = -1

    fun newTab(url: String? = null): TabState {
        val t = TabState(id = counter++, url = url, title = "New Tab")
        _tabs.add(t)
        activeIndex = _tabs.size - 1
        return t
    }

    fun active(): TabState? = if (activeIndex in _tabs.indices) _tabs[activeIndex] else null

    fun close(tab: TabState) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i < 0) return
        _tabs.removeAt(i)
        activeIndex = when {
            _tabs.isEmpty() -> -1
            activeIndex >= _tabs.size -> _tabs.size - 1
            i < activeIndex -> activeIndex - 1
            else -> activeIndex
        }
    }

    fun select(tab: TabState) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) activeIndex = i
    }

    fun openedUrl(tab: TabState, url: String) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) _tabs[i] = TabState(t.id, url, _tabs[i].title)
    }

    fun retitle(tab: TabState, title: String) {
        val i = _tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0) _tabs[i] = TabState(_tabs[i].id, _tabs[i].url, title)
    }
}
```

Note: in `openedUrl` above, `t` refers to the captured parameter — write it literally as `_tabs[i] = TabState(tab.id, url, _tabs[i].title)` when implementing. (Kotlin does not alias parameters; use `tab.id`.)

```kotlin
// browser/PrivacyWebView.kt
package dev.elyssia.iris.browser

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object PrivacyWebView {
    val DNT_HEADERS: Map<String, String> = mapOf("DNT" to "1")

    fun configure(wv: WebView, incognito: Boolean) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = !incognito
            databaseEnabled = false
            cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
    }

    fun load(wv: WebView, url: String) = wv.loadUrl(url, DNT_HEADERS)

    fun clearAll(wv: WebView, incognito: Boolean) {
        wv.apply {
            clearCache(true)
            clearFormData()
            clearSslPreferences()
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
```

(User-Agent stays the browser default — no spoofing. JS stays on with a note that per-session toggling is v2.)

- [ ] **Step 4: Run manager tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "dev.elyssia.iris.browser.TabManagerTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Write BrowserScreen (exact file)**

```kotlin
// browser/BrowserScreen.kt
package dev.elyssia.iris.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.elyssia.iris.search.UrlDetector

@Composable
fun BrowserScreen(
    initialUrl: String,
    incognito: Boolean = false,
    onExit: () -> Unit,
    onUrlRequested: (String) -> Unit,
) {
    val tabs = remember { TabManager() }
    val webViews = remember { mutableStateMapOf<Int, WebView>() }
    var activeId by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        val t = tabs.newTab(initialUrl)
        activeId = t.id
    }

    val activeTab = tabs.tabs.firstOrNull { it.id == activeId }
    var input by remember(initialUrl) { mutableStateOf(initialUrl) }

    Surface(Modifier.fillMaxSize()) {
        Column {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { onUrlRequested(input) }) { Text("Go") }
                },
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                tabs.tabs.forEach { t ->
                    TextButton(onClick = {
                        tabs.select(t)
                        activeId = t.id
                        input = t.url ?: ""
                    }) { Text(if (t.id == activeId) "\u25B6 " + t.title else t.title) }
                    IconButton(onClick = {
                        webViews.remove(t.id)?.let { PrivacyWebView.clearAll(it, incognito); it.destroy() }
                        tabs.close(t)
                        if (tabs.tabs.isEmpty()) onExit()
                    }) { Text("\u00D7") }
                }
                TextButton(onClick = {
                    val t = tabs.newTab()
                    activeId = t.id
                    input = ""
                }) { Text("+") }
            }
            TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Back to search") }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        wv.webViewClient = WebViewClient()
                        PrivacyWebView.configure(wv, incognito)
                        webViews[activeId] = wv
                        activeTab?.url?.let { PrivacyWebView.load(wv, it) }
                    }
                },
                update = { },
                modifier = Modifier.fillMaxSize().weight(1f),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { PrivacyWebView.clearAll(it, incognito) }
        }
    }
}
```

Implementer notes (authoritative details):
- `factory` captures `activeId`/`webViews` state at creation; WebView keyed composition is unnecessary for v1 — one WebView persists across tab switches; tab switching in v1 re-loads the selected tab's URL into the single WebView. On `activeId` change the `update` lambda is intentionally empty; simplicity wins for v1 (single shared WebView, tabs are URL contexts). Add to `update`: `if (activeTab != null) PrivacyWebView.load(it, activeTab.url ?: return@AndroidView-holder-free-noop)`? — NO. Final rule: `update = { }` exactly as written; the TextButton tab click path must instead call `webViews[activeIdAfterSelect]?.let { wv -> val u = t.url; if (u != null) PrivacyWebView.load(wv, u) }` — implement tab-click as: set `activeId`, then `webViews[activeId]?.let { wv -> t.url?.let { PrivacyWebView.load(wv, it) } }` on the SAME click. This is the one behavioral nuance to get right.

- [ ] **Step 6: MainActivity final wiring (exact file)**

```kotlin
// MainActivity.kt (final)
package dev.elyssia.iris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.elyssia.iris.browser.BrowserScreen
import dev.elyssia.iris.search.SearchViewModel
import dev.elyssia.iris.search.UrlDetector
import dev.elyssia.iris.storage.LocalStore
import dev.elyssia.iris.ui.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        setContent { AppRoot(store) }
    }
}

@Composable
fun AppRoot(store: LocalStore) {
    val vm: SearchViewModel = viewModel(initializer = { SearchViewModel(store) })
    val state by vm.uiState.collectAsState()
    var browsingUrl: String? by remember { mutableStateOf<String?>(null) }
    var incognito: Boolean by remember { mutableStateOf(false) }
    val currentUrl = browsingUrl
    if (currentUrl == null) {
        SearchScreen(
            uiState = state,
            onQuerySubmit = {
                val url = UrlDetector.detect(it)
                if (url != null) browsingUrl = url else vm.submit(it)
            },
            onOpenUrl = { browsingUrl = it },
        )
    } else {
        BrowserScreen(
            initialUrl = currentUrl,
            incognito = incognito,
            onExit = { browsingUrl = null },
            onUrlRequested = { text ->
                val url = UrlDetector.detect(text)
                if (url != null) {
                    browsingUrl = url
                } else {
                    browsingUrl = null
                    vm.submit(text)
                }
            },
        )
    }
}
```

- [ ] **Step 7: Build + tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: all PASS + `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit + log**

```bash
git add -A && git commit -m "feat: tabbed privacy WebView browser with DNT + incognito clear-on-exit"
echo "$(date -Iseconds) task-8 done: browser" >> ~/Desktop/Iris/chat.log
```

---

### Task 9: Expose self-hosted SearXNG on the network (laptop-side)

**Files:**
- Modify: `/home/elyssia/opt/searxng/searxng/searx/settings.yml` (`server.bind_address`)
- Create: `docs/searxng-host-setup.md`

**Interfaces:**
- Produces: SearXNG answering JSON on Tailscale + LAN (verified with curl); documented revert instructions.

- [ ] **Step 1: Capture current runtime context for faithful relaunch**

```bash
grep -n "^  bind_address:" /home/elyssia/opt/searxng/searxng/searx/settings.yml
readlink /proc/890/cwd
tr '\0' '\n' < /proc/890/environ | grep -i searx || true
ps -p 890 -o args= || true
ls /etc/systemd/system | grep -i searx || true; systemctl --user list-units --no-pager 2>/dev/null | grep -i searx || true
```

Record outputs verbatim — they become the doc's relaunch section. (PID 890 was the running webapp at plan time; if it changed, use the new PID.)

- [ ] **Step 2: Rebind**

```bash
cp /home/elyssia/opt/searxng/searxng/searx/settings.yml /home/elyssia/opt/searxng/searxng/searx/settings.yml.bak
sed -i 's/^  bind_address: "127.0.0.1"/  bind_address: "0.0.0.0"/' \
  /home/elyssia/opt/searxng/searxng/searx/settings.yml
grep -n "^  bind_address:" /home/elyssia/opt/searxng/searxng/searx/settings.yml
```

Expected: `bind_address: "0.0.0.0"`.

- [ ] **Step 3: Restart the process**

If a systemd unit exists (Step 1) → `systemctl [or --user] restart <unit>`.
Else → kill the recorded PID and relaunch with the exact cwd + env captured in Step 1:

```bash
kill <pid>
cd <recorded-cwd>
nohup <recorded-python-path> searx/webapp.py >/tmp/searxng-relaunch.log 2>&1 &
sleep 3
ss -tlnp | grep 8888
```

Expected: listener present; if a relaunch env var like `SEARXNG_SETTINGS_PATH` was in the environ capture, include it in the nohup line.

- [ ] **Step 4: Verify loopback + Tailscale + LAN, JSON enabled**

```bash
for host in 127.0.0.1 100.110.99.98 192.168.31.65; do
  curl -s -o /dev/null -w "$host probe=%{http_code}\n" "http://$host:8888/"
done
curl -s -o /dev/null -w "tailscale_json=%{http_code}\n" "http://100.110.99.98:8888/search?q=x&format=json"
```

Expected: all `200`.

- [ ] **Step 5: Write `docs/searxng-host-setup.md` (content below + Step 1 recordings filled in)**

```markdown
# Hosting SearXNG for Iris (this workstation)

- Native install (no docker). Config: `/home/elyssia/opt/searxng/searxng/searx/settings.yml`.
- Change for Iris: `server.bind_address` → `0.0.0.0` (serves loopback + LAN 192.168.31.65 + Tailscale 100.110.99.98). Backup kept at `settings.yml.bak`.
- Candidate URLs the app probes, in order: Tailscale → LAN → loopback.
- JSON API enabled (verified `?format=json` → 200).
- Launch context at time of change: <PASTE Step 1 outputs: cwd, env, ps args, unit status>
- Revert: restore backup, re-run launch command from the context section above.
```

- [ ] **Step 6: Commit + log**

```bash
git add docs/searxng-host-setup.md && git commit -m "docs: how Iris reaches the self-hosted SearXNG"
echo "$(date -Iseconds) task-9 done: searxng exposed" >> ~/Desktop/Iris/chat.log
```

---

### Task 10: Emulator instrumented E2E

**Files:**
- Create: `app/src/androidTest/java/dev/elyssia/iris/AppBootTest.kt`
- Create: `app/src/androidTest/java/dev/elyssia/iris/RealSearxngE2E.kt`

**Interfaces:**
- Consumes: installed debug APK; local SearXNG via `adb reverse tcp:8888 tcp:8888`.

- [ ] **Step 1: Boot emulator**

```bash
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin
avdmanager create avd -n iris -k "system-images;android-34;google_apis;x86_64" -d pixel_5 --force
emulator -avd iris -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect >/tmp/emu.log 2>&1 &
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done; echo BOOTED'
```

Expected: `BOOTED`.

- [ ] **Step 2: Write + run boot test (exact file `AppBootTest.kt`)**

```kotlin
package dev.elyssia.iris

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBootTest {
    @Test
    fun activityLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { }
        }
    }
}
```

```bash
adb reverse tcp:8888 tcp:8888
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.elyssia.iris.AppBootTest
```

Expected: PASS.

- [ ] **Step 3: Real-SearXNG E2E (exact file `RealSearxngE2E.kt`)**

```kotlin
package dev.elyssia.iris

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.elyssia.iris.search.Engine
import dev.elyssia.iris.search.SearXngProvider
import dev.elyssia.iris.search.SearchRouter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSearxngE2E {
    @Test
    fun searxngRespondsThroughTunnel() {
        val router = SearchRouter(
            providers = listOf(
                SearXngProvider(
                    OkHttpClient(),
                    urlProvider = { listOf("http://127.0.0.1:8888") },
                ),
            ),
        )
        val outcome = runBlocking { router.search("test") }
        assertEquals(Engine.SEARXNG, outcome.engine)
        assertTrue(outcome.results.isNotEmpty())
    }
}
```

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.elyssia.iris.RealSearxngE2E`
Expected: PASS (proves the real self-hosted engine works through the tunnel end-to-end).

- [ ] **Step 4: Full connected suite**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: both classes PASS (emulator has internet; DDG reachable).

- [ ] **Step 5: Commit + log**

```bash
git add -A && git commit -m "test: emulator boot + real self-hosted SearXNG E2E via adb reverse"
echo "$(date -Iseconds) task-10 done: emulator e2e" >> ~/Desktop/Iris/chat.log
```

---

### Task 11: Installable release APK + README + push

**Files:**
- Modify: `app/build.gradle.kts` (release signs with debug keystore → installable)
- Create: `README.md`

- [ ] **Step 1: Make release installable (replace `buildTypes` block)**

```kotlin
buildTypes {
    release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("debug")
    }
}
```

- [ ] **Step 2: Build release**

Run: `./gradlew :app:assembleRelease`
Expected: `app/build/outputs/apk/release/app-release.apk` exists.

- [ ] **Step 3: Emulator smoke (screenshot)**

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell monkey -p dev.elyssia.iris -c android.intent.category.LAUNCHER 1
sleep 2
adb exec-out screencap -p > ~/Desktop/Iris/smoke.png
```

Expected: smoke.png shows the search UI. (gitignored.)

- [ ] **Step 4: Write README (exact content)**

```markdown
# Iris — lean privacy browser (Android)

Search front door with a privacy fallback chain:
1) your self-hosted SearXNG (probes Tailscale → LAN → loopback, 2s), 2) DuckDuckGo Lite (always available).

Privacy: no accounts, no telemetry, no analytics; third-party cookies blocked; cookies/cache cleared on exit; DNT header sent on every page load; incognito wipes everything on close.

## Build
- JDK 17 + Android SDK (see `gradle.properties` `sdk.dir`).
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `./gradlew assembleRelease` → installable debug-signed `app-release.apk`.

## Install
`adb install -r app/build/outputs/apk/release/app-release.apk`

## Point at your SearXNG
Defaults live in `search/SearchSettings.kt` (order: Tailscale 100.110.99.98 → LAN 192.168.31.65 → loopback). Host-side how-to: `docs/searxng-host-setup.md`.

## Search chain
`search/SearchRouter.kt` — probes each provider (2s), skips unreachable, degrades a provider for the session on hard failure, throws `NoResultsException` when everything fails. UI always shows which engine answered ("via SearXNG" / "via DuckDuckGo").
```

- [ ] **Step 5: Push + close out**

```bash
git add -A
git commit -m "feat: installable release APK + README"
git push -u origin main
echo "$(date -Iseconds) task-11 done: release apk, README, pushed to origin" >> ~/Desktop/Iris/chat.log
```

Expected: push over SSH succeeds; `origin/main` = local HEAD.

---

## Spec coverage map (for reviewer)

| Spec section | Task(s) |
|---|---|
| §1 chain SearXNG→DDG, anywhere-no-laptop | 4, 6 (fallback works with all candidates down) |
| §2 SearXNG exposure | 9 |
| §3 router/providers/normalization | 2, 3, 4 ("via" chip: 7) |
| §4 tabs/URL-bar/incognito/privacy defaults/DNT | 5, 7, 8 |
| §5 error handling (silent failover, no-conn, degraded session, timeouts 2/4/5s) | 4 (degradation+timeouts), 7 (Error+Retry) |
| §6 testing (unit fixtures, Robolectric, instrumented, adb reverse) | 2, 3, 4, 6, 8, 10 |
| §7 deliverables (repo, APKs, README) | 1, 11 |
| §8 non-goals | respected (nothing added) |
