#!/usr/bin/env bash
# Pushes a small, realistic demo dataset into the app's private storage so the
# UI can be verified with content instead of empty states.
# Requires the *debug* build, which is debuggable and therefore accessible
# through `run-as`.
set -euo pipefail

# Git Bash rewrites /data/local/tmp/... into a Windows path; stop it.
export MSYS_NO_PATHCONV=1

. "$(dirname "$0")/_env.sh"

DEV="${1:-$DEV}"
require_device

# The app stores epoch *millis*; `date +%s` gives seconds.
NOW=$(( $("$ADB" -s "$DEV" shell date +%s | tr -d '\r') * 1000 ))
MIN=60000
HOUR=3600000
DAY=86400000
echo "device now=$NOW"

rm -rf "$TMP" && mkdir -p "$TMP"

cat > "$TMP/notes.json" <<EOF
[
  {"id":"n1","title":"周末计划","content":"1. 上午去公园跑步\n2. 下午整理书桌\n3. 晚上看一部电影","pinned":true,"tint":"SKY","createdAt":$((NOW-DAY)),"updatedAt":$NOW},
  {"id":"n2","title":"读书笔记","content":"《深度工作》：把注意力当成稀缺资源来管理，固定时段集中处理浅层事务。","pinned":false,"tint":"MINT","createdAt":$((NOW-2*DAY)),"updatedAt":$((NOW-HOUR))},
  {"id":"n3","title":"购物清单","content":"牛奶、鸡蛋、全麦面包、咖啡豆","pinned":false,"tint":"SAND","createdAt":$((NOW-3*DAY)),"updatedAt":$((NOW-2*HOUR))}
]
EOF

cat > "$TMP/todos.json" <<EOF
[
  {"id":"t1","title":"项目周会","detail":"同步本周进度与风险","startAt":$((NOW+90*MIN)),"done":false,"remind20":true,"remind10":true,"createdAt":$NOW},
  {"id":"t2","title":"提交设计稿","detail":"把登录页的两个方案补完","startAt":$((NOW+4*HOUR)),"done":false,"remind20":true,"remind10":true,"createdAt":$NOW},
  {"id":"t3","title":"健身 45 分钟","detail":"","startAt":$((NOW-HOUR)),"done":true,"remind20":false,"remind10":false,"createdAt":$NOW}
]
EOF

cat > "$TMP/weekly.json" <<EOF
[
  {"id":"w1","title":"每周复盘","detail":"回顾本周完成的事项，列下周重点","days":[1,5],"time":"20:30","enabled":true,"remind20":true,"remind10":false,"createdAt":$NOW},
  {"id":"w2","title":"打扫房间","detail":"","days":[6],"time":"10:00","enabled":true,"remind20":false,"remind10":true,"createdAt":$NOW}
]
EOF

"$ADB" -s "$DEV" shell am force-stop $PKG

for f in notes.json todos.json weekly.json; do
  "$ADB" -s "$DEV" push "$WTMP/$f" /data/local/tmp/$f >/dev/null
  "$ADB" -s "$DEV" shell run-as $PKG mkdir -p files
  "$ADB" -s "$DEV" shell run-as $PKG cp /data/local/tmp/$f files/$f
  "$ADB" -s "$DEV" shell run-as $PKG ls -la files/$f
done

echo "DEMO_DATA_OK"
