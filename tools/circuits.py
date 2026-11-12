# -*- coding: utf-8 -*-
"""Builds app/src/main/res/raw/circuits.json: Formula 1 circuit outlines keyed by ESPN circuit id.

Source: bacinger/f1-circuits (https://github.com/bacinger/f1-circuits), GeoJSON of every F1 circuit's centre line,
MIT licence, Copyright (c) 2019-2025 Tomislav Bacinger. The app draws the outline as a line in the card's own colours
(no image licence involved); the credit is in the app's About text (I18n, stream G) and in the JSON itself.

Each track: lon/lat projected flat around its own latitude (x = lon * cos(lat0), y = -lat: north up), simplified with
Ramer-Douglas-Peucker to at most MAX_POINTS, scaled so the longer side is 1000 and stored as integers.

usage: python tools/circuits.py [f1-circuits.geojson] > app/src/main/res/raw/circuits.json
       (no argument: downloads the GeoJSON from GitHub)
"""
import json
import math
import sys
import urllib.request

SOURCE = "https://raw.githubusercontent.com/bacinger/f1-circuits/master/f1-circuits.geojson"
MAX_POINTS = 110

# ESPN circuit id (sports.core.api.espn.com/v2/sports/racing/leagues/f1/circuits) -> bacinger id. Checked by hand
# against both lists (Nov 2026). ESPN lists some venues twice (Red Bull Ring 779 / 3954, Silverstone 611 / 3955,
# Imola 604 / 3985). Left out on purpose: 3984 (Bahrain outer track: a different layout from bh-2002), and the venues
# bacinger has no outline for (Korea 753, India 754, Hanoi 3885), and ESPN's NASCAR ovals.
ESPN = {
    "601": "au-1953",   # Melbourne Grand Prix Circuit
    "602": "my-1999",   # Sepang
    "603": "bh-2002",   # Bahrain International Circuit
    "604": "it-1953",   # Imola
    "605": "es-2026",   # Madring
    "606": "mc-1929",   # Monaco
    "607": "az-2016",   # Baku
    "608": "ca-1978",   # Gilles-Villeneuve
    "609": "us-2012",   # Circuit of the Americas
    "610": "fr-1969",   # Paul Ricard
    "611": "gb-1948",   # Silverstone
    "612": "de-1932",   # Hockenheim
    "613": "hu-1986",   # Hungaroring
    "614": "tr-2005",   # Istanbul
    "615": "it-1922",   # Monza
    "616": "be-1925",   # Spa-Francorchamps
    "617": "br-1940",   # Interlagos
    "618": "jp-1962",   # Suzuka
    "619": "cn-2004",   # Shanghai
    "740": "sg-2008",   # Marina Bay
    "744": "ae-2009",   # Yas Marina
    "779": "at-1969",   # Red Bull Ring
    "780": "ru-2014",   # Sochi
    "782": "mx-1962",   # Hermanos Rodriguez
    "1102": "nl-1948",  # Zandvoort
    "1125": "pt-2008",  # Portimao
    "3954": "at-1969",  # Red Bull Ring (second ESPN id)
    "3955": "gb-1948",  # Silverstone (second ESPN id)
    "3971": "it-1914",  # Mugello
    "3985": "it-1953",  # Imola (second ESPN id)
    "3990": "de-1927",  # Nurburgring
    "4069": "sa-2021",  # Jeddah
    "4211": "qa-2004",  # Lusail
    "4243": "us-2022",  # Miami
    "4250": "us-2023",  # Las Vegas
    "5826": "es-1991",  # Barcelona-Catalunya
}


def rdp(pts, eps):
    """Ramer-Douglas-Peucker on an open polyline."""
    if len(pts) < 3:
        return pts
    (x0, y0), (x1, y1) = pts[0], pts[-1]
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy)
    best, bi = -1.0, 0
    for i in range(1, len(pts) - 1):
        x, y = pts[i]
        d = abs(dy * x - dx * y + x1 * y0 - y1 * x0) / n if n > 0 else math.hypot(x - x0, y - y0)
        if d > best:
            best, bi = d, i
    if best <= eps:
        return [pts[0], pts[-1]]
    return rdp(pts[:bi + 1], eps)[:-1] + rdp(pts[bi:], eps)


def simplify(ring, max_points):
    """A closed ring simplified to at most max_points (the closing point not repeated)."""
    if len(ring) <= max_points:
        return ring
    # split the ring at its point farthest from the start, so both halves are open polylines
    far = max(range(len(ring)), key=lambda i: math.hypot(ring[i][0] - ring[0][0], ring[i][1] - ring[0][1]))
    a, b = ring[:far + 1], ring[far:] + [ring[0]]
    lo, hi = 0.0, 1.0
    for _ in range(40):  # the smallest tolerance that keeps it under max_points
        mid = (lo + hi) / 2
        n = len(rdp(a, mid)) + len(rdp(b, mid)) - 2
        if n > max_points:
            lo = mid
        else:
            hi = mid
    return rdp(a, hi)[:-1] + rdp(b, hi)[:-1]


def outline(coords):
    lat0 = sum(c[1] for c in coords) / len(coords)
    k = math.cos(math.radians(lat0))
    pts = [(c[0] * k, -c[1]) for c in coords]
    if math.hypot(pts[0][0] - pts[-1][0], pts[0][1] - pts[-1][1]) < 1e-9:
        pts = pts[:-1]
    # scale to 0..1 on the longer side first, so the tolerance is in the same units for every track
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    s = max(max(xs) - min(xs), max(ys) - min(ys))
    pts = [((x - min(xs)) / s, (y - min(ys)) / s) for x, y in pts]
    pts = simplify(pts, MAX_POINTS)
    flat = []
    for x, y in pts:
        flat += [int(round(x * 1000)), int(round(y * 1000))]
    return flat


def main():
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding="utf-8") as f:
            geo = json.load(f)
    else:
        with urllib.request.urlopen(SOURCE, timeout=60) as r:
            geo = json.loads(r.read().decode("utf-8"))
    by_id = {}
    for feat in geo["features"]:
        g = feat["geometry"]
        coords = g["coordinates"] if g["type"] == "LineString" else g["coordinates"][0]
        by_id[feat["properties"]["id"]] = (feat["properties"].get("Name", ""), coords)
    out = {"source": "https://github.com/bacinger/f1-circuits", "licence": "MIT",
           "credit": "Circuit outlines: bacinger/f1-circuits, Copyright (c) 2019-2025 Tomislav Bacinger, MIT licence",
           "format": "per ESPN circuit id: p = x0,y0,x1,y1,... integers 0..1000, north up, longer side 1000",
           "circuits": {}}
    for espn, bid in sorted(ESPN.items(), key=lambda kv: int(kv[0])):
        if bid not in by_id:
            sys.stderr.write("missing in the GeoJSON: %s (%s)\n" % (bid, espn))
            continue
        name, coords = by_id[bid]
        out["circuits"][espn] = {"b": bid, "name": name, "p": outline(coords)}
    data = json.dumps(out, ensure_ascii=False, separators=(",", ":")) + "\n"
    sys.stdout.buffer.write(data.encode("utf-8"))


if __name__ == "__main__":
    main()
