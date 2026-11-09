# 배포 (GCP VM)

프론트는 Vercel(`https://fflux.kro.kr`), API·미디어는 GCP VM(`https://api.fflux.kro.kr`)이다. VM의 운영 스택은 저장소 루트의 `compose.yml` 하나이고, VM의 `/opt/fflux`에 있다. 배포는 `main` 푸시 때 GitHub Actions가 자동으로 한다(아래 6절).

| 서비스 | 이미지 | 호스트 포트 |
|---|---|---|
| `caddy` | `caddy:2`, 설정 `infra/Caddyfile.prod` | 80, 443 |
| `app` | `ghcr.io/maruhxn/fflux-app`(Actions가 `server/Dockerfile`로 빌드) | 없음 |
| `mysql` | `mysql:8.4` | 없음 |
| `mediamtx` | `bluenviron/mediamtx:latest-ffmpeg`, 설정은 로컬과 같은 `infra/mediamtx.yml`·`infra/hooks` | 8189 UDP/TCP |

Caddy는 `DOMAIN`으로 자동 HTTPS를 받고 `/api/*` → Spring, `/media/*` → MediaMTX, `/vod/*` → 녹화 MP4로 보내고 그 밖(`/internal/*` 포함)은 404다. Spring 8080, MySQL, MediaMTX Control API(9997)는 Docker 내부망에만 있다.

## 1. VM 만들기 (처음 한 번)

