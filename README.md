<p align="center">
  <img src="store/graphics/feature-graphic-1024x500.png" alt="Fanela" width="680">
</p>

<p align="center">
  <a href="https://github.com/AlexisKaravitis/fanela-tv/releases/latest"><img alt="Latest release" src="https://img.shields.io/badge/release-v1.3.0-1565c0"></a>
  <img alt="Android TV 8.0+" src="https://img.shields.io/badge/Android%20TV-8.0%2B-3ddc84">
  <img alt="Languages" src="https://img.shields.io/badge/languages-English%20%7C%20%CE%95%CE%BB%CE%BB%CE%B7%CE%BD%CE%B9%CE%BA%CE%AC-555">
  <img alt="No ads" src="https://img.shields.io/badge/ads-none-555">
</p>

# Fanela

Fanela is an Android TV app for people who follow football and basketball clubs, in Greece and beyond, and Formula 1
and UFC too. You pick your clubs once, from the Super League to the Premier League, LaLiga or the NBA, and the app
shows their next matches, live scores and results, and where each game is on: in Greece the exact channel with its
number on Magenta TV, elsewhere the channels that hold the competition's TV rights in your country.

It is made for the remote: your club's next match large on the screen, in your club's colours, and nothing to sign
up for.

![Fanela home screen](docs/screenshots/home.png)

