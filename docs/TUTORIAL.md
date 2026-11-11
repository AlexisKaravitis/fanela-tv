# Using Fanela

This guide walks through Fanela 1.1.2 with the TV remote. If it isn't on your TV yet, start with the
[install guide](INSTALL.md).

You only need four buttons: the arrows, **OK**, **Back** and **Home**.

## 1. First start

The first time you open Fanela it asks three things, from the top.

1. **Language.** Choose English or Ελληνικά.
2. **Country.** It starts as the TV's own country. It decides where-to-watch (see [Reading a card](#reading-a-card))
   and which leagues come first. In a country Fanela has no TV partners for, you still get fixtures and scores.
3. **Your clubs.** A row of leagues sits above the clubs, football first, then basketball: your own country's league,
   the three UEFA cups, then the rest. Rest on a league for a moment, or press **OK**, to see its clubs. Press **OK**
   on each club you follow; a tick and a coloured ring show it's selected, and **OK** again removes it. The badge on
   a league is how many of its clubs you follow.

![First start](screenshots/first-start.png)

On TVs with the classic Android TV home screen, the TV asks whether to add each club's row to the home screen. Choose
**Add** (more about rows in section 5). Then press **Done**, at the top right or under the clubs, and your fixtures
appear within a few seconds.

If you're updating from Fanela 1.0, nothing to do: your clubs, language, favourite club and rows stay as they were,
and the country is set to Greece.

## 2. The main screen

Every club you follow gets a row. A row starts with the club's live or next match and, if it has a basketball team,
its next basketball game, all in date order. After those come the latest results. Use **left** and **right** to move
along a row, and **up** and **down** to switch clubs.

![Main screen](screenshots/home.png)

The two lines under the header always describe the card you're on: the teams, the competition, the day and time, and
the channel. Press **up** from the first row to reach **Settings**.

### Reading a card

- **Top left:** the competition (Super League, Premier League, EuroLeague and so on).
- **Top right:** the day. A white label means today or tomorrow; later dates are in grey.
- **Middle:** both crests, with the kick-off time between them. During a match this becomes the live score and the
  minute or quarter. Afterwards it shows the final score, plus **Win**, **Draw** or **Loss** for your club.
- **Bottom:** where to watch. In Greece:
  - **A channel logo with a number** (for example Novasports Prime 318): a TV guide has listed the match, and the
    number is the channel on Magenta TV.
  - **"Likely" and a logo:** no guide has listed it yet. Fanela shows the channel that holds the home team's TV
    rights. Check your provider's guide closer to the day.
  - **"Channel: to be announced":** nothing is known yet.

  In other countries:
  - **"Likely" and one or two names** (for example Sky Sports and TNT Sports in the UK): the channels or services
    that hold this competition's rights in your country this season. One match can still be elsewhere, so your
    provider's guide has the last word.
  - **"Check your provider's guide":** Fanela has no official list of this competition's TV partners for your
    country. Some leagues, such as the Super League and the Saudi Pro League, don't publish one.
- **"⚠ Not updated"** at the top of a card means Fanela couldn't refresh that match for more than 6 hours, usually
  because the TV was offline. The card shows the last information it had.

The header shows when the data was last updated. While Fanela is open it checks live matches every 30 seconds.

## 3. Match details

Press **OK** on any card to see the match on its own.

![A match with a confirmed channel](screenshots/match.png)

The panel gives the full date, how soon the match is ("in 5 days", "in 3 hours"), and the channel with its number.
If the channel's app (Magenta TV or SPORT FM TV) is installed, **Open Magenta TV** takes you straight there. If it
isn't, **Get Magenta TV** opens its page on Google Play. Press **Back** or **Close** to return.

When the channel is only "likely", the panel says so and explains why:

![A match with a likely channel](screenshots/match-likely.png)

Outside Greece the panel shows the season's rights holders and where that comes from, or says plainly that Fanela
has no list for your country:

![A Premier League match seen from the UK](screenshots/abroad-match.png)

![A match with no known TV partner](screenshots/check-guide.png)

## 4. Settings

From the main screen press **up** to reach **Settings** in the top right corner, then **OK**.

![Settings](screenshots/settings.png)

- **Your clubs.** The same leagues and grid as the first start. Follow or unfollow clubs at any time. Changes are
  saved straight away.
- **Language.** Switches between English and Greek. Club names switch too, in every league.
- **Favourite club.** The club whose colours Fanela wears: the accents, the shirt in the logo, and the app's tile on
  the TV. Until you choose one, it's the first club you follow. The tile changes after you leave the app; on some TVs
  it shows once the home screen reloads.
- **Country.** Sets which TV partners you see and which leagues come first. The TV's own country is first in the
  list, the current one has a tick.

  ![Choosing a country](screenshots/country.png)

- **Basketball.** On by default. Turn it off if you only want football.
- **Home-screen rows.** Puts a row for each club on the TV's home screen (see below). If the TV is hiding one of
  them, it's listed right under this switch with an **Add** button.
- **About.** Where the data comes from (with the date each competition's TV partners were last checked), and the
  privacy policy.

  ![Sources and credits](screenshots/sources.png)

- **Last updated.** When the data was last refreshed, and which club, if any, hasn't updated for a while.

## 5. Rows on the TV home screen

On TVs with the classic Android TV home screen, Fanela can add one row per club to the home screen. The row has the
Fanela logo in your favourite club's colours at its side, and cards drawn larger so they read from across the room.

![Fanela row on the home screen](screenshots/home-screen-row.png)

The rows update every 15 minutes when a match is close, and less often otherwise. Press **OK** on a card to open that
match in Fanela.

If you hid a row by mistake, go to Settings > Home-screen rows and press **Add** next to it. You can also manage rows
from the TV itself: Home > Customise channels.

Google TV and most operator launchers don't show rows from apps. On those, open Fanela to see your fixtures.

## 6. In Greek

Everything, including team names, competitions and dates, switches to Greek when you choose Ελληνικά. Clubs from
other leagues have their Greek names too ("Άρσεναλ", "Μπάγερν", "Γκλάντμπαχ"); MLS, Brazilian, Argentine and Saudi
clubs keep their own.

![Fanela in Greek](screenshots/home-greek.png)

![The Bundesliga in Greek](screenshots/greek-clubs.png)

## 7. Updating

Fanela doesn't update itself. When a new version is out, download its APK from the
[Releases page](https://github.com/AlexisKaravitis/fanela-tv/releases) and install it the same way you installed the
first one. Android installs it over the old version and keeps your clubs and settings.

## Something not right?

The [install guide](INSTALL.md#if-something-isnt-right) has a table of common problems, such as fixtures not loading,
a channel that looks wrong, or a missing row.
