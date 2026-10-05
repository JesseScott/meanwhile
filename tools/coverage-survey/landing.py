#!/usr/bin/env python3
"""
Which countries do people land on?

For each of about 100 big metro areas, work out the antipode and the country it falls in, or the nearest one if it
is open water (the same question the app asks, answered offline from country outlines instead of the phone's
geocoder). Weighted by metro population, so the countries that matter most for backup news feeds float to the top.

    python landing.py            # writes out/landing.csv and prints the ranking

Approximate by design: city coordinates are rounded to about a degree, populations are rough, and "nearest" uses the
closest outline vertex. Good enough to rank countries; not a census.
"""
import csv
import json
import math
import os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
os.makedirs(OUT, exist_ok=True)

# name, lat, lon, metro population in millions (rough), market: "en" for the English-language Play Store markets
# the app is most likely to find its first users in (US, Canada, UK, Ireland, Australia, New Zealand, South Africa).
CITIES = [
    ("Vancouver", 49.3, -123.1, 2.6, "en"), ("Seattle", 47.6, -122.3, 4.0, "en"), ("Portland", 45.5, -122.7, 2.5, "en"),
    ("San Francisco Bay", 37.8, -122.4, 7.8, "en"), ("Los Angeles", 34.1, -118.2, 13.0, "en"), ("San Diego", 32.7, -117.2, 3.3, "en"),
    ("Las Vegas", 36.2, -115.1, 2.3, "en"), ("Phoenix", 33.4, -112.1, 5.0, "en"), ("Salt Lake City", 40.8, -111.9, 1.3, "en"),
    ("Denver", 39.7, -105.0, 3.0, "en"), ("Calgary", 51.0, -114.1, 1.6, "en"), ("Edmonton", 53.5, -113.5, 1.5, "en"),
    ("Winnipeg", 49.9, -97.1, 0.8, "en"), ("Dallas", 32.8, -96.8, 7.6, "en"), ("Houston", 29.8, -95.4, 7.1, "en"),
    ("Austin", 30.3, -97.7, 2.3, "en"), ("Minneapolis", 44.98, -93.3, 3.7, "en"), ("Chicago", 41.9, -87.6, 9.5, "en"),
    ("Detroit", 42.3, -83.0, 4.3, "en"), ("Toronto", 43.7, -79.4, 6.4, "en"), ("Ottawa", 45.4, -75.7, 1.4, "en"),
    ("Montreal", 45.5, -73.6, 4.3, "en"), ("Halifax", 44.6, -63.6, 0.5, "en"), ("Boston", 42.4, -71.1, 4.9, "en"),
    ("New York", 40.7, -74.0, 20.1, "en"), ("Philadelphia", 39.95, -75.2, 6.2, "en"), ("Washington", 38.9, -77.0, 6.4, "en"),
    ("Atlanta", 33.7, -84.4, 6.1, "en"), ("Miami", 25.8, -80.2, 6.2, "en"), ("Anchorage", 61.2, -149.9, 0.4, "en"),
    ("Honolulu", 21.3, -157.9, 1.0, "en"),
    ("Mexico City", 19.4, -99.1, 21.8, ""), ("Guadalajara", 20.7, -103.3, 5.3, ""), ("Monterrey", 25.7, -100.3, 5.3, ""),
    ("Guatemala City", 14.6, -90.5, 3.0, ""), ("San Jose CR", 9.9, -84.1, 1.4, ""), ("Havana", 23.1, -82.4, 2.1, ""),
    ("Bogota", 4.7, -74.1, 11.0, ""), ("Caracas", 10.5, -66.9, 2.9, ""), ("Quito", -0.2, -78.5, 2.8, ""),
    ("Lima", -12.0, -77.0, 11.0, ""), ("Santiago", -33.4, -70.7, 6.8, ""), ("Buenos Aires", -34.6, -58.4, 15.4, ""),
    ("Montevideo", -34.9, -56.2, 1.8, ""), ("Sao Paulo", -23.6, -46.6, 22.4, ""), ("Rio de Janeiro", -22.9, -43.2, 13.5, ""),
    ("Brasilia", -15.8, -47.9, 4.8, ""),
    ("London", 51.5, -0.1, 14.8, "en"), ("Manchester", 53.5, -2.2, 3.3, "en"), ("Edinburgh", 55.95, -3.2, 0.9, "en"),
    ("Dublin", 53.35, -6.3, 2.1, "en"), ("Paris", 48.9, 2.35, 11.3, ""), ("Lisbon", 38.7, -9.1, 3.0, ""),
    ("Madrid", 40.4, -3.7, 6.7, ""), ("Barcelona", 41.4, 2.2, 5.6, ""), ("Amsterdam", 52.4, 4.9, 2.9, ""),
    ("Brussels", 50.85, 4.35, 2.1, ""), ("Berlin", 52.5, 13.4, 4.7, ""), ("Munich", 48.1, 11.6, 3.0, ""),
    ("Zurich", 47.4, 8.5, 1.4, ""), ("Milan", 45.5, 9.2, 4.3, ""), ("Rome", 41.9, 12.5, 4.3, ""),
    ("Vienna", 48.2, 16.4, 2.0, ""), ("Prague", 50.1, 14.4, 1.3, ""), ("Warsaw", 52.2, 21.0, 3.1, ""),
    ("Budapest", 47.5, 19.0, 3.0, ""), ("Stockholm", 59.3, 18.1, 2.4, ""), ("Copenhagen", 55.7, 12.6, 2.1, ""),
    ("Oslo", 59.9, 10.75, 1.5, ""), ("Helsinki", 60.2, 24.9, 1.5, ""), ("Athens", 38.0, 23.7, 3.6, ""),
    ("Istanbul", 41.0, 29.0, 15.5, ""), ("Moscow", 55.75, 37.6, 17.5, ""), ("Kyiv", 50.45, 30.5, 3.5, ""),
    ("Cairo", 30.0, 31.2, 21.8, ""), ("Casablanca", 33.6, -7.6, 3.7, ""), ("Lagos", 6.5, 3.4, 15.4, ""),
    ("Accra", 5.6, -0.2, 4.2, ""), ("Addis Ababa", 9.0, 38.75, 5.0, ""), ("Nairobi", -1.3, 36.8, 5.0, ""),
    ("Johannesburg", -26.2, 28.0, 10.0, "en"), ("Cape Town", -33.9, 18.4, 4.8, "en"), ("Durban", -29.9, 31.0, 3.9, "en"),
    ("Tel Aviv", 32.1, 34.8, 4.2, ""), ("Dubai", 25.2, 55.3, 3.6, ""), ("Riyadh", 24.7, 46.7, 7.7, ""),
    ("Tehran", 35.7, 51.4, 9.4, ""), ("Karachi", 24.9, 67.0, 17.2, ""), ("Delhi", 28.6, 77.2, 32.0, ""),
    ("Mumbai", 19.1, 72.9, 21.0, ""), ("Bangalore", 13.0, 77.6, 13.2, ""), ("Chennai", 13.1, 80.3, 11.5, ""),
    ("Dhaka", 23.8, 90.4, 22.5, ""), ("Colombo", 6.9, 79.9, 2.3, ""), ("Bangkok", 13.75, 100.5, 11.0, ""),
    ("Singapore", 1.35, 103.8, 6.0, ""), ("Kuala Lumpur", 3.1, 101.7, 8.4, ""), ("Jakarta", -6.2, 106.8, 34.5, ""),
    ("Manila", 14.6, 121.0, 14.4, ""), ("Ho Chi Minh City", 10.8, 106.7, 9.3, ""), ("Hong Kong", 22.3, 114.2, 7.5, ""),
    ("Shenzhen", 22.5, 114.1, 13.0, ""), ("Shanghai", 31.2, 121.5, 28.5, ""), ("Beijing", 39.9, 116.4, 21.0, ""),
    ("Taipei", 25.0, 121.5, 7.0, ""), ("Seoul", 37.55, 127.0, 25.5, ""), ("Tokyo", 35.7, 139.7, 37.0, ""),
    ("Osaka", 34.7, 135.5, 19.0, ""),
    ("Perth", -31.95, 115.9, 2.1, "en"), ("Adelaide", -34.9, 138.6, 1.4, "en"), ("Melbourne", -37.8, 145.0, 5.2, "en"),
    ("Sydney", -33.9, 151.2, 5.4, "en"), ("Brisbane", -27.5, 153.0, 2.6, "en"), ("Auckland", -36.85, 174.8, 1.7, "en"),
    ("Wellington", -41.3, 174.8, 0.5, "en"),
]

