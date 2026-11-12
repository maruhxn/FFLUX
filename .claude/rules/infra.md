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

- GCP Compute Engine VM 1대(서울 `asia-northeast3`, `e2-small` + 스왑 2GB) + Docker Compose: Spring, MySQL 8, MediaMTX(`bluenviron/mediamtx:latest-ffmpeg`, 훅 실행에 셸과 ffmpeg가 필요), 녹화 다시 처리(`recording-sweeper`, MediaMTX와 같은 이미지), Caddy.
- 프론트는 Vercel(`fflux.kro.kr`)이 서빙하고 VM은 API·미디어(`api.fflux.kro.kr`)만 맡는다.
- Caddy: 자동 HTTPS, `/api/*` → Spring, `/media/*` → MediaMTX(WHIP/WHEP/HLS), 녹화 디렉터리 정적 서빙(Range 지원). `/internal/*`은 라우팅하지 않는다.
- 호스트에 publish하는 포트: 80/443(Caddy), 8189 UDP/TCP(MediaMTX WebRTC). GCP VPC 방화벽 규칙도 이 포트만 연다. Spring 8080, MySQL, MediaMTX API는 Docker 내부망에만 둔다.
- MediaMTX 설정 요점
  - `authMethod: http`, `authHTTPAddress`는 Spring `/internal/mediamtx/auth`, `authHTTPExclude`에 `api`.
  - `overridePublisher: no`.
  - `runOnAvailable`/`runOnUnavailable` → Spring `/internal/mediamtx/available`·`unavailable`. (MediaMTX 옛 이름 `runOnReady`/`runOnNotReady`는 쓰지 않는다)
  - path 정규식: `~^rec/` 는 `record: yes`, `~^live/` 는 `record: no`. `recordSegmentDuration`은 길게(예: 12h).
  - `runOnRecordSegmentComplete` → 리먹싱 전에 `processing`, `ffmpeg -c copy -movflags +faststart` 리먹싱 뒤 `ready`(파일 경로, ffprobe 길이) 또는 `failed`를 Spring `/internal/mediamtx/recordings/...`에 통지. Spring이 `ready`를 받아야 원본을 지우고, 남은 원본은 `recording-sweeper`가 1분마다 같은 훅으로 다시 처리한다(원본별 `flock`, 송출 중 path 제외, Spring이 받은 리먹싱 실패 3번이면 멈춤).
  - `webrtcAdditionalHosts`에 `fflux.kro.kr` 또는 VM 고정 외부 IP.
- 개발은 로컬 Docker Compose + HTTPS 터널(Cloudflare Quick Tunnel 또는 ngrok)로 한다. 터널은 HTTP만 전달하므로 WebRTC 미디어는 같은 와이파이에서 PC 내부 IP로 연결된다(`webrtcAdditionalHosts`에 PC 내부 IP). LTE의 WebRTC 확인은 VM에서 한다.
- VM은 GCP 신규 크레딧(90일)으로 운영하고, 평가가 끝날 때까지 켜 둔다.
- TURN 없이 운영한다. 실기기에서 연결이 실패할 때만 coturn을 추가한다.
- 운영 환경변수는 GitHub Actions Secrets(비밀번호·결제 시크릿 키)와 Variables(도메인·출처 등 비밀이 아닌 운영 설정)에 둔다. CD가 배포마다 이 값으로 VM `/opt/fflux/.env`를 새로 쓰므로(권한 600) VM에서 직접 고치지 않는다. 값만 바꿨으면 CD를 수동 실행(`workflow_dispatch`)한다. 새 변수는 `.env.example`과 `cd.yml`의 `.env` 생성 단계에 함께 추가한다.
- CI(`.github/workflows/ci.yml`, 검사)와 CD(`cd.yml`, 배포)를 나눈다. CD는 `main`의 CI가 성공한 뒤 돈다: 마지막 성공 CD 이후 서버·인프라·`cd.yml` 변경이면 Spring 이미지를 GHCR에 올리고 VM(`/opt/fflux`)을 그 커밋으로 체크아웃해 `docker compose pull app && docker compose up -d`. VM에서 이미지를 빌드하지 않는다. GCP 인증은 Workload Identity Federation(장기 키 금지), VM 접속은 IAP 터널 SSH(OS Login).
