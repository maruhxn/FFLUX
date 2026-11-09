# 배포 (GCP VM)

운영 스택은 저장소 루트의 `compose.yml` 하나다. VM에서 `git pull && docker compose up -d --build`로 배포한다.

| 서비스 | 이미지 | 호스트 포트 |
|---|---|---|
| `caddy` | `client/Dockerfile`(클라이언트 빌드 결과 + `caddy:2`), 설정 `infra/Caddyfile.prod` | 80, 443 |
| `app` | `server/Dockerfile`(Spring bootJar, JRE 25) | 없음 |
| `mysql` | `mysql:8.4` | 없음 |
| `mediamtx` | `bluenviron/mediamtx:latest-ffmpeg`, 설정은 로컬과 같은 `infra/mediamtx.yml`·`infra/hooks` | 8189 UDP/TCP |

Caddy는 `DOMAIN`으로 자동 HTTPS를 받고 `/api/*` → Spring, `/media/*` → MediaMTX, `/vod/*` → 녹화 MP4, 그 밖은 프론트(SPA)로 보낸다. `/internal/*`은 404이고 Spring 8080, MySQL, MediaMTX Control API(9997)는 Docker 내부망에만 있다.

## 1. VM 만들기 (처음 한 번)

[gcloud CLI](https://cloud.google.com/sdk/docs/install)로 로그인하고 프로젝트를 고른 뒤 실행한다. 콘솔에서 같은 값으로 만들어도 된다.

```bash
REGION=asia-northeast3
ZONE=asia-northeast3-a

# 고정 외부 IP 예약
gcloud compute addresses create fflux-ip --region=$REGION
gcloud compute addresses describe fflux-ip --region=$REGION --format='value(address)'   # 이 IP를 DNS와 .env에 쓴다

# VM (Debian 12, e2-standard-2, 디스크 30GB)
gcloud compute instances create fflux \
  --zone=$ZONE --machine-type=e2-standard-2 \
  --image-family=debian-12 --image-project=debian-cloud \
  --boot-disk-size=30GB \
  --address=fflux-ip --tags=fflux-web

# 방화벽: 80/443(TCP), 8189(UDP/TCP)만 연다
gcloud compute firewall-rules create fflux-web \
  --direction=INGRESS --action=ALLOW \
  --rules=tcp:80,tcp:443,tcp:8189,udp:8189 \
  --target-tags=fflux-web --source-ranges=0.0.0.0/0
```

- `default` 네트워크에는 SSH(22)·RDP(3389)·ICMP를 여는 기본 규칙이 있다. 외부에 열린 포트가 80/443/8189만 남도록 `default-allow-rdp`, `default-allow-icmp`를 지운다. SSH는 관리에 필요하므로 지우지 않고 Google IAP 대역(`35.235.240.0/20`)에서만 받게 좁혀, 인터넷에서는 닿지 않고 `gcloud compute ssh --tunnel-through-iap`로만 접속한다. `default-allow-internal`은 VPC 내부 대역(`10.128.0.0/9`)만 허용하므로 그대로 둔다.
  ```bash
  gcloud compute firewall-rules delete default-allow-rdp default-allow-icmp
  gcloud compute firewall-rules update default-allow-ssh --source-ranges=35.235.240.0/20
  ```
- VM(Debian 이미지)에는 호스트 방화벽이 없으므로 호스트에서 열리는 포트는 Docker가 publish한 것뿐이다. Docker는 `compose.yml`의 `ports`(80, 443, 8189)만 호스트에 연다.

## 2. DNS

내도메인.한국에서 `fflux.kro.kr`의 A 레코드를 위 고정 IP로 둔다. `dig +short fflux.kro.kr`로 IP가 나오는지 확인한 뒤 스택을 띄운다(Caddy가 첫 기동에 인증서를 받는다).

## 3. VM 준비 (처음 한 번)

```bash
gcloud compute ssh fflux --zone=asia-northeast3-a --tunnel-through-iap

# Docker Engine + Compose 플러그인
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker

sudo apt-get install -y git
git clone https://github.com/maruhxn/FFLUX.git fflux && cd fflux

cp .env.example .env
vi .env   # 아래 값을 채운다
```

`.env`에서 VM이 쓰는 키(나머지 로컬용 키는 무시된다):

| 키 | 값 |
|---|---|
| `DOMAIN` | `fflux.kro.kr` |
| `WEBRTC_ADDITIONAL_HOSTS` | 고정 외부 IP |
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | 새로 만든 긴 무작위 값(`openssl rand -base64 24`) |
| `TEST_STREAMER_PASSWORD`, `TEST_VIEWER_PASSWORD` | 평가용 테스트 계정 비밀번호 |
| `TEST_ACCOUNTS_ENABLED` | `true`(테스트 계정 로그인 버튼) |

`.env`는 VM에만 두고 저장소에 올리지 않는다. 세션 쿠키 `Secure`는 `compose.yml`이 켠다.

## 4. 배포

```bash
cd ~/fflux
git pull && docker compose up -d --build
docker compose ps                      # 4개 모두 Up, mysql은 healthy
docker compose logs -f app caddy       # Flyway 마이그레이션, 인증서 발급 확인
```

MySQL 데이터, 녹화, 인증서는 named volume(`mysql_data`, `recordings`, `vod`, `caddy_data`)에 남는다. `docker compose down -v`는 이것까지 지우니 쓰지 않는다.

## 5. 배포 후 확인

```bash
curl -sI http://fflux.kro.kr/                            # 308 → https
curl -s -o /dev/null -w '%{http_code}\n' https://fflux.kro.kr/                       # 200
curl -s -o /dev/null -w '%{http_code}\n' 'https://fflux.kro.kr/api/broadcasts?status=LIVE'  # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://fflux.kro.kr/internal/mediamtx/auth  # 404
curl -s -D - -o /dev/null -X POST https://fflux.kro.kr/media/live/0/whep \
  -H 'Content-Type: application/sdp' --data 'v=0' | grep -i www-authenticate     # 아무것도 안 나와야 한다
```

이어서 실기기로 확인한다.

1. PC Chrome에서 `https://fflux.kro.kr`에 테스트 계정 `streamer`로 로그인해 방송을 등록하고 PC 송출을 시작한다.
2. 휴대폰을 와이파이에서 끄고 LTE로 같은 방송을 연다. 플레이어 왼쪽 아래가 `WebRTC`인지 본다(`HLS`면 WebRTC 연결이 실패한 것).
3. 휴대폰 LTE에서 카메라 송출을 시작하고 PC에서 시청한다.

LTE에서 WebRTC가 안 붙으면 coturn을 추가하고 `docs/decisions.md` 1절에 기록한다.
