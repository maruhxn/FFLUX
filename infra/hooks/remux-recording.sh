#!/bin/sh
# MediaMTX runOnRecordSegmentComplete 훅.
# fMP4 세그먼트(/recordings/...)를 faststart MP4(/vod/...)로 리먹싱한다. 재인코딩 없이 -c copy.
# 리먹싱 전에 processing, 성공하면 ready(파일 경로, ffprobe 길이), 실패하면 failed를 Spring에 알린다.
# 성공하면 원본을 지우고, 실패하면 손으로 복구할 수 있게 남긴다(자동 재시도 없음).
# FFLUX_SERVER_URL은 compose.yml이 넣는다. MTX_PATH 예: rec/12
# MTX_SEGMENT_PATH 예: /recordings/rec/12/2026-11-06_10-00-00-000000.mp4
set -eu

src="$MTX_SEGMENT_PATH"
file="${src#/recordings/}"
dst="/vod/$file"
tmp="$dst.part"

# 통지가 실패해도 리먹싱은 계속한다. 실패하면 1을 돌려준다. 서버가 응답하지 않아도 리먹싱을 오래 막지 않게 10초만 기다린다.
notify() {
  wget -q -T 10 -O /dev/null \
    --header 'Content-Type: application/json' \
    --post-data "{\"path\":\"$MTX_PATH\",\"segmentPath\":\"$src\"$2}" \
    "$FFLUX_SERVER_URL/internal/mediamtx/recordings/$1" \
    || { echo "[remux] $1 통지 실패: $src" >&2; return 1; }
}

notify processing "" || true

mkdir -p "$(dirname "$dst")"
# 다 만들어진 파일만 보이도록 임시 파일에 쓴 뒤 이름을 바꾼다.
if ffmpeg -nostdin -hide_banner -loglevel error -y -i "$src" -c copy -movflags +faststart -f mp4 "$tmp" && mv "$tmp" "$dst"; then
  # 길이(초, 소수). ffprobe가 없거나 실패하면 생략하고, 서버가 방송 시작~종료 시간으로 대신한다.
  duration="$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$dst" 2>/dev/null || true)"
  extra=",\"filePath\":\"$file\""
  case "$duration" in
    ''|*[!0-9.]*) ;;
    *) extra="$extra,\"durationSeconds\":$duration" ;;
  esac
  # 서버가 녹화를 알게 된 뒤에만 원본을 지운다. 통지가 실패하면 원본을 남겨 이 훅을 다시 돌릴 수 있게 한다.
  notify ready "$extra" && rm -f "$src"
  echo "[remux] $src -> $dst (${duration:-길이 모름}초)"
else
  rm -f "$tmp"
  notify failed "" || true
  echo "[remux] 리먹싱 실패, 원본을 남긴다: $src" >&2
  exit 1
fi
