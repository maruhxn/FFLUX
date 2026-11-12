#!/bin/sh
# 남은 녹화 원본을 다시 처리한다(recording-sweeper 컨테이너의 진입점, docs/decisions.md 녹화 절).
# remux-recording.sh는 Spring이 ready를 받아야만 원본을 지운다. 그래서 /recordings에 남은 원본은 알림이 사라졌거나,
# 리먹싱 중에 미디어 서버가 죽었거나, 방송 중에 죽어 세그먼트 완료 훅이 오지 않은 녹화다.
# 1분마다 2분 넘게(find -mmin은 분 단위로 올려 세므로 실제로는 2~3분) 바뀌지 않은 원본을 찾아 remux-recording.sh를
# 다시 돌린다. Spring이 실패를 받은 리먹싱이 3번 쌓인 원본은 건너뛴다.
# 2분 동안 데이터가 오지 않는 송출일 수도 있으므로, MediaMTX에서 지금 송출 중인 경로의 원본도 건너뛴다.
# FFLUX_SERVER_URL(Spring), MEDIAMTX_API_URL(MediaMTX Control API)은 compose가 넣는다.
set -u

MAX_FAILURES=3

while true; do
  # 지금 송출 중인 경로를 모르면 녹화 중인 원본을 건드릴 수 있으므로 이번 차례는 건너뛴다.
  if paths="$(wget -q -T 10 -O - "$MEDIAMTX_API_URL/v3/paths/list?itemsPerPage=1000")"; then
    find /recordings -type f -name '*.mp4' -mmin +2 | while read -r src; do
      failures="$(cat "$src.failures" 2>/dev/null || true)"
      case "$failures" in ''|*[!0-9]*) failures=0 ;; esac
      [ "$failures" -ge "$MAX_FAILURES" ] && continue
      # /recordings/rec/12/2026-...mp4 → rec/12
      path="${src#/recordings/}"
      path="${path%/*}"
      case "$paths" in *"\"name\":\"$path\""*) continue ;; esac
      echo "[sweep] 남은 원본을 다시 처리한다: $src"
      # 훅이 find 목록(표준 입력)을 먹지 않게 한다.
      MTX_PATH="$path" MTX_SEGMENT_PATH="$src" sh /hooks/remux-recording.sh </dev/null || true
    done
  else
    echo "[sweep] MediaMTX 경로 목록을 읽지 못해 이번 차례는 건너뛴다" >&2
  fi
  sleep 60
done
