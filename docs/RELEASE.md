# Releasing Fanela

## The signing key: keep an offline copy

`fanela-release.jks` and `keystore.properties` (project root) sign every release of Fanela. Android only accepts an
update signed with the **same key**. If you lose it, everyone has to uninstall Fanela and install it again.

- Copy both files somewhere safe that isn't this PC, such as a password manager or an encrypted USB stick.
- Never put them in git. `.gitignore` already excludes them.
- The certificate is `CN=Fanela`, RSA 4096. Its SHA-256 fingerprint is
  `5d:e9:79:ca:58:8c:87:4e:82:16:43:d8:4d:f8:ee:18:2d:48:74:69:05:42:40:49:0e:c1:4c:50:2f:df:e3:86`.
  `apksigner verify --print-certs` on a release APK must show the same.

## Making a release

1. Raise `versionCode` (+1) and `versionName` in `app/build.gradle`.
2. Build and check:
   ```bash
   ./gradlew testDebugUnitTest lintRelease assembleRelease
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```
   Rename the APK to `fanela-<version>.apk`, for example `fanela-1.1.0.apk`.
3. Check it on a TV or the emulator: install it fresh, then install it again over itself and over the previous
   version, open it, and confirm clubs and settings survived.
4. Add the version to `CHANGELOG.md` and attach the APK to a GitHub release tagged `v<version>`.

Fanela has no in-app updates (`UPDATE_REPO` is empty in `app/build.gradle`). People install each new APK over the old
one.

## Upkeep (every August and January)

TV rights change at the start of each season and sometimes in the winter.

- **`res/raw/broadcasters.json`:** open each competition's `sources`, compare, update the countries, and set `checked`
  to the day. Leagues marked `none` publish no list for other countries; look again in case one appears.
- **`res/raw/broadcasters_wikipedia.json`:** rebuild with `python tools/wiki_broadcasters.py` and look over the diff.
- **Greek TV rights** (`Cards.java`: `FOOTBALL_RIGHTS`, `GBL_RIGHTS`, `EURO_RIGHTS_UNTIL`, `SL_NOVA`, `SL_SPORTFM`),
  the same as in Fanela.
- **Promoted clubs** appear on their own (from ESPN). Give them a Greek name in `I18n.java`, a short form if the name is
  long, and refresh `app/src/test/resources/league_clubs.json` so `NamesTest` covers them.

## Checks before a release

- `./gradlew testDebugUnitTest lintRelease` passes.
- With a debug build on a device:
  - `COUNTRY=GB CLUBS="359 86 nba13" sh tools/demo_all.sh <device> out en el` renders every card state as seen from
    the UK. Do the same for Greece without `COUNTRY`. Look through the images.
  - The audit (see CONTRIBUTING.md) for Greece and at least one other country shows no problems.
- On a TV or the emulator with the remote, in at least Greece, the UK and the US, in English and Greek:
  - First-run setup.
  - Following and unfollowing clubs in two leagues.
  - Switching the language and the country.
  - Choosing a favourite team: the app stays open, and the tile changes after leaving it.
  - Opening a match, then pressing Back.
  - No internet (Wi-Fi off): the screen says so, and saved fixtures still show.
