<p align="center">
  <img src="store/graphics/feature-graphic-1024x500.png" alt="Fanela" width="680">
</p>

<p align="center">
  <a href="https://github.com/AlexisKaravitis/fanela-tv/releases/latest"><img alt="Latest release" src="https://img.shields.io/badge/release-v1.0.4-1565c0"></a>
  <img alt="Android TV 8.0+" src="https://img.shields.io/badge/Android%20TV-8.0%2B-3ddc84">
  <img alt="Languages" src="https://img.shields.io/badge/languages-English%20%7C%20%CE%95%CE%BB%CE%BB%CE%B7%CE%BD%CE%B9%CE%BA%CE%AC-555">
  <img alt="No ads" src="https://img.shields.io/badge/ads-none-555">
</p>

# Fanela

Fanela is an Android TV app for people who follow Greek football and basketball. You pick your clubs once, and the
app shows their next matches, live scores and results, and the channel each game is on, with its number on Magenta TV
when the guides have it.

It is made for the remote: one row per club, big cards, and nothing to sign up for.

![Fanela home screen](docs/screenshots/home.png)

**[Download the APK](https://github.com/AlexisKaravitis/fanela-tv/releases/latest)** ·
[Install guide](docs/INSTALL.md) ·
[How to use Fanela](docs/TUTORIAL.md) ·
[What's new](CHANGELOG.md) ·
[Working on the code](CONTRIBUTING.md)

## What it does

- **All 14 Super League clubs**, plus each club's basketball team when it has one (EuroLeague, EuroCup, Basket
  League, Champions League).
- **Matches in order.** Whatever is live comes first, then the coming matches of both sports by date, then the latest
  results. The line under the header describes the match you're on.
- **The channel for every match.** The channel logo and its Magenta TV number come from the Greek TV guides. Before a
  guide lists a game, Fanela shows the channel that holds the home team's TV rights and marks it "Likely".
- **Live scores** every 30 seconds while the app is open, then the result and whether your club won, drew or lost.
- **Rows on the TV's home screen.** On TVs with the classic Android TV home screen, each club gets its own row of
  cards, drawn large enough to read from the sofa, so you can see the next game without opening the app.
- **Your club's colours.** Choose a favourite team and the app's accents, its logo and even its tile on the TV take
  on that club's kit.
- **English or Greek**, switchable at any time.
- No account, no ads, no tracking. See the [privacy policy](docs/PRIVACY.md).

## Screenshots

| | |
|---|---|
| ![Match with a confirmed channel](docs/screenshots/match.png) | ![Match with a likely channel](docs/screenshots/match-likely.png) |
| A match the TV guide has listed: channel and number. | Not listed yet: the channel that usually has it, and a way to get the app. |
| ![Choosing clubs](docs/screenshots/clubs.png) | ![Settings](docs/screenshots/settings.png) |
| Pick your clubs in Settings. | Favourite team, language, basketball and home-screen rows. |
| ![Fanela in Greek](docs/screenshots/home-greek.png) | ![Fanela row on the Android TV home screen](docs/screenshots/home-screen-row.png) |
| The whole app in Greek. | A club's row on the TV's home screen, with the Fanela logo. |

## Install

Fanela is not on Google Play. You install the APK yourself, which takes about five minutes the first time.

1. Download `fanela-1.0.4.apk` from the [Releases page](https://github.com/AlexisKaravitis/fanela-tv/releases/latest).
2. Copy it to the TV with **Send Files to TV** or a USB stick, or install it from a computer with
   `adb install fanela-1.0.4.apk`.
3. Open Fanela, choose a language, pick your clubs and press **Done**.

The [install guide](docs/INSTALL.md) has every step, including how to allow installs from unknown sources on Sony,
Philips, TCL and Google TV devices.

**Works on** Android TV and Google TV 8.0 or newer: Sony, Philips, TCL, Hisense and Xiaomi TVs, Chromecast with
Google TV, Google TV Streamer, Nvidia Shield, Xiaomi Mi Box.
**Doesn't work on** Samsung (Tizen), LG (webOS), Apple TV or Fire TV devices running Vega OS.

To update, install the new APK over the old one. Every release is signed with the same key, so your clubs and
settings stay.

## Where the data comes from

| What | Source |
|---|---|
| Football fixtures and live scores | ESPN |
| EuroLeague and EuroCup | EuroLeague |
| Basket League and Basketball Champions League | TheSportsDB |
| Channels, channel numbers and logos | MagentaTV channel list, Sport24 and MagentaTV TV guides, SofaScore |
| Crests | the feeds above, SofaScore for any that are missing |

None of these is an official, guaranteed service. If one of them changes, that part of the app can stop working until
Fanela is updated. Each source is handled on its own, so the rest keeps working, and a card says "Not updated" when
its data is more than 6 hours old.

## Limitations

- **The "Likely" channel** comes from a table of this season's TV rights (2026-27). It has to be updated every summer.
  Channels confirmed by a TV guide don't depend on it.
- **Home-screen rows** only appear on the classic Android TV home screen (for example Sony sets from before 2021,
  Nvidia Shield, Mi Box). Google TV and most operator launchers don't show rows from apps.
- **The TV tile in club colours** shows once the TV's home screen reloads; on some TVs that takes a restart.
- **Live basketball scores** are there for EuroLeague and EuroCup. For other basketball competitions the score
  appears after the game.
- Fanela is an independent app and isn't affiliated with any club, league or broadcaster. Club names and crests
  belong to their owners and are only shown to identify the teams.

## Releases

Every version, with its APK and what changed, is on the [Releases page](https://github.com/AlexisKaravitis/fanela-tv/releases).
The full history is in [CHANGELOG.md](CHANGELOG.md).

## Building from source

You need JDK 17 and the Android SDK with platform 36.

```bash
git clone https://github.com/AlexisKaravitis/fanela-tv.git
cd fanela-tv
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
| `app/src/main/res/raw` | Bundled data: the channel line-up and the Google Sans font licence |
| `app/src/debug` | Debug-only tools: card renders for every state and a data audit for every club |
| `app/src/test` | Unit tests |
| `design/make_brand.py` | Draws the shirt logo, the app icon and TV banner in every kit colour, and the store graphics |
| `store/graphics` | Icon, banner and feature graphic |
| `tools/demo_all.sh` | Renders every card for every club and language on a device and pulls the images |
| `docs/` | Install guide, tutorial, privacy policy, release notes |

## Privacy

Fanela has no accounts, ads or analytics. Your clubs and settings stay on the TV. The app only talks to the data
sources listed above. Full policy: [docs/PRIVACY.md](docs/PRIVACY.md).

## Licence

Copyright © 2026 Alexis Karavitis. All rights reserved. The code is shared here for reference; please ask before
reusing it. Google Sans is used under the SIL Open Font License.
