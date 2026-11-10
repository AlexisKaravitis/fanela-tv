# Working on Fanela

How to get the code, make a change on a branch, test it on a TV or emulator, and get it back into the repository.

## Getting the code

Anyone can clone the repository and build it. To push changes you need write access; otherwise fork it and open a
pull request.

**Command line**

```bash
git clone https://github.com/AlexisKaravitis/fanela-tv.git
cd fanela-tv
```

The first push or pull asks you to sign in to GitHub in the browser. Git for Windows remembers the login afterwards.

**GitHub Desktop**

File > Clone repository > GitHub.com, pick `AlexisKaravitis/fanela-tv` and choose a folder. Branches, commits and pushes
are all buttons in the app.

**Android Studio**

File > Open and select the `fanela-tv` folder (the one with `settings.gradle`). Android Studio sets up Gradle on its own.
You can also clone straight from Android Studio: File > New > Project from Version Control.

## Branches

`main` is what is released. Do new work on a branch of its own and merge it when it's ready.

Every release has a tag (`v1.0.4`, ...). `git checkout v1.0.4` gives you exactly the code of that release.

Do new work on its own branch, so `main` always matches the released app:

```bash
git switch main
git pull
git switch -c fix-kickoff-times
```

To carry on with an existing branch:

```bash
git fetch
git switch fix-kickoff-times
git pull
```

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
5. On GitHub, open a pull request from your branch into `main`, look over the changes and merge it.

Small edits to the documentation can be made on github.com directly: open the file, press the pencil icon, and
commit to a new branch.

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
real clubs or settings. The app's name and icon are the same, so check which one you open.

### Debug tools

Debug builds have a receiver for checks you'd otherwise do by hand:

```bash
# render every card state for one club and pull the images
sh tools/demo_all.sh <device> out en el

# refresh everything now
adb shell am broadcast -n app.fanela.tv.debug/app.fanela.tv.DebugReceiver -a app.fanela.tv.REFRESH

# check every Super League club's matches (names, sides, translations, duplicates, channels)
adb shell am broadcast -n app.fanela.tv.debug/app.fanela.tv.DebugReceiver -a app.fanela.tv.AUDIT
adb exec-out run-as app.fanela.tv.debug cat files/audit.txt
```

## Before merging into main

- `./gradlew testDebugUnitTest lintRelease` passes.
- The audit above shows no problems.
- On a TV, with the remote: first start, following and unfollowing a club, switching language, opening a match and
  going back, and turning Wi-Fi off (the app should say it's offline and still show saved fixtures).
- Anything the user sees exists in both English and Greek (`I18n.java`).

## How the code is organised

Plain Java with framework Views. There are no third-party libraries, which keeps the APK under 400 KB and means
nothing breaks when a library changes.

- **HomeActivity:** the main screen. One row of cards per club, the match panel, and live polling only while it's
  visible.
- **SettingsActivity:** first-run setup, the clubs grid, language, basketball, home-screen rows, About.
- **Engine:** how often each club is refreshed, the live tick, and states that depend on the clock.
- **Sources:** ESPN, EuroLeague and TheSportsDB.
- **Guide:** the Sport24 and MagentaTV TV guides.
- **Channels, Crests, Sofa:** channel logos and numbers, crests the feeds leave empty, and SofaScore as a third channel
  source.
- **Store:** merging the sources and saving them.
- **Cards:** choosing which matches to show and drawing the cards.
- **Badges:** drawn club badges, shown while a crest is still downloading.
- **HomeRows, UpdateJob, Updater:** the Android TV home-screen rows and the 15-minute job behind them.
- **AppUpdate:** in-app updates from GitHub releases (switched off; see docs/RELEASE.md).
- **Logo, Brand, LaunchActivity:** the shirt logo in the favourite club's colours, the TV tile in the nearest of eleven
  kit colours (one launcher entry each), and the tiny screen those entries open, so switching tiles never closes the
  app.
- **I18n, Teams:** all text in English and Greek, and the list of clubs.

## Secrets

Release builds are signed with `fanela-release.jks`, using the passwords in `keystore.properties`. Neither file is in
the repository, and `.gitignore` keeps it that way. Never commit them. Without them, `assembleRelease` still works but
produces an unsigned APK, which is fine for testing.

Releases, the signing key and the yearly upkeep of the TV rights table are covered in [docs/RELEASE.md](docs/RELEASE.md).
