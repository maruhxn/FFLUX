#!/bin/sh
# MediaMTX runOnRecordSegmentComplete 훅.
# fMP4 세그먼트(/recordings/...)를 faststart MP4(/vod/...)로 리먹싱한다. 재인코딩 없이 -c copy.
# MTX_SEGMENT_PATH 예: /recordings/rec/test/2026-11-06_10-00-00-000000.mp4
set -eu

src="$MTX_SEGMENT_PATH"
dst="/vod/${src#/recordings/}"
tmp="$dst.part"

mkdir -p "$(dirname "$dst")"
# 다 만들어진 파일만 보이도록 임시 파일에 쓴 뒤 이름을 바꾼다.
ffmpeg -nostdin -hide_banner -loglevel error -y -i "$src" -c copy -movflags +faststart -f mp4 "$tmp"
mv "$tmp" "$dst"
echo "[remux] $src -> $dst"
