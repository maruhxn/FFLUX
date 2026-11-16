#!/bin/sh
# 송출 중인 방송의 화면을 30초마다 한 장씩 찍는다(thumbnailer 컨테이너의 진입점, docs/decisions.md 썸네일).
# MediaMTX 경로 목록에서 방송 경로(live/12, rec/12)마다 내부 RTSP로 한 프레임을 읽어 /thumbnails/<경로>.jpg(가로 640px)로 둔다.
# MediaMTX는 WebRTC 송출자에게 2초마다 키프레임을 요청하므로 한 장을 금방 얻는다. RTSP 읽기는 Spring의 시청 허가(LIVE)를 탄다.
# 임시 파일에 쓴 뒤 이름을 바꿔 다 만든 이미지만 보이게 하고, 실패하면 이전 장면을 그대로 둔다. 끝난 방송은 마지막 장면이 남는다.
# MEDIAMTX_API_URL(Control API), MEDIAMTX_RTSP_URL(RTSP 주소)은 compose가 넣는다.
set -u

INTERVAL_SECONDS=30
# 한 장을 얻는 데 이보다 오래 걸리면 이번에는 포기한다(키프레임은 2초마다 온다).
CAPTURE_TIMEOUT_SECONDS=10

while true; do
  if paths="$(wget -q -T 10 -O - "$MEDIAMTX_API_URL/v3/paths/list?itemsPerPage=1000")"; then
    # 경로 이름만 뽑는다("confName"은 따옴표 앞 글자가 달라 걸리지 않는다). 방송 경로 모양만 남긴다.
    echo "$paths" | grep -o '"name":"[^"]*"' | sed 's/"name":"\(.*\)"/\1/' | grep -E '^(live|rec)/[0-9]+$' | while read -r path; do
      # 송출자가 아직 붙는 중인 경로는 시청 허가가 거절되므로 찍지 않는다.
      wget -q -T 5 -O - "$MEDIAMTX_API_URL/v3/paths/get/$path" 2>/dev/null | grep -q '"available":true' || continue
      dst="/thumbnails/$path.jpg"
      tmp="/thumbnails/$path.tmp.jpg"
      mkdir -p "$(dirname "$dst")"
      # 키프레임만 디코딩한다. 중간 프레임부터 디코딩하면 참조 프레임이 없어 깨진 장면이 찍힌다.
      if timeout "$CAPTURE_TIMEOUT_SECONDS" ffmpeg -nostdin -hide_banner -loglevel error -rtsp_transport tcp \
        -skip_frame nokey -i "$MEDIAMTX_RTSP_URL/$path" -frames:v 1 -vf scale=640:-2 -q:v 5 -f image2 -update 1 -y "$tmp" </dev/null \
        && mv "$tmp" "$dst"; then
        :
      else
        rm -f "$tmp"
        echo "[thumbnail] 찍지 못해 이전 장면을 둔다: $path" >&2
      fi
    done
  else
    echo "[thumbnail] MediaMTX 경로 목록을 읽지 못해 이번 차례는 건너뛴다" >&2
  fi
  sleep "$INTERVAL_SECONDS"
done