# Countries missing from the outline file (small island states and territories), as single points.
EXTRA_POINTS = {
    "MUS Mauritius": (-20.3, 57.6), "REU Reunion (France)": (-21.1, 55.5), "SYC Seychelles": (-4.7, 55.5),
    "COM Comoros": (-11.7, 43.3), "MDV Maldives": (3.2, 73.2), "FJI Fiji": (-17.7, 178.0), "TON Tonga": (-21.2, -175.2),
    "WSM Samoa": (-13.8, -172.1), "VUT Vanuatu": (-15.4, 166.9), "NCL New Caledonia": (-21.3, 165.5),
    "PYF French Polynesia": (-17.7, -149.4), "COK Cook Islands": (-21.2, -159.8), "KIR Kiribati": (1.9, -157.4),
    "TUV Tuvalu": (-8.5, 179.2), "SLB Solomon Islands": (-9.6, 160.2), "PNG Papua New Guinea": (-6.3, 147.0),
    "NRU Nauru": (-0.5, 166.9), "MHL Marshall Islands": (7.1, 171.4), "FSM Micronesia": (6.9, 158.2),
    "PLW Palau": (7.5, 134.6), "GUM Guam": (13.4, 144.8), "CPV Cape Verde": (16.0, -24.0), "STP Sao Tome": (0.3, 6.7),
    "BHR Bahrain": (26.0, 50.55), "MLT Malta": (35.9, 14.4), "BRB Barbados": (13.2, -59.5), "TTO Trinidad": (10.5, -61.3),
    "BHS Bahamas": (25.0, -77.4), "JAM Jamaica": (18.1, -77.3), "ATG Antigua": (17.1, -61.8), "MTQ Martinique": (14.6, -61.0),
    "GLP Guadeloupe": (16.2, -61.6), "ISL Iceland": (64.9, -18.5), "FRO Faroe": (62.0, -6.8), "SHN St Helena": (-15.95, -5.7),
    "FLK Falklands": (-51.7, -59.5), "PCN Pitcairn": (-25.1, -130.1), "ASM American Samoa": (-14.3, -170.7),
    "NIU Niue": (-19.05, -169.9), "WLF Wallis and Futuna": (-13.8, -177.15), "TKL Tokelau": (-9.2, -171.85),
}


