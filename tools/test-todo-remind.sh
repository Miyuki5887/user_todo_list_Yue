#!/usr/bin/env bash
# End-to-end to-do reminder test: injects a single to-do that starts 21 minutes
# from now, so the "20 minutes before" alarm is due in about a minute. Then waits
# for it to fire and screenshots the notification shade.
set -uo pipefail
export MSYS_NO_PATHCONV=1

. "$(dirname "$0")/_env.sh"

DEV="${1:-$DEV}"
mkdir -p "$OUT" "$TMP"
require_device

MIN=60000
NOW=$(( $("$ADB" -s "$DEV" shell date +%s | tr -d '\r') * 1000 ))
START=$(( NOW + 21 * MIN ))
echo "startAt = $START  ($(date -d "@$((START / 1000))" '+%H:%M'))"

cat > "$TMP/todos.json" <<EOF
[
  {"id":"t-remind","title":"提醒链路自检","detail":"开始前 20 分钟应弹通知","startAt":$START,"done":false,"remind20":true,"remind10":true,"createdAt":$NOW}
]
EOF

"$ADB" -s "$DEV" push "$WTMP/todos.json" /data/local/tmp/todos.json >/dev/null
"$ADB" -s "$DEV" shell run-as $PKG mkdir -p files
"$ADB" -s "$DEV" shell run-as $PKG cp /data/local/tmp/todos.json files/todos.json
"$ADB" -s "$DEV" shell am force-stop $PKG
"$ADB" -s "$DEV" shell am start -n $PKG/.MainActivity >/dev/null
sleep 4

echo "--- armed to-do alarms ---"
"$ADB" -s "$DEV" shell dumpsys alarm 2>/dev/null | grep "origWhen" | grep "com.yueji.memo" | sort -t= -k2 | head -3

# The +20 min alarm is due ~1 minute from the moment we injected the to-do.
sleep 75

"$ADB" -s "$DEV" shell cmd statusbar expand-notifications 2>/dev/null
sleep 3
"$ADB" -s "$DEV" exec-out screencap -p > "$OUT/50-todo-remind.png"
echo "TODO_REMIND_TEST_DONE"
