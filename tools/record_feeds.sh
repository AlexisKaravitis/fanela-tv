#!/usr/bin/env bash
# Records ESPN's live F1 and UFC feeds as timestamped JSON files, so Fanela's live parsing (F1Feed.live,
# UfcFeed.live) can be checked against real frames: does status.period count laps, does the order move, does a
# bout show "in" with its round and clock (F1-UFC-ARCHITECTURE §3.5, §9.1). A dev tool, not part of the app.
#
# Needs bash and curl: Git Bash on Windows, or any Linux / macOS shell. Polite: one request per feed every 30 s
# (never faster), the UFC fight card every 5 min, User-Agent "fanela-recorder", only inside the event's windows.
# A frame identical to the previous one of the same feed is not saved again (index.tsv still logs the poll).
#
# usage:
#   bash tools/record_feeds.sh ufc334   [out dir]   UFC 334, MSG: 2026-11-14 21:45Z to 2026-11-15 08:00Z
#                                                   (early prelims 22:00Z = Sun 00:00 Athens; main card 02:00Z = 04:00)
#   bash tools/record_feeds.sh lasvegas [out dir]   Las Vegas GP: each session from 15 min before its start
#                                                   FP1 Thu 20 Nov 00:30Z (02:30 Athens), FP2 04:00Z (06:00),
#                                                   FP3 Fri 21 Nov 00:30Z, qualifying 04:00Z, race Sun 22 Nov 04:00Z (06:00)
#   bash tools/record_feeds.sh ufc <event id> <from> <until> [out dir]
#   bash tools/record_feeds.sh f1  <event id> <from> <until> [out dir] [session id]
#       from, until: UTC times like 2026-11-21T17:45Z, or "now"; until may be "+6h" (hours after from).
#       Event ids: ESPN's (the number in espn.com/mma/fightcenter/_/id/600061400, or in the F1 calendar's $ref).
#   Started early, the script waits for each window (keep the computer awake: no sleep, on mains power).
#   Ctrl-C stops it; what was saved stays.
#
# Other events worth recording (§3.5), with the generic modes:
#   UFC Fight Night 21 Nov (600061665), Qatar GP 27-29 Nov (600057450), Abu Dhabi GP 4-6 Dec (600057451),
#   UFC Fight Night 5 Dec (600062700), UFC 335 13 Dec (600061401). Take the session / segment times from the
#   scoreboard or the fight card first (curl the URLs below) and start each window 15 min early.
#
# What is polled:
#   both sports, every 30 s:  site.api.espn.com/apis/site/v2/sports/<racing/f1|mma/ufc>/scoreboard
#                             and the same with ?dates=yyyyMMdd: the US Eastern day ESPN files the session under (F1:
#                             of the session; UFC: of the event's first segment), what the app asks for live. The
#                             day is worked out as UTC-5 (US winter time); set DATES for an event in US summer time.
#   UFC, every 5 min:         site.web.api.espn.com/apis/common/v3/sports/mma/ufc/fightcenter/<event>  (methods)
#   F1 with a session id,     sports.core.api.espn.com/v2/sports/racing/leagues/f1/events/<event>/competitions/
#     every 60 s:             <session>/status  (its "flag" may show red flags and safety cars)
# Env: EVERY=30 (seconds, at least 30), DATES="20261114" (override the scoreboard day), QUIET=1.
#
# Output (default $HOME/fanela-recordings/<name>):
#   <feed>/<UTC yyyymmddTHHMMSSZ>.json, one folder per feed (board, board_20261114, fightcenter, status_401839137),
#   index.tsv: time, feed, HTTP code, bytes, saved file (or "same" / "error").
# Selected frames become unit-test fixtures in app/src/test/resources/f1ufc/live/ (F1FeedTest, UfcLiveTest).
set -u

UA="fanela-recorder"
SITE="https://site.api.espn.com/apis/site/v2/sports"
FIGHTCENTER="https://site.web.api.espn.com/apis/common/v3/sports/mma/ufc/fightcenter"
CORE="https://sports.core.api.espn.com/v2/sports/racing/leagues/f1/events"
EVERY=${EVERY:-30}
[ "$EVERY" -lt 30 ] 2>/dev/null && EVERY=30

die() { echo "record_feeds: $*" >&2; exit 2; }
say() { [ -n "${QUIET:-}" ] || echo "$(date -u +%H:%M:%SZ) $*"; }
command -v curl >/dev/null 2>&1 || die "curl is needed"

