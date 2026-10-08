# AGENTS.md

Notes for AI coding agents and new contributors. See `README.md` for what the app does and `docs/RELEASING.md` for releases.

Meanwhile is a free, non-commercial Android app that shows news from the antipode of the user's location (or the sea
there, when it is open water). Kotlin, Jetpack Compose, Android 8.0+. No accounts, no ads.

## Working agreement
- Every change goes through a pull request into `main` (squash merge; required checks **Build and test** and
  **Version check**). Agents: commit locally in small, logical steps, but do not push or open a PR unless asked.
- Never commit or print secrets: `keystore.properties` (release signing) and `app/google-services.json` (Firebase) are
  git-ignored and stay local.
- Releases are tagged from `version.properties`; see `docs/RELEASING.md`. Play's "What's new" limit is 500 characters.
- Line endings are pinned to LF (`.gitattributes`).

## Build and test
```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug   # what CI runs
./gradlew :core:test --tests '*LiveFeedsCheck*' -DliveFeeds=<csv> # optional network check of feeds; no-op without the flag
```
JDK 17. OkHttp is pinned to 5.1.0 in `:app` (newer versions need a newer compileSdk).

## Architecture
- `:core` is plain Kotlin/JVM and holds everything testable: geometry, ISO-to-FIPS codes, news sources, cleaning rules,
  ocean maths, telemetry vocabulary, settings. One test file per class, no network (Ktor `MockEngine`, virtual time).
- `:app` is Compose with MVI: `UiEvent` into `MainViewModel`, out come an immutable `UiState` and one-shot `UiEffect`s
  (`UiContract.kt`). All user-facing wording lives in `ui/Messages.kt`; state carries typed values, never text.
  Never expose a news source's name or internals to the user.
- Headlines: `FallbackNewsSource` asks GDELT and the curated RSS feeds (`CuratedFeeds.kt`, keyed by **FIPS** code, not
  ISO) concurrently and streams snapshots. `CachingNewsSource` shows a recent entry at once, then replaces it with the
  live result. Empty results are never cached.
- **Quiet versus unknown:** "no headlines" means every source answered with nothing. If a source failed and nothing was
  found, throw `NewsUnavailableException` (shown as "busy"); never report it as a quiet place, or the app hops to another country.
- The sticky header holds only things whose size does not depend on loading. Progress, errors and suggestions live
  in the list or an overlay.

## Privacy
Usage and crash reports (Firebase) are opt-in and off by default. Only events from the fixed vocabulary in
`Telemetry.kt` can be sent. The one fact about a place is `country` on `load_finished` and `load_failed`: the ISO
code of the country whose headlines were loaded, from the fixed `KNOWN_COUNTRIES` list (`unknown` for anything else,
`none` on the open sea), for device-located and searched loads alike. Events never include the user's exact location,
the antipode's coordinates, a place name, a search or what was read, and `article_opened` never carries a country.
Changing this means updating together: the About screen text, the opt-in prompt, the README, the hosted privacy page,
the Play Data safety form, and `docs/play-store/tester-message.txt`. New telemetry events or parameters also need
`TelemetryTest` updated. If reports start to carry something new, raise `AnalyticsPrompt.CONSENT_VERSION` so that
people who answered under the old wording are asked again; nothing is sent until they answer.

## News sources
- GDELT's DOC API is unreliable: stricter than its documented limit, blocks last a minute or more, errors can arrive
  as plain text with a 200, and thin countries return empty answers. `GdeltNewsSource` stands down after a refusal; do
  not add retries or call it in bulk.
- Google News is not used (its licence is personal and non-commercial only).
- Add feeds to `CuratedFeeds.kt` only after checking them with `LiveFeedsCheck`: English first, fresh, small (the app
  fetches every feed for a country on each load) and https only. `FeedDates.kt` handles odd or wrong dates.
- `docs/coverage/README.md` and `tools/coverage-survey/` describe how countries and feeds were chosen.

## Testing notes
- Debug builds log through `AppLog`; release builds log nothing. Risky changes are worth trying in a signed release
  build too (R8 and signing can change behaviour).
- Work is tracked in GitHub issues. Label each pull request `enhancement` or `bug` (or `skip-changelog`); the release
  notes are grouped by those labels (see `docs/RELEASING.md`).
