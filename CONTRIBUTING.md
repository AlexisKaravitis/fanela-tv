# Working on Fanela

How to get the code, make a change, test it on a TV or emulator, and get it back into the repository.

## Getting the code

Anyone can clone the repository and build it. To push changes you need write access; otherwise fork it and open a
pull request.

```bash
git clone https://github.com/AlexisKaravitis/fanela-tv.git
cd fanela-tv
```

The first push or pull asks you to sign in to GitHub in the browser. Git for Windows remembers the login afterwards.
In GitHub Desktop or Android Studio, clone `AlexisKaravitis/fanela-tv`.

## Branches

| Branch | Purpose |
|---|---|
| `main` | What is released. Only changes that are ready go here. |

Every release has a tag (`v1.0.4`, `v1.1.0`, ...). `git checkout v1.1.0` gives you exactly the code of that release.

Do new work on its own branch, so `main` always matches the released app:

```bash
git switch main
git pull
git switch -c fix-kickoff-times
```

When you're done, open a pull request into `main`.

## Making a change

1. Edit the code.
2. Build and run the tests:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```
3. Try it on a TV or the emulator (see below).
4. Commit and push:
   ```bash
   git add -A
   git commit -m "Show the referee in the match panel"
   git push -u origin fix-kickoff-times
   ```

Commit messages describe what changes for the person using the app, in one line, for example "Keep NBA games that
have no winner yet".

## Running it

You need JDK 17 and the Android SDK with platform 36 (Android Studio installs both).

- **On a TV:** turn on Developer options > USB debugging (or Network debugging), then
  ```bash
  adb connect <TV IP address>:5555
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- **On the emulator:** create an Android TV device in Android Studio (Device Manager > Create device > TV), start it,
  and install the same APK. Use the classic Android TV image if you want to see the home-screen rows.

The debug build's package is `app.fanela.tv.debug`, so it installs next to the released app and doesn't touch your
real clubs or settings.

To see the app as a viewer elsewhere would, change Settings > Country. Settings > Language switches English and
Greek.

### Debug tools

Debug builds have a receiver for checks you'd otherwise do by hand:

```bash
# render every card state for one club and pull the images; COUNTRY draws them as seen from there
COUNTRY=GB CLUBS="359 382" sh tools/demo_all.sh <device> out en

# refresh everything now
adb shell am broadcast -n app.fanela.tv.debug/app.fanela.tv.DebugReceiver -a app.fanela.tv.REFRESH

# check a country's view of some leagues (names, sides, translations, duplicates, where to watch)
adb shell am broadcast -n app.fanela.tv.debug/app.fanela.tv.DebugReceiver -a app.fanela.tv.AUDIT \
  --es country GB --es leagues eng.1,uefa.champions --es max 6
adb exec-out run-as app.fanela.tv.debug cat files/audit_GB.txt

# check every Super League club as before (files/audit.txt)
adb shell am broadcast -n app.fanela.tv.debug/app.fanela.tv.DebugReceiver -a app.fanela.tv.AUDIT
```

The `FOLLOW` action in the same receiver sets followed clubs by id. It doesn't save a club's league, so for clubs
outside the Super League follow them in Settings instead.

## The broadcaster data

`app/src/main/res/raw/broadcasters.json` holds, per competition, the countries and their TV partners for the season,
the official pages they came from (`sources`), the date they were last checked (`checked`), and for leagues that
publish no list, a `none` note saying so. `broadcasters_wikipedia.json` fills empty cells from Wikipedia and is
rebuilt with `tools/wiki_broadcasters.py`. An official row always wins over Wikipedia.

When you change the table:

- Only use the competition's own page or a broadcaster's own announcement, and add it to `sources`.
- Set `checked` to the day you checked.
- `DataTest` must pass: it rejects country codes that aren't ISO, empty names, a country listed twice, missing dates
  and sources that aren't https.

Each table row names the official page it came from, and `res/raw/broadcasters.json` carries the date it was checked.

## Club names in Greek

`I18n.java` gives every club in the leagues on offer a Greek name, and long ones a short form in `SHORT`. `NamesTest`
checks this against a snapshot of every league's clubs (`app/src/test/resources/league_clubs.json`). When a promoted
club appears, add its Greek name and refresh the snapshot. MLS, Brazilian, Argentine and Saudi clubs stay in Latin
letters on purpose.

## Before merging into main

- `./gradlew testDebugUnitTest lintRelease` passes.
- The audit above shows no problems for at least Greece and one other country.
- On a TV or the emulator, with the remote: first start, following and unfollowing clubs in two leagues, switching
  language and country, opening a match and going back, and turning Wi-Fi off (the app should say it's offline and
  still show saved fixtures).
- Greece looks exactly as before: render Greek clubs' cards with `tools/demo_all.sh` before and after the change
  and compare them.
- Anything the user sees exists in both English and Greek (`I18n.java`).

## How the code is organised

Plain Java with framework Views. There are no third-party libraries, which keeps the APK small and means nothing
breaks when a library changes.

- **HomeActivity:** the main screen. One row of cards per club, the match panel, and live polling only while it's
  visible.
- **SettingsActivity:** first-run setup, leagues and clubs, language, favourite team, country, basketball, home-screen
  rows, About.
- **Leagues:** the leagues on offer, their order and the viewer's country.
- **Teams:** clubs per league, follows and the favourite team.
- **Engine:** how often each club is refreshed, the live tick, and states that depend on the clock.
- **Sources:** ESPN, EuroLeague and TheSportsDB.
- **Where / Watch:** chooses a where-to-watch source per country.
  - **GreeceProvider:** the Greek TV guides, SofaScore and the clubs' TV rights, as in Fanela.
  - **SeasonTableProvider:** `broadcasters.json`, then the Wikipedia layer for gaps.
  - **RoninProvider:** per-match listings; off until a `roninKey` is set in `keystore.properties`.
  - **TvGuideProvider:** the TV's own programme guide; debug builds only, never in a release.
- **Guide, Channels, Crests, Sofa:** the Greek guides, channel logos and numbers, missing crests, SofaScore.
- **Store:** merging the sources and saving them.
- **Cards:** choosing which matches to show and drawing the cards.
- **Logo, Brand:** the shirt logo in the favourite team's colours, and the app's tile and banner on the TV in the
  nearest kit colour (one launcher entry per colour, started through LaunchActivity).
- **HomeRows, UpdateJob, Updater:** the Android TV home-screen rows and the 15-minute job behind them.
- **I18n:** all text in English and Greek, and the clubs' Greek names.

## Secrets

Release builds are signed with `fanela-release.jks`, using the passwords in `keystore.properties` (alias `fanela`).
Neither file is in the repository, and `.gitignore` keeps it that way. Never commit them, and keep an offline copy of
both: without the key, no update can be installed over Fanela and everyone would have to uninstall it first. Without them, `assembleRelease` still works but produces an unsigned APK,
which is fine for testing.

Releases and the yearly upkeep of the broadcaster table are covered in [docs/RELEASE.md](docs/RELEASE.md).
