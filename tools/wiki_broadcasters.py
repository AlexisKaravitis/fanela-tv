#!/usr/bin/env python3
"""
Builds app/src/main/res/raw/broadcasters_wikipedia.json: the season table's second layer, from Wikipedia's broadcaster
lists (CC BY-SA 4.0, credited on the app's Sources screen). The hand-kept broadcasters.json comes first in the app; these
rows only fill a (country, competition) that it leaves empty.

Reads each page once through the MediaWiki API (action=parse) with a descriptive User-Agent, a second apart. Run it in
August and January, check the diff, then build.
    python tools/wiki_broadcasters.py            # writes the file
    python tools/wiki_broadcasters.py --dry-run  # prints the rows only
"""
import json
import re
import sys
import datetime
import time
import urllib.parse
import urllib.request
from html.parser import HTMLParser

UA = 'Fanela/1.1 (personal Android TV app; season broadcaster table; reads each page once per run)'
API = 'https://en.wikipedia.org/w/api.php'
OUT = 'app/src/main/res/raw/broadcasters_wikipedia.json'
def season(today=None):
    """The football season running on a day: 1 July to 30 June (from July, the new season)."""
    today = today or datetime.date.today()
    y = today.year if today.month >= 7 else today.year - 1
    return ('%d-07-01' % y, '%d-06-30' % (y + 1))


SEASON = season()

# competition -> page; the table to read is the one whose heading or columns name countries
PAGES = {
    'eng.1': 'List of Premier League overseas broadcasters',
    'uefa.champions': 'List of UEFA Champions League broadcasters',
    'esp.1': 'List of La Liga broadcasters',
    'ita.1': 'List of Serie A broadcasters',
    'nba': 'List of current NBA broadcasters',  # local US teams only today: yields nothing, kept for when it lists countries
}
# only the first table of these pages is the current cycle (later ones are the next rights cycle, women's or youth)
FIRST_TABLE_ONLY = {'uefa.champions'}

COUNTRIES = {
    'United Kingdom': 'GB', 'Ireland': 'IE', 'Republic of Ireland': 'IE', 'United States': 'US', 'Canada': 'CA',
    'Spain': 'ES', 'Italy': 'IT', 'Germany': 'DE', 'Austria': 'AT', 'France': 'FR', 'Portugal': 'PT',
    'Netherlands': 'NL', 'Belgium': 'BE', 'Turkey': 'TR', 'Türkiye': 'TR', 'Cyprus': 'CY', 'Greece': 'GR',
}
# viewer brands for names the lists write differently
NAMES = {'fubo TV': 'Fubo', 'fuboTV': 'Fubo', 'Cytavision Sports': 'Cytavision', 'CYTA': 'Cytavision',
         'Nova Sports': 'Nova', 'LiveModeTV': 'LiveMode', 'beIN Sports': 'beIN SPORTS', 'Mega TV': 'MEGA',
         # what viewers see, not the company: Amazon's service, Paramount's streamer, TelevisaUnivision's sports brand
         'Amazon Prime Video': 'Prime Video', 'Skydance': 'Paramount+', 'TelevisaUnivision': 'TUDN'}


