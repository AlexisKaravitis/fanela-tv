# Releasing Fanela

## The signing key: back it up now

`fanela-release.jks` and `keystore.properties` (project root) sign every release. Android only accepts an update
signed with the **same key**. If you lose the key, everyone has to uninstall Fanela and install it again.

- Copy both files somewhere safe that isn't this PC, such as a password manager or an encrypted USB stick.
- Never put them in git. `.gitignore` already excludes them.

## Making a release

1. Raise `versionCode` (+1) and `versionName` (for example `1.1.0`) in `app/build.gradle`.
2. Build and check:
   ```
   ./gradlew testDebugUnitTest lintRelease assembleRelease
   ```
   The APK is `app/build/outputs/apk/release/app-release.apk`. Rename it to `fanela-1.1.0.apk`.
3. Check it on a TV: install over the previous version, open it, and confirm your clubs and settings survived.
4. Publish it as below.

## In-app updates (one-time setup)

Fanela can update itself from GitHub releases.

1. Create a GitHub repository, for example `yourname/fanela`. It can hold just the releases.
2. In `app/build.gradle`, set `buildConfigField 'String', 'UPDATE_REPO', '"yourname/fanela"'` and build.
3. For each release: on GitHub, Releases > Draft a new release, tag `v1.1.0`, and attach the `.apk`. Fanela checks
   twice a day and offers it under Settings > App updates.

Fanela installs an update only if it is signed with the same key, so nobody else can push one.

The release page's APK link is also the link people type into Downloader (see INSTALL.md). A short link (for
example from bit.ly) is easier to type with a remote.

## Yearly upkeep (every summer)

- **TV rights table:** `Cards.java` has `FOOTBALL_RIGHTS`, `GBL_RIGHTS`, `EURO_RIGHTS_UNTIL`, `SL_NOVA` and
  `SL_SPORTFM`.
  - Update them when the new season's broadcast deals are known. They are keyed by ESPN team id, and every other home
    team falls back to Magenta Sport.
  - Without the update, unlisted games show "Channel: to be announced" until the guides list them.
- **Promoted clubs:** these appear on their own (from ESPN). Add their Greek name to `Teams.GR_BY_ID`, and their
  colours and short name to `Badges`.
- **EuroLeague and EuroCup:** membership is detected each season. New basketball clubs go in `Teams.BASKET`.

## Checks before a release

- `./gradlew testDebugUnitTest lintRelease` passes.
- With a debug build on a device:
  - `sh tools/demo_all.sh <device> out en el` renders every card state for every club. Look through the images.
  - `adb shell am broadcast -n app.fanela.tv.debug/app.fanela.tv.DebugReceiver -a app.fanela.tv.AUDIT` writes
    `files/audit.txt`. It checks every club's matches: names, sides, translations, duplicates and channels. Read it
    with `adb exec-out run-as app.fanela.tv.debug cat files/audit.txt`.
- On a TV with the remote:
  - First-run setup.
  - Following and unfollowing a club.
  - Switching the language.
  - Opening a match, then pressing Back.
  - No internet (Wi-Fi off): the screen says so, and saved fixtures still show.
