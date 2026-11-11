#!/bin/sh
# Renders every card state for every club and language on a device with the debug build, and pulls the images.
# usage: sh tools/demo_all.sh <adb serial> <out dir> [lang...]
#   CLUBS="359 382 nba13"  clubs to draw (ESPN ids; NBA teams as nba<id>). Default: the Super League.
#   COUNTRY=GB             draw them as a viewer in that country sees them (where-to-watch); default: the app's setting.
# Use an emulator or a test device; this is the worldwide exploration (app.fanela.world.debug).
export MSYS_NO_PATHCONV=1
S=$1; OUT=$2; shift 2; LANGS=${*:-"en el"}
A="adb -s $S"; P=app.fanela.world.debug
CLUBS=${CLUBS:-"435 605 887 443 11553 1010 6790 5276 132429 8354 2575 11431 20043 21969"}
for L in $LANGS; do for C in $CLUBS; do
  N=${C}_$L${COUNTRY:+_$COUNTRY}
  D=files/demo/$N
  $A shell am broadcast -n $P/app.fanela.tv.DebugReceiver -a app.fanela.tv.DEMO --es club $C --es lang $L ${COUNTRY:+--es country $COUNTRY} >/dev/null
  sleep 5
  for i in $(seq 1 120); do $A shell run-as $P ls $D 2>/dev/null | grep -q DONE && break; sleep 3; done
  mkdir -p "$OUT/$N"
  for f in $($A shell run-as $P ls $D | tr -d '\r' | grep 'png$'); do $A exec-out run-as $P cat $D/$f > "$OUT/$N/demo_$f"; done
  echo "$C $L ${COUNTRY:-}: $(ls "$OUT/$N" | wc -l) cards"
done; done