[gcloud CLI](https://cloud.google.com/sdk/docs/install)로 로그인하고 프로젝트를 고른 뒤 실행한다. 콘솔에서 같은 값으로 만들어도 된다.

```bash
# 전용 프로젝트를 만들고 결제 계정(무료 체험 크레딧)에 연결
gcloud projects create fflux-maruhxn --name=fflux
gcloud billing accounts list
gcloud billing projects link fflux-maruhxn --billing-account=<결제 계정 ID>
gcloud config set project fflux-maruhxn
gcloud services enable compute.googleapis.com iap.googleapis.com

REGION=asia-northeast3
ZONE=asia-northeast3-a

# 고정 외부 IP 예약
gcloud compute addresses create fflux-ip --region=$REGION
gcloud compute addresses describe fflux-ip --region=$REGION --format='value(address)'   # 이 IP를 DNS와 .env에 쓴다

# VM (Debian 12, e2-small, 디스크 20GB)
gcloud compute instances create fflux \
  --zone=$ZONE --machine-type=e2-small \
  --image-family=debian-12 --image-project=debian-cloud \
  --boot-disk-size=20GB --boot-disk-type=pd-balanced \
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

내도메인.한국에서 `api.fflux.kro.kr`의 A 레코드를 위 고정 IP로 둔다(`fflux.kro.kr`은 Vercel). `dig +short api.fflux.kro.kr`로 IP가 나오는지 확인한 뒤 스택을 띄운다(Caddy가 첫 기동에 인증서를 받는다).

## 3. VM 준비 (처음 한 번)

```bash
gcloud compute ssh fflux --zone=asia-northeast3-a --tunnel-through-iap

# 스왑 2GB(e2-small 메모리 2GB의 여유분)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Docker Engine + Compose 플러그인
curl -fsSL https://get.docker.com | sudo sh

# 저장소는 계정과 상관없는 /opt/fflux에 둔다(OS Login이라 사람·배포용 서비스 계정의 사용자 이름이 다르다).
sudo apt-get install -y git
sudo git clone https://github.com/maruhxn/FFLUX.git /opt/fflux
sudo git config --system --add safe.directory /opt/fflux
cd /opt/fflux

sudo cp .env.example .env && sudo chmod 600 .env
sudo vi .env   # 아래 값을 채운다
```

`.env`에서 VM이 쓰는 키(나머지 로컬용 키는 무시된다):

| 키 | 값 |
|---|---|
| `DOMAIN` | `api.fflux.kro.kr` |
| `ACME_EMAIL` | 인증서 발급 계정 이메일. `kro.kr`의 Let's Encrypt 발급 한도(주 50장, 다른 사용자와 공유)에 걸리면 ZeroSSL로 받는 데 필요 |
| `CORS_ALLOWED_ORIGINS` | `https://fflux.kro.kr`(프론트 출처) |
| `WEBRTC_ADDITIONAL_HOSTS` | 고정 외부 IP |
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | 새로 만든 긴 무작위 값(`openssl rand -base64 24`) |
| `TEST_STREAMER_PASSWORD`, `TEST_VIEWER_PASSWORD` | 평가용 테스트 계정 비밀번호 |
| `TEST_ACCOUNTS_ENABLED` | `true`(테스트 계정 로그인 버튼) |

`.env`는 VM에만 두고 저장소에 올리지 않는다. 세션 쿠키 `Secure`는 `compose.yml`이 켠다.

## 4. 손 배포와 되돌리기

평소에는 6절의 자동 배포를 쓴다. 처음 띄울 때나 Actions가 안 될 때만 VM에서 직접 한다.

```bash
cd /opt/fflux
# 자동 배포가 VM 저장소를 커밋 단위로 체크아웃하므로(브랜치가 아니다) pull 대신 fetch + checkout을 쓴다.
# docker compose pull은 MediaMTX·Caddy 이미지도 새로 받는다(자동 배포는 app만 받는다).
sudo git fetch origin && sudo git checkout --detach origin/main
sudo docker compose pull && sudo docker compose up -d
sudo docker compose ps                      # 4개 모두 Up, mysql은 healthy
sudo docker compose logs -f app caddy       # Flyway 마이그레이션, 인증서 발급 확인

# 되돌리기: 이전 커밋으로 체크아웃하고 그 커밋의 이미지(태그 = 커밋 SHA)를 띄운다.
sudo git checkout --detach <커밋 SHA>
sudo APP_TAG=<커밋 SHA> sh -c 'docker compose pull app && docker compose up -d'
# 다음 자동 배포가 다시 최신 커밋으로 맞춘다.
```

Flyway 마이그레이션은 되돌리지 않는다. 스키마를 바꾼 커밋 이전으로 되돌릴 때는 주의한다.

MySQL 데이터, 녹화, 인증서는 named volume(`mysql_data`, `recordings`, `vod`, `caddy_data`)에 남는다. `docker compose down -v`는 이것까지 지우니 쓰지 않는다.

## 5. 배포 후 확인

```bash
curl -sI http://api.fflux.kro.kr/                        # 308 → https
curl -s -o /dev/null -w '%{http_code}\n' 'https://api.fflux.kro.kr/api/broadcasts?status=LIVE'  # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://api.fflux.kro.kr/internal/mediamtx/auth  # 404
# 프론트 출처의 CORS: Access-Control-Allow-Origin이 https://fflux.kro.kr, Allow-Credentials가 true
curl -s -D - -o /dev/null -X OPTIONS https://api.fflux.kro.kr/api/auth/login \
  -H 'Origin: https://fflux.kro.kr' -H 'Access-Control-Request-Method: POST' | grep -i access-control
curl -s -D - -o /dev/null -X POST https://api.fflux.kro.kr/media/live/0/whep \
  -H 'Content-Type: application/sdp' --data 'v=0' | grep -i www-authenticate     # 아무것도 안 나와야 한다
```

이어서 실기기로 확인한다.

1. PC Chrome에서 `https://fflux.kro.kr`에 테스트 계정 `streamer`로 로그인해 방송을 등록하고 PC 송출을 시작한다.
2. 휴대폰을 와이파이에서 끄고 LTE로 같은 방송을 연다. 플레이어 왼쪽 아래가 `WebRTC`인지 본다(`HLS`면 WebRTC 연결이 실패한 것).
3. 휴대폰 LTE에서 카메라 송출을 시작하고 PC에서 시청한다.

2026-11-09 첫 배포에서 두 방향 모두 TURN 없이 WebRTC로 연결됐다. 이후 LTE에서 WebRTC가 안 붙으면 coturn을 추가하고 `docs/decisions.md` 1절에 기록한다.

## Vercel (프론트)

- Root Directory는 `client`, 빌드는 Vite 기본값(`npm run build`, 출력 `dist`)이다.
- API 주소는 `client/.env.production`의 `VITE_API_ORIGIN=https://api.fflux.kro.kr`로 빌드에 들어간다. Vercel 환경변수로 따로 넣지 않아도 된다.
- `client/vercel.json`이 모든 경로를 `index.html`로 돌려 SPA 라우팅(`/broadcasts/1` 새로고침 등)을 받는다.

## 6. 자동 배포 (GitHub Actions)

CI(`.github/workflows/ci.yml`)와 CD(`.github/workflows/cd.yml`)로 나뉜다. CD는 `main`의 CI가 성공해야 돈다.

| 작업 | 언제 | 내용 |
|---|---|---|
| CI `server` | `main` 푸시, PR | `./gradlew test`(Testcontainers MySQL) |
| CI `client` | `main` 푸시, PR | `npm ci`, oxlint, Vitest, `npm run build`(타입체크 포함) |
| CD `changes` | `main`의 CI 성공 | 마지막으로 성공한 CD의 커밋과 비교해 `server/**`·`compose.yml`·`infra/**`·`cd.yml`이 바뀌었는지 본다(첫 CD는 무조건 배포) |
| CD `deploy` | 위에서 바뀌었을 때 | 이미지를 GHCR(`ghcr.io/maruhxn/fflux-app:<SHA>`, `latest`)에 올리고, IAP 터널 SSH로 VM 저장소를 그 커밋으로 체크아웃한 뒤 `docker compose pull app`, `up -d`. 뒤이어 5분 안에 `https://api.fflux.kro.kr/api/broadcasts?status=LIVE`가 200인지 확인 |

배포는 하나씩만 돈다(`concurrency: deploy`). 진행 상황은 저장소 Actions 탭에서 본다.

### 처음 한 번: GCP와 GitHub 설정

```bash
P=fflux-maruhxn
N=$(gcloud projects describe $P --format='value(projectNumber)')
SA=github-deploy@$P.iam.gserviceaccount.com
gcloud services enable iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com

# Workload Identity Federation: 이 저장소의 main 푸시만 토큰을 받는다
gcloud iam workload-identity-pools create github --location=global
gcloud iam workload-identity-pools providers create-oidc fflux-repo --location=global --workload-identity-pool=github \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository=='maruhxn/FFLUX' && assertion.ref=='refs/heads/main'"

# 배포용 서비스 계정과 최소 권한: IAP 터널, 조회, 이 VM의 OS Login(sudo), VM 서비스 계정 사용
gcloud iam service-accounts create github-deploy
gcloud iam service-accounts add-iam-policy-binding $SA --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$N/locations/global/workloadIdentityPools/github/attribute.repository/maruhxn/FFLUX"
gcloud projects add-iam-policy-binding $P --member=serviceAccount:$SA --role=roles/iap.tunnelResourceAccessor --condition=None
gcloud projects add-iam-policy-binding $P --member=serviceAccount:$SA --role=roles/compute.viewer --condition=None
gcloud compute instances add-iam-policy-binding fflux --zone=asia-northeast3-a --member=serviceAccount:$SA --role=roles/compute.osAdminLogin
gcloud iam service-accounts add-iam-policy-binding $N-compute@developer.gserviceaccount.com --member=serviceAccount:$SA --role=roles/iam.serviceAccountUser
gcloud compute instances add-metadata fflux --zone=asia-northeast3-a --metadata=enable-oslogin=TRUE

# GitHub 저장소 변수(비밀값 아님)
gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER --body "projects/$N/locations/global/workloadIdentityPools/github/providers/fflux-repo"
gh variable set GCP_DEPLOY_SERVICE_ACCOUNT --body "$SA"
```

GHCR 패키지 `fflux-app`은 공개(public)로 둔다. VM이 로그인 없이 이미지를 받는다. 패키지는 첫 이미지 푸시 때 비공개로 생기므로, 첫 배포가 `docker compose pull`에서 실패하면 GitHub 패키지 설정(Package settings → Change visibility)에서 Public으로 바꾸고 재실행한다.