def rad(x):
    return x * math.pi / 180


def haversine(a, b):
    la1, lo1, la2, lo2 = map(rad, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371.0 * math.asin(math.sqrt(h))


def antipode(lat, lon):
    return -lat, ((lon + 180 + 180) % 360) - 180


def load_countries():
    data = json.load(open(os.path.join(HERE, "data", "countries.geo.json"), encoding="utf-8"))
    countries = {}
    for f in data["features"]:
        g = f["geometry"]
        polys = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        countries[f"{f['id']} {f['properties']['name']}"] = polys
    return countries


def in_ring(lon, lat, ring):
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i][0], ring[i][1]
        xj, yj = ring[j][0], ring[j][1]
        if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / (yj - yi + 1e-12) + xi:
            inside = not inside
        j = i
    return inside


def country_at(countries, lat, lon):
    for name, polys in countries.items():
        for poly in polys:
            if in_ring(lon, lat, poly[0]) and not any(in_ring(lon, lat, hole) for hole in poly[1:]):
                return name
    return None


# Places with no news to speak of. The app skips some of these on purpose (NO_COVERAGE_ISO: French Southern Lands,
# Antarctica, ...) and keeps searching outward; Pitcairn (about 50 people) behaves the same in practice.
SKIP = ("ATF ", "ATA ", "PCN ")


