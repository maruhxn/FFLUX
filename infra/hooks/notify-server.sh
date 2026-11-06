#!/bin/sh
# MediaMTX path 훅(runOnAvailable 등)을 Spring의 /internal/mediamtx/<이벤트>로 알린다.
# 사용: sh /hooks/notify-server.sh available
# FFLUX_SERVER_URL은 compose.yml이 넣는다. MTX_PATH 예: rec/12
set -eu

event="$1"
wget -q -O /dev/null \
  --header 'Content-Type: application/json' \
  --post-data "{\"path\":\"$MTX_PATH\"}" \
  "$FFLUX_SERVER_URL/internal/mediamtx/$event"
