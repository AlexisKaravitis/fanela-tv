# Changelog

## 1.1.2 (11 November 2026)

- The match screen has room for longer names. Its lines break between the teams or after a "·", never inside a
  club's name or "Magenta TV".
- Karditsa reads "Karditsa" (and "Καρδίτσα" in Greek) on every card, however the data source spells it.
- New screenshots in the guides, and the "Likely" wording used for TV partners outside Greece.

## 1.1.1 (11 November 2026)

A finishing pass: new match cards, a faster and lighter app, and many small fixes.

**Cards**
- New match cards, designed first for the TV's home screen: the competition is back at the top left, team names are
  calmer and always the same size, long names wrap neatly, and the card stands out on a dark home screen.
- One way of showing where to watch everywhere: "Novasports 4 · ch. 325 on Magenta TV", or "Likely" with the channel
  logo when it's a guess. WIN, DRAW and LOSS line up with the channel line; postponed matches say so in amber.
- Three-digit basketball scores stay large; live cards show the clock ("90'+3'", "Q3") instead of a second LIVE label.

**Better**
- The home screen appears at once with the cards already on the TV, and new ones swap in as they're drawn. Placeholder
  cards show while a club loads.
- One type scale and one focus motion across the app; Settings choices (language, country, favourite club) open as
  lists; Privacy and Sources have a Close button; first start has one Done button, or "Skip for now".
- Basketball-only clubs read "Real Madrid · Basketball"; Spanish, Italian, Turkish, German, French and Adriatic
  basketball games carry their own league names instead of "Basket League".
- Clearer wording: "Updated" vs "Updating…", a short offline line, and Greek buttons that read naturally.

**Lighter**
- Far fewer downloads and redraws during live matches, fewer refreshes when no match is near, and much less memory
  used while drawing cards. Files of clubs you stop following are cleaned up.
- The app no longer asks for the permission to install updates, since it doesn't update itself.

## 1.1.0 (11 November 2026)

Fanela now also follows clubs and leagues outside Greece. In Greece it works and looks exactly as before. Updating
from 1.0 keeps your clubs, language, favourite club, rows and tile colour, and sets the country to Greece.
Version 1.0.5 was never released; its fix is in this one.

**New**
- **Clubs from 18 more competitions**, picked league by league: the Premier League, LaLiga, Serie A, Bundesliga,
  Ligue 1, Liga Portugal, Eredivisie, Süper Lig, MLS, Brasileirão, Argentine Primera, Saudi Pro League, the three UEFA
  cups, the NBA, the EuroLeague and the EuroCup. Cup and European games come with the club.
- **Country** in Settings and at the first start. It starts as the TV's own and decides where-to-watch and which
  leagues come first.
- **Where to watch outside Greece:** "Usually on Sky Sports / TNT Sports" and the like, from the leagues', UEFA's,
  the NBA's and EuroLeague's own broadcaster lists for 14 countries, with Wikipedia's lists filling gaps. When no
  official list exists, the card says "Check your provider's guide".
- **Sources and credits** lists every source and the date each competition's TV partners were last checked.
- **Greek names for the clubs of every league**, so a Greek grid never mixes alphabets (MLS, Brazilian, Argentine and
  Saudi clubs keep their own names).
- Viewers in the US and Canada get "Sat, Nov 21" and "7:30 PM".

**Better**
- Leagues sit in a row above the clubs, football then basketball; resting on one shows its clubs, and a badge counts
  the clubs you follow in it.
- The first start asks for language and country first, and has a Done button at the top.
- The country picker lists the TV's own country first, in three columns.
- Club tiles never split a name mid-word and sort by the name they show; short names for NBA and MLS clubs.
- "Favourite club" rather than "Favourite team".

**Fixed**
- Club rows scrolling up slide under the header instead of drawing over it (1.0.5's fix).
- On a first start the focus is on the screen straight away, so the remote works at once.
- Upcoming NBA games are no longer dropped.

## 1.0.4 (10 November 2026)

The first release since 1.0.0. Versions 1.0.1 to 1.0.3 were test builds.

**New**
- **Favourite team** in Settings. The app's accents, the shirt in its logo and its tile and banner on the TV take on
  that club's colours, in one of eleven kits.
- The cards on the TV's home-screen rows have their own, larger layout, so they read from the sofa. The rows show
  the Fanela logo, so it's clear which app they come from.
- The match screen says how soon a match is ("in 5 days") and, when the channel's app isn't installed, offers
  **Get Magenta TV**.

**Better**
- Matches are in date order across football and basketball, with anything live first, in the app and on the TV rows.
- One details line under the header describes the card you're on, so two club rows fit on screen.
- Short club names on cards, the same for both sports ("AEK", not "AEK Athens" on one card and "AEK" on the next).
- Channels read the same everywhere: "Novasports Prime 318 · Magenta TV", or "Likely: Magenta Sport · Magenta TV".
- Date labels are white only for today and tomorrow, and "pm" is smaller, so the time stays the main thing.
- Settings: values are grey until their row has focus, hidden rows sit under the Home-screen rows switch with an
  **Add** button, and Last updated is one line.
- The shirt logo's cuffs run straight across the sleeves, and dark trims like AEK's black keep the sleeve's outline.

**Fixed**
- Choosing or changing the favourite club no longer closes the app.
- Postponed and TBA matches show their date on the TV rows.
- Focus stays where it was after the TV's "add this row?" prompt.


## 1.0.0 (7 November 2026)

First release.

- All 14 Super League clubs, with their basketball teams in the EuroLeague, EuroCup, Basket League and Basketball
  Champions League.
- Fixtures, live scores every 30 seconds while the app is open, and results with win, draw or loss.
- The channel for each match, with its logo and Magenta TV number from the Greek TV guides, or the likely channel
  before a guide lists it.
- Official club crests and channel logos.
- Rows on the classic Android TV home screen, one per club.
- English and Greek.
- The app takes on the colours of your first club.
- Google Sans throughout.
