#!/usr/bin/env bash
# End-to-end digest test: rewrites the user's digest slots so one lands ~2
# minutes from now, restarts the app (which re-arms every alarm), waits for the
# AlarmManager entry to fire and then screenshots the notification shade.
set -uo pipefail
export MSYS_NO_PATHCONV=1

. "$(dirname "$0")/_env.sh"

DEV="${1:-$DEV}"
mkdir -p "$OUT" "$TMP"
require_device

TARGET=$(date -d "+2 minutes" +%H:%M)
LEAD=$(( $(date +%s) + 120 ))
echo "target digest slot: $TARGET"

cat > "$TMP/yueji_settings.xml" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="digest_times">08:00,$TARGET</string>
    <int name="theme_mode" value="0" />
    <boolean name="todo_remind_20" value="true" />
    <boolean name="todo_remind_10" value="true" />
    <boolean name="digest_include_weekly" value="true" />
</map>
EOF

"$ADB" -s "$DEV" shell am force-stop $PKG
"$ADB" -s "$DEV" push "$WTMP/yueji_settings.xml" /data/local/tmp/yueji_settings.xml >/dev/null
"$ADB" -s "$DEV" shell run-as $PKG mkdir -p shared_prefs
"$ADB" -s "$DEV" shell run-as $PKG cp /data/local/tmp/yueji_settings.xml shared_prefs/yueji_settings.xml
"$ADB" -s "$DEV" shell run-as $PKG cat shared_prefs/yueji_settings.xml

"$ADB" -s "$DEV" shell am start -n $PKG/.MainActivity >/dev/null
sleep 4
echo "--- armed alarms ---"
"$ADB" -s "$DEV" shell dumpsys alarm 2>/dev/null | grep -A 1 "RTC_WAKEUP .*com.yueji.memo}" | grep origWhen | head -6

# Wait until a little past the slot.
while [ "$(date +%s)" -lt "$((LEAD + 25))" ]; do sleep 5; done

"$ADB" -s "$DEV" shell cmd statusbar expand-notifications 2>/dev/null || \
  "$ADB" -s "$DEV" shell service call statusbar 1 >/dev/null 2>&1
sleep 3
"$ADB" -s "$DEV" exec-out screencap -p > "$OUT/30-digest.png"

echo "--- digest notification present? ---"
"$ADB" -s "$DEV" shell dumpsys notification --noredact 2>/dev/null | grep -c "yueji" || true
echo "DIGEST_TEST_DONE"