# ---------- time ----------
# epoch seconds of an ISO UTC time ("2026-11-14T21:45Z"), "now", or "+6h" after $2
epoch() {
  case "$1" in
    now) date -u +%s ;;
    +*h) echo $(( $2 + ${1:1:${#1}-2} * 3600 )) ;;
    *) date -u -d "$1" +%s 2>/dev/null || date -j -u -f "%Y-%m-%dT%H:%MZ" "$1" +%s 2>/dev/null || die "bad time: $1" ;;
  esac
}
day_utc() { date -u -d "@$1" +%Y%m%d 2>/dev/null || date -u -r "$1" +%Y%m%d; }
stamp() { date -u +%Y%m%dT%H%M%SZ; }
hm() { date -u -d "@$1" "+%a %d %b %H:%MZ" 2>/dev/null || date -u -r "$1" "+%a %d %b %H:%MZ"; }

# ---------- one poll of one feed ----------
SAVED=0; SAME=0; FAILED=0
poll() {  # poll <feed name> <url>
  local feed=$1 url=$2 dir="$OUT/$1" tmp="$OUT/.tmp_$1" code bytes last file
  mkdir -p "$dir"
  code=$(curl -sS -m 25 -A "$UA" -H "Accept: application/json" -o "$tmp" -w '%{http_code}' "$url" 2>/dev/null) || code="000"
  bytes=$(wc -c < "$tmp" 2>/dev/null | tr -d ' ' || echo 0)
  if [ "$code" != "200" ] || [ "${bytes:-0}" -eq 0 ]; then
    printf '%s\t%s\t%s\t%s\terror\n' "$(stamp)" "$feed" "$code" "${bytes:-0}" >> "$OUT/index.tsv"
    FAILED=$((FAILED + 1)); rm -f "$tmp"; say "$feed HTTP $code"; return
  fi
  last=$(ls "$dir" 2>/dev/null | tail -n 1)
  if [ -n "$last" ] && cmp -s "$tmp" "$dir/$last"; then
    printf '%s\t%s\t%s\t%s\tsame\n' "$(stamp)" "$feed" "$code" "$bytes" >> "$OUT/index.tsv"
    SAME=$((SAME + 1)); rm -f "$tmp"; return
  fi
  file="$(stamp).json"
  mv "$tmp" "$dir/$file"
  printf '%s\t%s\t%s\t%s\t%s/%s\n' "$(stamp)" "$feed" "$code" "$bytes" "$feed" "$file" >> "$OUT/index.tsv"
  SAVED=$((SAVED + 1)); say "$feed saved $file ($bytes bytes)"
}

# ---------- one window ----------
# window <sport> <event> <from epoch> <until epoch> <scoreboard day> [session id]
window() {
  local sport=$1 event=$2 from=$3 until=$4 day=$5 session=${6:-} now n=0 path
  [ "$sport" = f1 ] && path="racing/f1" || path="mma/ufc"
  now=$(date -u +%s)
  if [ "$now" -ge "$until" ]; then say "window $(hm "$from") - $(hm "$until") is over: skipped"; return; fi
  if [ "$now" -lt "$from" ]; then
    say "waiting for $(hm "$from") ($(( (from - now) / 60 )) min)"
    while [ "$(date -u +%s)" -lt "$from" ]; do sleep 20; done
  fi
  say "recording $sport $event ${session:+session $session }until $(hm "$until") into $OUT"
  while [ "$(date -u +%s)" -lt "$until" ]; do
    local t0; t0=$(date -u +%s)
    poll board "$SITE/$path/scoreboard"
    poll "board_$day" "$SITE/$path/scoreboard?dates=$day"
    if [ "$sport" = ufc ] && [ $((n % 10)) -eq 0 ]; then poll fightcenter "$FIGHTCENTER/$event?region=us&lang=en"; fi
    if [ "$sport" = f1 ] && [ -n "$session" ] && [ $((n % 2)) -eq 0 ]; then
      poll "status_$session" "$CORE/$event/competitions/$session/status?lang=en&region=us"
    fi
    n=$((n + 1))
    local left=$(( EVERY - ($(date -u +%s) - t0) ))
    [ "$left" -gt 0 ] && sleep "$left"
  done
  say "window done: $SAVED saved, $SAME unchanged, $FAILED failed"
}

finish() { echo; say "stopped: $SAVED saved, $SAME unchanged, $FAILED failed, in $OUT"; exit 0; }
trap finish INT TERM

# ---------- modes ----------
MODE=${1:-}
case "$MODE" in
  ufc334)
    OUT=${2:-$HOME/fanela-recordings/ufc334}; mkdir -p "$OUT" || die "can't write $OUT"
    window ufc 600061400 "$(epoch 2026-11-14T21:45Z)" "$(epoch 2026-11-15T08:00Z)" "${DATES:-20261114}"
    ;;
  lasvegas)
    OUT=${2:-$HOME/fanela-recordings/lasvegas}; mkdir -p "$OUT" || die "can't write $OUT"
    # event 600057449; sessions FP1 401839133, FP2 401839134, FP3 401839135, Qual 401839136, Race 401839137
    for w in "2026-11-20T00:15Z 2026-11-20T02:00Z 401839133" "2026-11-20T03:45Z 2026-11-20T05:30Z 401839134" \
             "2026-11-21T00:15Z 2026-11-21T02:00Z 401839135" "2026-11-21T03:45Z 2026-11-21T05:45Z 401839136" \
             "2026-11-22T03:45Z 2026-11-22T07:30Z 401839137"; do
      set -- $w
      f=$(epoch "$1"); u=$(epoch "$2")
      window f1 600057449 "$f" "$u" "${DATES:-$(day_utc $((f + 900 - 5 * 3600)))}" "$3"
    done
    ;;
  ufc|f1)
    [ $# -ge 4 ] || die "usage: $0 $MODE <event id> <from> <until> [out dir]$([ "$MODE" = f1 ] && echo ' [session id]')"
    EVENT=$2; F=$(epoch "$3") && [ -n "$F" ] || exit 2
    U=$(epoch "$4" "$F") && [ -n "$U" ] || exit 2
    [ "$U" -gt "$F" ] || die "until is not after from"
    OUT=${5:-$HOME/fanela-recordings/$MODE-$EVENT}; mkdir -p "$OUT" || die "can't write $OUT"
    D=${DATES:-$(day_utc $((F + 900 - 5 * 3600)))}  # the US Eastern day ESPN files it under (UTC-5 in winter)
    window "$MODE" "$EVENT" "$F" "$U" "$D" "${6:-}"
    ;;
  *)
    sed -n '2,/^set -u/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
say "done: $SAVED saved, $SAME unchanged, $FAILED failed, in $OUT"
