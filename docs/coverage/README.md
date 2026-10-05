# Coverage survey: where people land, and who covers it

*Run on 2026-10-04. The scripts are in `tools/coverage-survey/`; raw output goes to its git-ignored `out/` folder.*

Meanwhile's main source, GDELT, is often empty or rate limited, and for thin places what it has is mostly a single
misfiled outlet. Backup RSS feeds from real local outlets fix that, but there are too many countries to cover by
hand, so this survey asks **which countries people actually land on** and finds working feeds for those first.

## 1. Where people land

`landing.py` takes about 115 big metro areas, works out each one's antipode, and finds the country it falls in, or the
nearest one if it is open water, skipping places with no news (French Southern Lands, Antarctica, Pitcairn) the same way
the app does. "All" weights by metro population. "English markets" counts only the cities in the US, Canada, UK,
Ireland, Australia, New Zealand and South Africa, where the app's first users most likely are.

| Country | Share, all | Share, English markets | Typical km from land | Backup feeds now |
| --- | ---: | ---: | ---: | --- |
| New Zealand | 12.7% | 10.3% | 1261 | RNZ National, Stuff (English) |
| Peru | 10.3% | 0.0% | 2081 | Andina in English and Spanish |
| Australia | 10.2% | 42.7% | 1638 | ABC News, Sydney Morning Herald (English) |
| Argentina | 8.7% | 0.0% | 0 | Buenos Aires Times (English), Clarin, Ambito |
| Brazil | 8.0% | 0.0% | 769 | Agencia Brasil in English and Portuguese, Folha |
| French Polynesia | 7.8% | 0.0% | 1840 | Tahiti Infos (French), on top of RNZ Pacific |
| Mauritius | 6.5% | 12.1% | 2437 | Newsmoris (English), Defi Media (French) |
| Japan | 4.1% | 0.0% | 1150 | Japan Times, Japan Today (English) |
| Colombia | 3.9% | 0.0% | 0 | El Tiempo, El Colombiano (Spanish) |
| Chile | 3.6% | 0.0% | 2908 | Cooperativa, The Clinic (Spanish) |
| Uruguay | 2.9% | 0.0% | 358 | Montevideo Portal, El Observador, El Pais (Spanish) |
| China | 2.7% | 0.0% | 163 | CGTN, Sixth Tone (English) |
| Indonesia | 2.4% | 0.0% | 243 | Jakarta Post, Antara (English) |
| Madagascar | 2.3% | 9.7% | 2836 | 2424.mg, Newsmada, RFI, allAfrica (done earlier, PR 13) |
| Tuvalu | 2.2% | 0.0% | 514 | RNZ Pacific only (already there) |
| Cook Islands | 2.2% | 0.0% | 2352 | RNZ Pacific only (already there) |
| United States of America | 2.1% | 9.1% | 1236 | NPR, New York Times (fallback) |
| Ecuador | 1.6% | 0.0% | 0 | El Comercio, El Universo (Spanish) |
| Reunion (France) | 1.5% | 6.3% | 1572 | Zinfos974, Imaz Press (French) |
| Cambodia | 1.2% | 0.0% | 0 | Khmer Times, Phnom Penh Post, CamboJA (English) |
| Morocco | 0.9% | 3.9% | 1631 | Hespress in English and French |
| Canada | 0.7% | 3.2% | 1777 | Global News, Globe and Mail (fallback) |
| Kiribati | 0.6% | 0.0% | 1580 | RNZ Pacific only (already there) |
| Philippines | 0.5% | 0.0% | 819 | Philstar, GMA, Inquirer (English) |
| Spain | 0.2% | 1.1% | 0 | El Mundo (Spanish, fallback) |
| Bermuda | 0.2% | 1.0% | 68 | none yet |
| Botswana | 0.1% | 0.5% | 0 | The Gazette, Sunday Standard (English, thin) |
| South Africa | 0.0% | 0.2% | 3022 | Daily Maverick (English) |