class Tables(HTMLParser):
    """Every top-level wikitable as rows of cell texts (rowspans repeated); footnotes and styles dropped."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tables, self.depth, self.cur, self.row, self.cell = [], 0, None, None, None
        self.heading, self.inh, self.skip, self.span, self.col, self.rs = '', False, 0, {}, 0, 1

    def handle_starttag(self, tag, a):
        a = dict(a)
        if tag in ('h2', 'h3', 'h4'):
            self.inh, self.heading = True, ''
        if tag in ('sup', 'style'):
            self.skip += 1
        if tag == 'table':
            self.depth += 1
            if self.depth == 1 and 'wikitable' in (a.get('class') or ''):
                self.cur, self.span = {'heading': self.heading, 'rows': []}, {}
        if self.cur is None or self.depth != 1:
            return
        if tag == 'tr':
            self.row, self.col = [], 0
        if tag in ('td', 'th') and self.row is not None:
            self._spans()
            self.cell, self.rs = '', int(re.sub('[^0-9]', '', a.get('rowspan', '1')) or 1)
        if tag in ('br', 'li') and self.cell is not None:
            self.cell += '; '

    def _spans(self):
        while self.col in self.span and self.span[self.col][0] > 0:
            n, txt = self.span[self.col]
            self.row.append(txt)
            self.span[self.col] = (n - 1, txt)
            self.col += 1

    def handle_endtag(self, tag):
        if tag in ('h2', 'h3', 'h4'):
            self.inh = False
        if tag in ('sup', 'style'):
            self.skip -= 1
        if self.cur is not None and self.depth == 1:
            if tag in ('td', 'th') and self.cell is not None:
                t = re.sub(r'\s+', ' ', self.cell).strip(' ;')
                self.row.append(t)
                if self.rs > 1:
                    self.span[self.col] = (self.rs - 1, t)
                self.col, self.cell = self.col + 1, None
            if tag == 'tr' and self.row is not None:
                self._spans()
                self.cur['rows'].append(self.row)
                self.row = None
        if tag == 'table':
            if self.depth == 1 and self.cur is not None:
                self.tables.append(self.cur)
                self.cur = None
            self.depth -= 1

    def handle_data(self, d):
        if self.skip:
            return
        if self.inh:
            self.heading += d
        if self.cell is not None:
            self.cell += d


def fetch(title):
    q = urllib.parse.urlencode({'action': 'parse', 'page': title, 'prop': 'text|revid', 'format': 'json',
                                'formatversion': '2', 'redirects': '1'})
    req = urllib.request.Request(API + '?' + q, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)['parse']


def names(cell):
    out = []
    for part in re.split(r'[;/]', re.sub(r'\(.*?\)', '', cell)):
        n = part.strip(' .,')
        n = NAMES.get(n, n)
        if n and n not in out and len(n) < 40:
            out.append(n)
    return out


def rows(comp, parsed):
    p = Tables()
    p.feed(parsed['text'])
    found = {}
    for i, t in enumerate(p.tables):
        if comp in FIRST_TABLE_ONLY and i > 0:
            break
        head = [c.lower() for c in (t['rows'][0] if t['rows'] else [])]
        if not head or not any(h in ('country', 'territory', 'countries') for h in head):
            continue
        ci = next(k for k, h in enumerate(head) if h in ('country', 'territory', 'countries'))
        bi = next((k for k, h in enumerate(head) if 'broadcaster' in h or 'rights holder' in h), ci + 1)
        for r in t['rows'][1:]:
            if len(r) <= max(ci, bi):
                continue
            for country in re.split(r'\s*;\s*', r[ci]):
                cc = COUNTRIES.get(country.strip())
                if cc:
                    for n in names(r[bi]):
                        found.setdefault(cc, [])
                        if n not in found[cc]:
                            found[cc].append(n)
    return found


def main():
    dry = '--dry-run' in sys.argv
    comps = {}
    for comp, title in PAGES.items():
        parsed = fetch(title)
        got = rows(comp, parsed)
        url = 'https://en.wikipedia.org/w/index.php?title=' + urllib.parse.quote(parsed['title'].replace(' ', '_')) \
            + '&oldid=' + str(parsed['revid'])
        print('%-16s %-48s rev %s: %d countries' % (comp, parsed['title'], parsed['revid'], len(got)))
        for cc in sorted(got):
            print('    %s %s' % (cc, '; '.join(got[cc])))
        if got:
            comps[comp] = {'from': SEASON[0], 'until': SEASON[1], 'sources': [url], 'title': parsed['title'],
                           'countries': {cc: got[cc] for cc in sorted(got)}}
        time.sleep(1)
    if dry:
        return
    doc = {'about': 'Season broadcasters from Wikipedia\'s lists, used only where broadcasters.json (hand-kept from the '
                    'official pages) has no row. Generated by tools/wiki_broadcasters.py; check the diff before building.',
           'license': 'CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/)',
           'credit': 'Wikipedia contributors', 'updated': time.strftime('%Y-%m-%d'), 'competitions': comps}
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print('wrote', OUT)


if __name__ == '__main__':
    main()
