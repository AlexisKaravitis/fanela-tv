# Installing Fanela on your TV

Fanela isn't on Google Play, so you install the APK file yourself. It takes about five minutes and you only do the
setup once.

**Works on:** Android TV and Google TV 8.0 or newer. That covers Sony, Philips, TCL, Hisense and Xiaomi TVs with
Android TV or Google TV, plus Chromecast with Google TV, Google TV Streamer, Nvidia Shield and Xiaomi Mi Box.
**Doesn't work on:** Samsung (Tizen), LG (webOS), Apple TV, or newer Fire TV devices running Vega OS.

Not sure what your TV runs? On the TV go to Settings > Device preferences > About (or Settings > System > About). If
you see "Android TV OS", you're fine.

## Step 1: get the APK

Open the [Releases page](https://github.com/AlexisKaravitis/fanela-tv/releases/latest) and download
`fanela-1.1.2.apk` (about 700 KB) to your phone or computer.

## Step 2: allow installs from unknown sources

Android only installs APKs from apps you allow. You turn this on once, for the app you'll install Fanela with
(Send Files to TV, Downloader or a file manager).

- **Google TV:** Settings > System > About > Android TV OS build. Press **OK** on it 7 times until it says you're a
  developer. Then Settings > Apps > Security & restrictions > Unknown sources, and turn on the app.
- **Android TV (for example Sony sets from 2018 to 2020):** Settings > Device preferences > Security & restrictions >
  Unknown sources, and turn on the app.

## Step 3: put it on the TV

Pick whichever is easiest for you.

### From your phone (Send Files to TV)

1. Install **Send Files to TV** on your phone and on the TV (both from Google Play).
2. Allow it to install apps on the TV, as in step 2.
3. Open it on both devices, choose **Send** on the phone and pick `fanela-1.1.2.apk`.
4. On the TV, open the file and choose **Install**, then **Open**.

### From a USB stick

1. Copy `fanela-1.1.2.apk` to a USB stick and plug it into the TV.
2. Open it with a file manager on the TV (for example **File Commander** or **X-plore** from Google Play) and choose
   **Install**.

### With Downloader

No phone or computer needed. Install **Downloader** by AFTVnews from Google Play, allow it as in step 2, then type
this link and press **Go**:

```
github.com/AlexisKaravitis/fanela-tv/releases/latest/download/fanela-1.1.2.apk
```

When the download finishes, choose **Install**, then **Open**.

### From a computer (adb)

Turn on Developer options > USB debugging (or Network debugging) on the TV, then:

```bash
adb connect <TV IP address>:5555
adb install fanela-1.1.2.apk
```

The TV shows a prompt the first time a computer connects. Choose **Allow**.

## First start

1. Choose **English** or **Ελληνικά**.
2. Check the **Country**. It starts as the TV's own and decides where-to-watch: in Greece the exact channel, elsewhere
   the channels that hold each competition's rights there.
3. Pick a league from the row above the clubs, then press **OK** on the clubs you follow, in as many leagues as you
   like. The first club you pick sets the app's colours until you choose a favourite club in Settings.
4. On the classic Android TV home screen the TV asks to add each club's row: choose **Add**.
5. Press **Done** (top right, or under the clubs). Your clubs' matches appear in a few seconds.

The [tutorial](TUTORIAL.md) explains the cards, the match screen and Settings.

## Home-screen rows (Android TV only)

On TVs with the classic Android TV home screen (Sony before 2021, Nvidia Shield, Xiaomi Mi Box), Fanela can add a row
for each club to the home screen. The TV asks once per club, so choose **Add**.

Google TV doesn't show rows from apps. Open Fanela itself to see your fixtures.

To remove the rows: Settings > Home-screen rows > Off. You can also hide one on the TV: Home > Customise channels.

## Updating

Download the new APK from the Releases page and install it the same way. Android replaces the old version and keeps
your clubs and settings. Every release is signed with the same key, so you never need to uninstall first.

Updating from 1.0: your clubs, language, favourite club, rows and tile colour stay, and the country is set to Greece,
so everything looks as before. Change it in Settings > Country if you're elsewhere.

## If something isn't right

| What you see | What to do |
|---|---|
| "Couldn't load fixtures" | Check the TV's internet connection. Fanela tries again on its own every few minutes while it's open. |
| "App not installed" when installing | An APK signed with a different key is already installed (for example a test build). Uninstall that Fanela first. |
| The app's tile on the TV is still in the old colours | The TV draws app tiles when its home screen starts. Restart the TV, or wait for it to refresh. |
| A match time or channel looks wrong | Times and channels can change late, especially for Greek Cup and basketball games. Check your provider's guide; Fanela catches up within 15 minutes. |
| "Likely" under a match | In Greece: no TV guide has listed the game yet, so Fanela shows the channel that holds the home team's TV rights. Elsewhere: the channels that hold the competition's rights in your country this season, not the exact channel for one match. Check your provider's guide. |
| "Check your provider's guide" under a match | Fanela has no official list of that competition's TV partners for your country. |
| The wrong channels for where you live | Settings > Country. |
| No basketball games | Settings > Basketball must be On. Not every club has a basketball team that Fanela can follow. |
| A row disappeared from the home screen | Settings > Home-screen rows lists it as "not on the home screen" with an **Add** button. Or use Home > Customise channels. |
| "Get Magenta TV" instead of "Open Magenta TV" | The Magenta TV (or SPORT FM TV) app isn't on this TV. The button opens it on Google Play. |

## Uninstalling

Settings > Apps > Fanela > Uninstall. This removes everything, including the home-screen rows.
