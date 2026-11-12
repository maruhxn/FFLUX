#!/bin/sh
# MediaMTX runOnRecordSegmentComplete 훅. sweep-recordings.sh도 남은 원본마다 이 스크립트를 다시 돌린다.
# fMP4 세그먼트(/recordings/...)를 faststart MP4(/vod/...)로 리먹싱한다. 재인코딩 없이 -c copy.
# 리먹싱 전에 processing, 성공하면 ready(파일 경로, ffprobe 길이), 실패하면 failed를 Spring에 알린다.
# Spring이 ready에 2xx로 답해야만 원본을 지운다. 그래서 남은 원본은 곧 끝나지 않았거나 Spring이 받았다고 답하지 않은
# 녹화이고, sweep-recordings.sh가 다시 처리한다. 리먹싱 실패는 Spring이 failed를 받았을 때만 원본 옆 .failures에 센다
# (실패 통지가 사라진 채 멈추면 녹화가 "처리 중"에 남으므로). 3번이면 sweep-recordings.sh가 더 다시 처리하지 않는다.
# FFLUX_SERVER_URL은 compose.yml이 넣는다. MTX_PATH 예: rec/12
# MTX_SEGMENT_PATH 예: /recordings/rec/12/2026-11-06_10-00-00-000000.mp4
set -eu

src="$MTX_SEGMENT_PATH"
file="${src#/recordings/}"
dst="/vod/$file"
tmp="$dst.part"

# 원래 훅과 sweep-recordings.sh(다른 컨테이너)가 같은 원본을 동시에 처리하지 않게 원본별로 잠근다.
# 같은 볼륨의 파일 잠금이라 컨테이너 사이에도 걸리고, 프로세스가 죽으면 커널이 푼다. 이미 잠겨 있으면 그쪽에 맡긴다.
exec 9>"$src.lock"
flock -n 9 || exit 0
# 잠그는 사이 다른 쪽이 끝내 원본을 지웠으면 할 일이 없다.
[ -f "$src" ] || { rm -f "$src.lock"; exit 0; }

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
# 리먹싱한 MP4가 이미 있으면(ready 통지만 실패했던 경우) 다시 만들지 않는다. mv로만 생기므로 다 만들어진 파일이다.
# 다 만들어진 파일만 보이도록 임시 파일에 쓴 뒤 이름을 바꾼다.
if [ -f "$dst" ] || { ffmpeg -nostdin -hide_banner -loglevel error -y -i "$src" -c copy -movflags +faststart -f mp4 "$tmp" && mv "$tmp" "$dst"; }; then
  # 길이(초, 소수). ffprobe가 없거나 실패하면 생략하고, 서버가 방송 시작~종료 시간으로 대신한다.
  duration="$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$dst" 2>/dev/null || true)"
  extra=",\"filePath\":\"$file\""
  case "$duration" in
    ''|*[!0-9.]*) ;;
    *) extra="$extra,\"durationSeconds\":$duration" ;;
  esac
  # 서버가 녹화를 알게 된 뒤에만 원본을 지운다. 통지가 실패하면 원본을 남겨 이 훅을 다시 돌릴 수 있게 한다.
  if notify ready "$extra"; then
    rm -f "$src" "$src.failures" "$src.lock"
    echo "[remux] $src -> $dst (${duration:-길이 모름}초)"
  else
    echo "[remux] ready 통지 실패, 원본을 남겨 다시 처리한다: $src" >&2
  fi
else
  rm -f "$tmp"
  if notify failed ""; then
    failures="$(cat "$src.failures" 2>/dev/null || true)"
    case "$failures" in ''|*[!0-9]*) failures=0 ;; esac
    echo $((failures + 1)) > "$src.failures"
  fi
  echo "[remux] 리먹싱 실패, 원본을 남긴다: $src" >&2
  exit 1
fi