def skipped(name):
    return name is None or name.startswith(SKIP)


def nearest(countries, lat, lon):
    best, best_d = None, 1e9
    for name, polys in countries.items():
        if skipped(name):
            continue
        for poly in polys:
            for x, y in poly[0][::3]:  # every third vertex is plenty for a ranking
                d = haversine((lat, lon), (y, x))
                if d < best_d:
                    best, best_d = name, d
    for name, (la, lo) in EXTRA_POINTS.items():
        if skipped(name):
            continue
        d = haversine((lat, lon), (la, lo))
        if d < best_d:
            best, best_d = name, d
    return best, best_d


def _unused_nearest(countries, lat, lon):
    best, best_d = None, 1e9
    for name, polys in countries.items():
        for poly in polys:
            for x, y in poly[0][::3]:  # every third vertex is plenty for a ranking
                d = haversine((lat, lon), (y, x))
                if d < best_d:
                    best, best_d = name, d
    for name, (la, lo) in EXTRA_POINTS.items():
        d = haversine((lat, lon), (la, lo))
        if d < best_d:
            best, best_d = name, d
    return best, best_d


def main():
    countries = load_countries()
    rows = []
    for name, lat, lon, pop, market in CITIES:
        alat, alon = antipode(lat, lon)
        # A tiny island can sit under the antipode and be missed by the outlines, so check the extra points first.
        near_extra = min(((haversine((alat, alon), p), n) for n, p in EXTRA_POINTS.items() if not skipped(n)), default=(1e9, None))
        land = country_at(countries, alat, alon)
        if skipped(land):
            land = None  # the antipode is on land nobody writes news about: look outward, as the app does
        if land and near_extra[0] > 150:
            country, dist = land, 0.0
        elif near_extra[0] <= 150:
            country, dist = near_extra[1], near_extra[0]
        else:
            country, dist = nearest(countries, alat, alon)
        rows.append((name, lat, lon, pop, market, round(alat, 1), round(alon, 1), country, round(dist)))

    with open(os.path.join(OUT, "landing_by_city.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["city", "lat", "lon", "metro_millions", "market", "antipode_lat", "antipode_lon", "country", "km_from_land"])
        w.writerows(rows)

    total = sum(r[3] for r in rows)
    en_total = sum(r[3] for r in rows if r[4] == "en")
    agg = defaultdict(lambda: {"pop": 0.0, "en": 0.0, "cities": [], "dist": []})
    for city, _, _, pop, market, _, _, country, dist in rows:
        a = agg[country]
        a["pop"] += pop
        a["en"] += pop if market == "en" else 0
        a["cities"].append(city)
        a["dist"].append(dist)

    ranked = sorted(agg.items(), key=lambda kv: -kv[1]["pop"])
    with open(os.path.join(OUT, "landing.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["country", "share_all_pct", "share_english_markets_pct", "cities", "typical_km_from_land", "example_cities"])
        for country, a in ranked:
            w.writerow([country, round(100 * a["pop"] / total, 1), round(100 * a["en"] / en_total, 1), len(a["cities"]),
                        round(sorted(a["dist"])[len(a["dist"]) // 2]), "; ".join(a["cities"][:6])])

    print(f"{len(rows)} cities, {len(ranked)} landing countries\n")
    print(f"{'country':28} {'all%':>5} {'en%':>5} {'cities':>6} {'km':>6}  examples")
    cum = 0.0
    for country, a in ranked:
        cum += a["pop"]
        print(f"{country:28} {100*a['pop']/total:5.1f} {100*a['en']/en_total:5.1f} {len(a['cities']):6d} {sorted(a['dist'])[len(a['dist'])//2]:6d}  {', '.join(a['cities'][:4])}  (cum {100*cum/total:.0f}%)")


if __name__ == "__main__":
    main()
