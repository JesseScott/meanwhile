# Meanwhile monitoring

`meanwhile_monitor.py` writes the daily HEALTH and weekly GROWTH reports that the project's cloud routines post. It
only reads from Google Play: Android vitals (Play Developer Reporting API), the bulk report CSVs in Cloud Storage, and
recent reviews. Standard library Python plus `openssl`; nothing to install. It is not part of the app build, and it
takes the package name from `applicationId` in `app/build.gradle.kts`, so run it from a checkout of this repo.

```bash
M=/mnt/project-files/monitoring
python3 tools/monitoring/meanwhile_monitor.py health --state $M/monitor-state.json
python3 tools/monitoring/meanwhile_monitor.py growth --state $M/monitor-state.json --history $M/growth-history.csv \
    --experiments $M/experiments.md
python3 -m unittest discover tools/monitoring        # offline tests, no credentials needed
```

The first line of the output is `STATUS: OK`, `WARN` or `ERROR`. Health stays quiet unless it is `WARN` or `ERROR`;
growth always posts. The state file remembers which anomalies, error clusters and low reviews were already reported.

Health checks the user-perceived crash and ANR rates, slow cold start, excessive wakeups, Play's anomalies and new or
growing error clusters. Growth reports installs, uninstalls, active devices, store listing visitors and conversion,
ratings and reviews. The experiments log is a Markdown file with one line per change,
`- 2026-10-28 New short description`; lines from the last two weeks are printed under the numbers.

Credentials come from two environment variables on the `meanwhile-monitor` cloud environment, never from the repo:

- `MEANWHILE_PLAY_SA_KEY_B64`: the read-only Play service account's JSON key, base64 encoded (the same account as
  Moonlight; its Play Console access is account-wide). It has no Google Cloud roles; in Play Console it has only "View
  app information and download bulk reports (read-only)" and "Reply to reviews" (Google requires that one to read
  reviews; the script never replies).
- `MEANWHILE_PLAY_REPORTS_BUCKET`: `gs://pubsite_prod_rev_...` from Play Console, Download reports.

To check them without printing values: `for v in MEANWHILE_PLAY_SA_KEY_B64 MEANWHILE_PLAY_REPORTS_BUCKET; do
[ -n "${!v}" ] && echo "$v set" || echo "$v missing"; done`. The environment needs network access to
`oauth2.googleapis.com`, `playdeveloperreporting.googleapis.com`, `androidpublisher.googleapis.com` and
`storage.googleapis.com`.

Thresholds: user-perceived crash rate 1.09% and ANR rate 0.47% over 7 days (Play's bad behaviour thresholds), checked
per device type; a warning also fires when the 28-day rate reaches 80% of the threshold, or when the newest
versionCode has at least twice the previous version's rate (and at least 0.5%).

Things that look wrong but are not:

- At low volume Play withholds vitals and the report says "no data". That is OK, not an error.
- Slow rendering is only available to games (403), so it is not queried.
- Install reports lag 3 to 7 days; the report says which week they cover.
- The reports bucket can answer 403 for about a day after the account is given access.
- At low volume every traffic source is "Other", so the traffic source breakdown is left out.
- In Open Beta, tester feedback is private and only shown in Play Console, so the reviews API may return nothing. The
  report says so instead of counting zero.