**[Download the APK](https://github.com/AlexisKaravitis/fanela-tv/releases/latest)** ·
[Install guide](docs/INSTALL.md) ·
[How to use Fanela](docs/TUTORIAL.md) ·
[What's new](CHANGELOG.md) ·
[Working on the code](CONTRIBUTING.md)

## What it does

- **All 14 Super League clubs**, plus each club's basketball team when it has one (EuroLeague, EuroCup, Basket
  League, Champions League).
- **Clubs from 18 more competitions:** the Premier League, LaLiga, Serie A, Bundesliga, Ligue 1, Liga Portugal,
  Eredivisie, Süper Lig, MLS, Brasileirão, Argentine Primera, Saudi Pro League, the three UEFA cups, the NBA, the
  EuroLeague and the EuroCup. Pick a league, then its clubs; cup and European games come with the club.
- **Where to watch, by country.** Set your country (it starts from the TV's own). Outside Greece each card shows who
  holds that competition's rights there this season, for example "Likely on Sky Sports or TNT Sports" in the UK.
  When no official list exists it says so plainly: "Check your provider's guide".
- **Two layouts.** Club shows one club at a time in its colours: the next match large, the latest results, then the
  fixtures, with your clubs in a rail on the left. Matchday puts the next match on a stage, with rows of cards for
  every club below.
- **In Greece, the channel for every match.** The channel logo and its Magenta TV number come from the Greek TV guides. Before a
  guide lists a game, Fanela shows the channel that holds the home team's TV rights and marks it "Likely".
- **Live scores** every 30 seconds while the app is open, then the result and whether your club won, drew or lost.
- **Rows on the TV's home screen.** On TVs with the classic Android TV home screen, each club gets its own row of
  cards, drawn large enough to read from the sofa, so you can see the next game without opening the app. Each row
  carries the Fanela shirt in that club's colours.
- **Five card styles.** Signature, Clean, Kit, Ticket and Clash, for the home-screen rows and the cards in the app,
  with live previews in Settings > Appearance. A switch hides scores on the home screen, for matches you'll watch
  later.
- **Formula 1 and UFC.** Follow them in Settings > More sports. Each gets its own row on the TV's home screen and
  its own place in the app: every session of a race weekend and every part of a fight card at your local time, where
  to watch (in Greece ANT1 and ANT1+, Magenta Sport 6), results, and the race weekend and fight card screens. Pick
  up to two drivers or teams, or the fighters you follow, and the cards put them first.
- **Your club's colours.** Choose a favourite club and the app's accents, its logo and even its tile on the TV take
  on that club's kit.
- **English or Greek**, switchable at any time, with Greek names for the clubs of every league.
- No account, no ads, no tracking. See the [privacy policy](docs/PRIVACY.md).

## Screenshots

| | |
|---|---|
| ![Match with a confirmed channel](docs/screenshots/match.png) | ![Match with a likely channel](docs/screenshots/match-likely.png) |
| A match the TV guide has listed: channel and number. | Not listed yet: the channel that usually has it, and a way to get the app. |
| ![The Matchday layout](docs/screenshots/matchday.png) | ![Settings](docs/screenshots/settings.png) |
| Matchday: the next match on a stage, cards for every club below. | Language, favourite club, country, basketball, home-screen rows and appearance. |
| ![Choosing clubs by league](docs/screenshots/clubs.png) | ![Choosing a country](docs/screenshots/country.png) |
| Pick clubs league by league. The badge is how many you follow. | The country sets the channels and which leagues come first. |
| ![Fanela in the UK](docs/screenshots/abroad-home.png) | ![A Premier League match seen from the UK](docs/screenshots/abroad-match.png) |
| Outside Greece: the season's rights holders for your country. | Newcastle against Arsenal, seen from the UK. |
| ![A match with no known TV partner](docs/screenshots/check-guide.png) | ![Fanela in Greek](docs/screenshots/home-greek.png) |
| No official list for your country: the app says so. | The whole app in Greek, here in PAOK's colours. |

### Formula 1 and UFC

| | |
|---|---|
| ![The Las Vegas Grand Prix weekend](docs/screenshots/f1-weekend.png) | ![The UFC 334 fight card](docs/screenshots/ufc-event.png) |
| The race weekend: every session at your time, where to watch it and the championship. | The fight card, bout by bout, with records and the channel for each part. |
| ![Formula 1 and UFC rows on the home screen](docs/screenshots/home-screen-sports.png) | |
| Their own rows on the TV's home screen, next to your clubs. | |

### Card styles on the TV's home screen

| | |
|---|---|
| ![Signature cards on the Android TV home screen](docs/screenshots/home-screen-row.png) | ![Clean cards](docs/screenshots/home-screen-clean.png) |
| Signature, the default: your club's colours and its crest. Each row has its own shirt logo. | Clean: calm and minimal. |
| ![Kit cards](docs/screenshots/home-screen-kit.png) | ![Ticket cards](docs/screenshots/home-screen-ticket.png) |
| Kit: your club's shirt down the side. | Ticket: a match ticket with the channel on the stub. |
| ![Clash cards](docs/screenshots/home-screen-clash.png) | ![Settings > Appearance](docs/screenshots/appearance.png) |
| Clash: both clubs' colours on a diagonal. | Settings > Appearance: live previews, the app layout and the scores switch. |

## Install

Fanela is not on Google Play. You install the APK yourself, which takes about five minutes the first time.

1. Download `fanela-1.3.0.apk` from the [Releases page](https://github.com/AlexisKaravitis/fanela-tv/releases/latest).
2. Copy it to the TV with **Send Files to TV** or a USB stick, or install it from a computer with
   `adb install fanela-1.3.0.apk`.
3. Open Fanela, choose a language and your country, pick your clubs and press **Done**.

The [install guide](docs/INSTALL.md) has every step, including how to allow installs from unknown sources on Sony,
Philips, TCL and Google TV devices.

**Works on** Android TV and Google TV 8.0 or newer: Sony, Philips, TCL, Hisense and Xiaomi TVs, Chromecast with
Google TV, Google TV Streamer, Nvidia Shield, Xiaomi Mi Box.
**Doesn't work on** Samsung (Tizen), LG (webOS), Apple TV or Fire TV devices running Vega OS.

To update, install the new APK over the old one. Every release is signed with the same key, so your clubs and
settings stay. Updating from 1.0 keeps everything as it was and sets the country to Greece.

## Where the data comes from

| What | Source |
|---|---|
| Football fixtures and live scores | ESPN |
| EuroLeague and EuroCup | EuroLeague |
| Basket League and Basketball Champions League | TheSportsDB |
| Channels, channel numbers and logos in Greece | MagentaTV channel list, Sport24 and MagentaTV TV guides, SofaScore |
| TV partners in 14 other countries | The leagues', UEFA's, the NBA's and EuroLeague's own broadcaster pages, kept in `res/raw/broadcasters.json` with the date each was checked |
| Gaps in those lists | Wikipedia's broadcaster lists (CC BY-SA 4.0), in `res/raw/broadcasters_wikipedia.json`; an official row always wins |
| Crests | the feeds above, SofaScore for any that are missing |
| Formula 1 and UFC: schedules, results, standings and fighters | ESPN |
| Formula 1 and UFC TV partners | The broadcasters' and the press's announcements of each deal, kept in `res/raw/sport_rights.json` |
| Circuit outlines | bacinger/f1-circuits on GitHub (MIT License) |

None of these is an official, guaranteed service. If one of them changes, that part of the app can stop working until
Fanela is updated. Each source is handled on its own, so the rest keeps working, and a card says "Not updated" when
its data is more than 6 hours old.

## Limitations

- **The "Likely" channel** comes from a table of this season's TV rights (2026-27). It has to be updated every summer.
  Channels confirmed by a TV guide don't depend on it.
- **Outside Greece the channel is a season-level guide**, not the match's own channel, with no channel logos or
  numbers; your provider's guide has the last word. The broadcaster lists need checking each August and January.
  The Super League, Brasileirão, Argentine Primera and Saudi Pro League publish no list for other countries.
- **Basketball outside Greece** covers the NBA, the EuroLeague and the EuroCup.
- **Formula 1 and UFC while live** show LIVE with the session or the bout. The lap and running order of a race and the
  round of a fight come in a later update.
- **Home-screen rows** only appear on the classic Android TV home screen (for example Sony sets from before 2021,
  Nvidia Shield, Mi Box). Google TV and most operator launchers don't show rows from apps.
- **The TV tile in club colours** shows once the TV's home screen reloads; on some TVs that takes a restart.
- **Live basketball scores** are there for EuroLeague and EuroCup. For other basketball competitions the score
  appears after the game.
- Fanela is an independent app and isn't affiliated with any club, league or broadcaster. Club names and crests
  belong to their owners and are only shown to identify the teams.

## Branches and releases

| | |
|---|---|
| `main` | The current release, 1.3.0 (tag `v1.3.0`). |
| `v1.0.0` | The first release, kept as a tag. |

Every release and its APK is on the [Releases page](https://github.com/AlexisKaravitis/fanela-tv/releases).

## Building from source

You need JDK 17 and the Android SDK with platform 36.

```bash
git clone https://github.com/AlexisKaravitis/fanela-tv.git
cd fanela
./gradlew testDebugUnitTest assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The debug build installs as `app.fanela.tv.debug`, next to the released app, so you can try changes without losing
your real setup. Release builds are signed with a key that is not in the repository. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the day-to-day workflow and [docs/RELEASE.md](docs/RELEASE.md) for releases.

### Project layout

| Path | What |
|---|---|
| `app/src/main/java/app/fanela/tv` | The app: plain Java, framework Views, no third-party libraries |
| `app/src/main/res/raw` | Bundled data: the channel line-up, the broadcaster tables, the Formula 1 and UFC TV rights, the circuit outlines and the licences |
| `app/src/debug` | Debug-only tools: card renders for every state and style, fixed home screens for screenshots, and a data audit for every club |
| `app/src/test` | Unit tests |
| `design/make_brand.py` | Draws the shirt logo, the app icon and TV banner in every kit colour, and the store graphics |
| `store/graphics` | Icon, banner and feature graphic |
| `tools/demo_all.sh` | Renders every card for every club and language on a device (optionally as seen from another country) |
| `tools/wiki_broadcasters.py` | Rebuilds the Wikipedia broadcaster layer |
| `docs/` | Install guide, tutorial, privacy policy, release notes |

## Privacy

Fanela has no accounts, ads or analytics. Your clubs and settings stay on the TV. The app only talks to the data
sources listed above; the broadcaster tables are built in. Full policy: [docs/PRIVACY.md](docs/PRIVACY.md).

## Licence

Copyright © 2026 Alexis Karavitis. All rights reserved. The code is shared here for reference; please ask before
reusing it. The Wikipedia-derived table (`broadcasters_wikipedia.json`) is shared under CC BY-SA 4.0, as its source
requires. Google Sans is used under the SIL Open Font License and Roboto Condensed under the Apache License 2.0.
