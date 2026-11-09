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

- GCP Compute Engine VM 1대(서울 `asia-northeast3`, `e2-small` + 스왑 2GB) + Docker Compose: Spring, MySQL 8, MediaMTX(`bluenviron/mediamtx:latest-ffmpeg`, 훅 실행에 셸과 ffmpeg가 필요), Caddy.
- 프론트는 Vercel(`fflux.kro.kr`)이 서빙하고 VM은 API·미디어(`api.fflux.kro.kr`)만 맡는다.
- Caddy: 자동 HTTPS, `/api/*` → Spring, `/media/*` → MediaMTX(WHIP/WHEP/HLS), 녹화 디렉터리 정적 서빙(Range 지원). `/internal/*`은 라우팅하지 않는다.
- 호스트에 publish하는 포트: 80/443(Caddy), 8189 UDP/TCP(MediaMTX WebRTC). GCP VPC 방화벽 규칙도 이 포트만 연다. Spring 8080, MySQL, MediaMTX API는 Docker 내부망에만 둔다.
- MediaMTX 설정 요점
  - `authMethod: http`, `authHTTPAddress`는 Spring `/internal/mediamtx/auth`, `authHTTPExclude`에 `api`.
  - `overridePublisher: no`.
  - `runOnAvailable`/`runOnUnavailable` → Spring `/internal/mediamtx/available`·`unavailable`. (MediaMTX 옛 이름 `runOnReady`/`runOnNotReady`는 쓰지 않는다)
  - path 정규식: `~^rec/` 는 `record: yes`, `~^live/` 는 `record: no`. `recordSegmentDuration`은 길게(예: 12h).
  - `runOnRecordSegmentComplete` → `ffmpeg -c copy -movflags +faststart` 리먹싱 후 Spring에 통지.
  - `webrtcAdditionalHosts`에 `fflux.kro.kr` 또는 VM 고정 외부 IP.
- 개발은 로컬 Docker Compose + HTTPS 터널(Cloudflare Quick Tunnel 또는 ngrok)로 한다. 터널은 HTTP만 전달하므로 WebRTC 미디어는 같은 와이파이에서 PC 내부 IP로 연결된다(`webrtcAdditionalHosts`에 PC 내부 IP). LTE의 WebRTC 확인은 VM에서 한다.
- VM은 GCP 신규 크레딧(90일)으로 운영하고, 평가가 끝날 때까지 켜 둔다.
- TURN 없이 운영한다. 실기기에서 연결이 실패할 때만 coturn을 추가한다.
- 비밀값은 VM `.env`로만 주입한다. 새 변수는 `.env.example`에 추가한다.
- 배포는 GitHub Actions(`.github/workflows/ci.yml`)가 `main` 푸시 때 한다: 검사 통과 + 서버·인프라·workflow 변경이면 Spring 이미지를 GHCR에 올리고 VM(`/opt/fflux`)을 그 커밋으로 체크아웃해 `docker compose pull app && docker compose up -d`. VM에서 이미지를 빌드하지 않는다. GCP 인증은 Workload Identity Federation(장기 키 금지), VM 접속은 IAP 터널 SSH(OS Login).
