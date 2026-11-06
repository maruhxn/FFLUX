---
paths:
  - "infra/**"
  - "**/docker-compose*.yml"
  - "**/compose*.yml"
  - "**/Caddyfile"
  - "**/mediamtx.yml"
  - "**/Dockerfile"
  - ".github/workflows/**"
  - "**/.env.example"
---

# 인프라

- AWS VM 1대 + Docker Compose: Spring, MySQL 8, MediaMTX(`bluenviron/mediamtx:latest-ffmpeg`, 훅 실행에 셸과 ffmpeg가 필요), Caddy.
- Caddy: 자동 HTTPS, 프론트 정적 파일, `/api/*` → Spring, `/media/*` → MediaMTX(WHIP/WHEP/HLS), 녹화 디렉터리 정적 서빙(Range 지원). `/internal/*`은 라우팅하지 않는다.
- 호스트에 publish하는 포트: 80/443(Caddy), 8189 UDP/TCP(MediaMTX WebRTC). Spring 8080, MySQL, MediaMTX API는 Docker 내부망에만 둔다.
- MediaMTX 설정 요점
  - `authMethod: http`, `authHTTPAddress`는 Spring `/internal/mediamtx/auth`, `authHTTPExclude`에 `api`.
  - `overridePublisher: no`.
  - `runOnAvailable`/`runOnUnavailable` → Spring `/internal/mediamtx/available`·`unavailable`. (MediaMTX 옛 이름 `runOnReady`/`runOnNotReady`는 쓰지 않는다)
  - path 정규식: `~^rec/` 는 `record: yes`, `~^live/` 는 `record: no`. `recordSegmentDuration`은 길게(예: 12h).
  - `runOnRecordSegmentComplete` → `ffmpeg -c copy -movflags +faststart` 리먹싱 후 Spring에 통지.
  - `webrtcAdditionalHosts`에 `fflux.kro.kr` 또는 VM 공인 IP(탄력적 IP).
- 개발은 로컬 Docker Compose + HTTPS 터널(Cloudflare Quick Tunnel 또는 ngrok)로 한다. 터널은 HTTP만 전달하므로 WebRTC 미디어는 같은 와이파이에서 PC 내부 IP로 연결된다(`webrtcAdditionalHosts`에 PC 내부 IP). LTE의 WebRTC 확인은 AWS에서 한다.
- AWS VM은 쓰지 않을 때 멈춰 비용을 줄인다.
- TURN 없이 운영한다. 실기기에서 연결이 실패할 때만 coturn을 추가한다.
- 비밀값은 VM `.env`로만 주입한다. 새 변수는 `.env.example`에 추가한다.
- 배포는 VM에서 `git pull && docker compose up -d --build`. GitHub Actions 자동 배포는 마무리 단계에서 붙인다.
