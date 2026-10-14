# Releasing

The version lives in one file, `version.properties`. A merge to `main` that changes it tags the commit and creates a
GitHub release; the signed bundle is built locally and attached.

## Cutting a release

1. Branch from `main` and run `scripts/bump-version.sh`. It raises `versionCode` by one and the last part of
   `versionName` by one in `version.properties` (`0.1.11` (11) becomes `0.1.12` (12)); give it a name to set that
   instead, for example `scripts/bump-version.sh 0.2.0` (the code still goes up by one).
   - `versionCode` goes up for every upload to Google Play (Play rejects a repeat).
   - `versionName` is what people see, in the form `1.2.3`, and goes up with every release so each build can be told
     apart (About shows both, as "Version 0.1.11 (11)"). For now the last part matches `versionCode`; every release up
     to `v0.1.0-10` was `0.1.0`, so the first one after it is `scripts/bump-version.sh 0.1.11`.
2. Update `docs/play-store/release-notes-en-US.txt` (the Play "What's new", 500 characters at most) if it should change.
3. Open a pull request into `main`. CI runs the tests and builds a debug APK, and the **Version check** fails the PR
   if `versionCode` or `versionName` is not higher than on `main`.
4. Merge it. The **Release** workflow creates the tag `v<versionName>-<versionCode>` (for example `v0.1.11-11`) on the
   merge commit and a GitHub release for it: the release notes file first, then GitHub's list of merged pull requests.
   Versions below 1.0 are marked as pre-releases.
5. On your machine, with the upload key available (`keystore.properties`, vault unlocked):
   ```bash
   git checkout main && git pull
   scripts/release.sh
   ```
   It refuses to run unless HEAD is the tagged commit and the tree is clean, builds the signed bundle, and attaches
   `meanwhile-<name>-<code>-release.aab` and the R8 mapping file to the release.
6. Upload the bundle to the Play Console track.

The upload key never leaves your machine, which is why the bundle is not built in CI.

## Release notes

The GitHub release lists the merged pull requests since the last release, grouped by label (`.github/release.yml`):
**New** (`enhancement`), **Fixes** (`bug`) and **Everything else** (any other label, or none). Label a pull request
that only changes the version `release` so it is left out (one that also changes the app keeps its `enhancement` or
`bug`), and anything else that should not appear in the notes, such as build tooling, `skip-changelog`.

## One-time repository settings

See the notes in the pull request that added this; in short: require a pull request and the **Build and test** and
**Version check** checks before merging to `main`, squash-merge, delete branches on merge, and protect `v*` tags.