Limits worth knowing:
- Populations, not users. The English-market column is the better guide for now.
- The app finds a tiny island only when one of its probe points lands on it, so places like Mauritius, Tuvalu or the
  Cook Islands show up here more than they will in the app.
- City coordinates are rounded and "nearest" uses outline vertices; it ranks, it does not measure.

## 2. Feeds

Four research agents looked for news feeds for 25 countries (one region each) and fetched every candidate. Every
recommended feed was then fetched again independently (`verify_feeds.py`), and finally pushed through the app's own
`RssNewsSource` with the OkHttp engine the app uses (`LiveFeedsCheck`, run with `-DliveFeeds=<csv>`), because a site can
answer curl and refuse OkHttp, and only the app's parser can say whether dates and encodings come out right.

**64 feeds recommended; 64 work in the app.** The app-side check also found problems no HTTP check could: a French
weekday name, a feed with no time zone, one that labels Manila time as UTC (so items arrive hours in the future), a
1970 placeholder for "no date", and feeds that keep years of old items. All are handled now (`FeedDates.kt`, with tests).

Picked for the app, per country, in this order: English where an outlet has an English edition, fresh (newest item within
a day), small (the app fetches every feed for a country on each load, so nothing over about 300 KB), and https only.

**Left out on purpose:**
- *Too big:* El Comercio Peru (1.5 MB), Infobae (1.1 MB), Expreso (868 KB), La Nacion (809 KB), Semana (653 KB), La Tercera
  (633 KB), El Pais Spain (623 KB), The Citizen South Africa (638 KB), G1 (341 KB).
- *Plain http:* ECNS (China). Android blocks cleartext traffic.
- *Blocked, dead or stale:* Fiji Times, Fijivillage, Fiji Sun, China Daily, Global Times, Xinhua, Kyodo, Jakarta Globe, Manila
  Bulletin, PNA, Midi Madagasikara, Radio1 Tahiti, Morocco World News, H24, Medias24, Le360, News24, TimesLive, Mail and
  Guardian, EWN, CBC, Mmegi, Peru21, Biobio, Emol, Pagina12, Primicias, La Republica Peru, Le Mauricien, lexpress.mu.
- *Not country news:* the generic SBS feed (stale), Infobae Argentina (empty), El Espectador (comments only), The Rio Times.

## 3. What this does not tell us

**GDELT itself was not surveyed.** The plan was to ask GDELT for the same countries (one request every 2.5 minutes, 7 day
window, raw answers saved) and run the app's own cleaning over them, to find which countries are thin or empty and which
do not need feeds. This connection is blocked by GDELT (HTTP 429 on every request, including after waiting 10 minutes
twice), so no GDELT numbers were collected. An earlier overnight survey got 12 successes against 33 rate-limited answers.
Rather than keep a blocked connection busy, the sampler was stopped. `gdelt_sample.py` is ready to run from a different
network and resumes where it left off.

Until then, the feeds are insurance for the shortlist rather than a response to measured gaps. The cost is small (a few
tens of kilobytes per load, skipped when GDELT already has enough), but it does mean some of these countries probably did
not need them.

## 4. Countries not researched

Everything below the shortlist: 150 or so countries that few cities' antipodes fall on. They will fall back to GDELT alone.
The next candidates, when a tester lands somewhere empty, are the neighbours of the heavy hitters (Fiji and Samoa are
covered by RNZ; Uruguay's neighbour Paraguay; Chile's and Peru's neighbour Bolivia).

## Re-running

```bash
python tools/coverage-survey/landing.py          # the ranking
python tools/coverage-survey/verify_feeds.py     # re-fetch recommended feeds (needs out/feeds/*.json)
./gradlew :core:test --tests '*LiveFeedsCheck*' -DliveFeeds=<path to out/feeds_verified.csv>
python tools/coverage-survey/gdelt_sample.py 150 # slow and polite; from a connection GDELT has not blocked
```
