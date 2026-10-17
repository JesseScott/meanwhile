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
| New Zealand | 12.7% | 10.3% | 1261 | RNZ National, Stuff, NZ Herald (English) |
| Peru | 10.3% | 0.0% | 2081 | Andina in English and Spanish, Panamericana (Spanish) |
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

## 3. What GDELT has for the same places

*Sampled 2026-10-10 to 2026-10-11 with `gdelt_sample.py 150`.*

The first attempt, on 2026-10-04, got HTTP 429 on every request, so GDELT was sampled again a week later. The sampler
asks for one country at a time (`sourcecountry:<FIPS>`, 7 day window, up to 250 articles), waits 2.5 minutes between
countries and 10 minutes after a 429, and gives up on a country after four tries. It took three passes over about 24
hours: **92 of 116 requests were refused with 429**, and only 4 countries answered on the first try. Australia never
answered (12 refusals in a row across the three passes, while other countries got through in between).

The counts are GDELT's raw answers, before the app's cleaning (blocklist, language fit, noise rules), so they are an upper
bound on what the app would show.

| Country | Articles (7 days) | Domains | Biggest domains |
| --- | ---: | ---: | --- |
| New Zealand | 8 | 4 | home.nzcity.co.nz 3, nzherald.co.nz 2, itbrief.co.nz 2 |
| Peru | 6 | 4 | larepublica.pe 3, rpp.pe 1, diariocorreo.pe 1 |
| Australia | no answer | | HTTP 429 every time |
| Argentina | 28 | 13 | argentinastar.com 5, lanacion.com.ar 4, tn.com.ar 4 |
| Brazil | 128 | 12 | g1.globo.com 103, estadao.com.br 8, arede.info 5 |
| French Polynesia | 0 | 0 | |
| Mauritius | 3 | 2 | defimedia.info 2, 5plus.mu 1 |
| Japan | 59 | 12 | ascii.jp 23, hokkoku.co.jp 9, nikkei.com 8 |
| Colombia | 27 | 7 | elheraldo.co 11, lapatria.com 9, eltiempo.com 3 |
| Chile | 22 | 4 | diariolongino.cl 10, 24horas.cl 8, elmostrador.cl 3 |
| Uruguay | 6 | 3 | elpais.com.uy 2, ladiaria.com.uy 2, bitacora.com.uy 2 |
| China | 250 (cap) | 56 | 163.com 24, baijiahao.baidu.com 22, tech.ifeng.com 17 |
| Indonesia | 92 | 17 | cnnindonesia.com 15, koran-jakarta.com 13, jawapos.com 12 |
| Madagascar | 13, none usable | 1 | storm.mg 13 (Taiwanese, already blocklisted) |
| United States of America | 250 (cap) | 99 | boredpanda.com 16, atlantamagazine.com 15, tomsguide.com 10 |
| Ecuador | 0 | 0 | |
| Reunion (France) | 0 | 0 | |
| Cambodia | 0 | 0 | |
| Morocco | 0 | 0 | |
| Canada | 53 | 19 | saultstar.com 12, thesudburystar.com 10, therecord.com 4 |
| Philippines | 20 | 5 | rappler.com 6, bworldonline.com 4, shangbao.com.ph 4 |
| Spain | 192 | 33 | heraldo.es 33, lavozdegalicia.es 21, diariovasco.com 15 |
| Botswana | 0 | 0 | |
| South Africa | 12 | 5 | citizen.co.za 6, timeslive.co.za 2, it-online.co.za 2 |
| Fiji | 5 | 2 | fijivillage.com 3, pina.com.fj 2 |

Tuvalu, the Cook Islands, Kiribati and Bermuda were not sampled. Fiji was, though it is not in the landing table.

What it shows:
- **Most of the shortlist is thin or empty in GDELT.** Six countries came back empty (French Polynesia, Ecuador, Reunion,
  Cambodia, Morocco, Botswana), Madagascar has nothing usable once storm.mg is removed, and five more had under ten
  articles in a week (Mauritius, Fiji, Uruguay, Peru, New Zealand). With Australia unanswered, that is most of the
  "English markets" share, so the backup feeds are doing real work, not just insurance.
- **Where GDELT has volume, one outlet often dominates.** g1.globo.com is 103 of Brazil's 128, Madagascar is a single
  misfiled site, and Chile and Colombia are mostly two papers each.
- **GDELT covers a few countries well:** China, the United States, Spain, Indonesia, Japan and Canada. Their feeds are
  skipped when GDELT already has enough, so they cost little.
- **GDELT is slow to answer, not just unreliable.** A country often needed two or three 10-minute waits, which is why the
  app stands down after a refusal and shows a recent cached answer first.

One sample in one week, so read small counts as "thin", not as exact figures.

## 4. Countries not researched

Everything below the shortlist: 150 or so countries that few cities' antipodes fall on. They will fall back to GDELT alone.
The next candidates, when a tester lands somewhere empty, are the neighbours of the heavy hitters (Fiji and Samoa are
covered by RNZ; Uruguay's neighbour Paraguay; Chile's and Peru's neighbour Bolivia).

## Re-running

```bash
python tools/coverage-survey/landing.py          # the ranking
python tools/coverage-survey/verify_feeds.py     # re-fetch recommended feeds (needs out/feeds/*.json)
./gradlew :core:test --tests '*LiveFeedsCheck*' -DliveFeeds=<path to out/feeds_verified.csv>
python tools/coverage-survey/gdelt_sample.py 150 # slow (hours); rerun to retry only countries that got no answer
```
