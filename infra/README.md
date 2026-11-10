# 로컬 미디어 스택 (이슈 #2 미디어 스파이크)

> VM 운영 스택은 저장소 루트 `compose.yml`이다. 배포 방법은 `docs/deploy.md`.

PC에 Caddy + MediaMTX를 Docker Compose로 띄우고, Cloudflare Quick Tunnel로 HTTPS 주소를 붙여 휴대폰에서도 송출·시청을 확인한다.

#6부터 MediaMTX는 송출·시청마다 Spring(`/internal/mediamtx/auth`)에 묻는다. 송출은 서버가 발급한 송출 토큰이 있어야 하고, 시청은 LIVE 방송만 된다. 송출이 시작되면 `runOnAvailable`, 끊기면 `runOnUnavailable` 훅(`hooks/notify-server.sh`)이 Spring에 알려 방송이 LIVE·ENDED가 된다. 종료 요청 뒤에는 Spring이 Control API(`127.0.0.1:9997`)로 송출 세션을 kick한다. Spring은 호스트에서 `./gradlew :app:bootRun`(8080)으로 띄우고, MediaMTX는 `FFLUX_SERVER_URL`(기본 `http://host.docker.internal:8080`)로 닿는다. 그래서 아래 스파이크 테스트 페이지로는 더 이상 송출할 수 없고, 송출은 클라이언트의 방송 상세 → "방송 시작 (PC)"로 한다(`client`의 Vite가 `/media`를 이 스택의 Caddy로 넘긴다).

## 구성

| 파일 | 역할 |
|---|---|
| `compose.yml` | Caddy, MediaMTX(`bluenviron/mediamtx:latest-ffmpeg`) |
| `Caddyfile` | 로컬 HTTP(:80, 호스트 `HTTP_PORT`) same-origin 라우팅 |
| `mediamtx.yml` | WHIP/WHEP/HLS, `rec/`·`live/` path 규칙, 녹화와 리먹싱 훅 |
| `hooks/notify-server.sh` | path 훅(`runOnAvailable`, `runOnUnavailable`)을 Spring `/internal/mediamtx/<이벤트>`로 알림 |
| `hooks/remux-recording.sh` | 녹화 세그먼트(fMP4)를 faststart MP4로 리먹싱(`-c copy`) |
| `spike/` | 빌드 없는 테스트 페이지(PC 송출, 모바일 송출, 시청, 녹화 목록) |
| `data/` | 녹화 원본(`data/recordings`)과 리먹싱 결과(`data/vod`). git에 넣지 않는다 |

### URL

| 경로 | 대상 |
|---|---|
| `/media/<path>/whip`, `/media/<path>/whep` | MediaMTX WebRTC(8889). WHIP 송출, WHEP 시청 |
| `/media/<path>/index.m3u8` | MediaMTX HLS(8888). WHEP 폴백 |
| `/vod/rec/<id>/<파일>.mp4` | 리먹싱된 녹화 MP4 정적 서빙(Range 지원) |
| `/` | 스파이크 테스트 페이지 |

`<path>`는 `rec/<id>`(녹화) 또는 `live/<id>`(미녹화)만 허용한다. 다른 path는 MediaMTX가 거절한다. `/internal/*`은 라우팅하지 않는다.

미디어(RTP)는 터널을 거치지 않는다. 터널은 HTTP만 전달하므로 WHIP/WHEP 시그널링과 HLS만 터널로 가고, WebRTC 미디어는 휴대폰이 PC 내부 IP의 8189(UDP/TCP)로 직접 붙는다. 그래서 같은 와이파이에서만 WebRTC가 되고, LTE에서는 HLS만 된다(LTE의 WebRTC는 VM 배포 단계에서 확인).

## 사전 준비

- Docker Desktop
- cloudflared: `brew install cloudflared`
- PC와 휴대폰이 같은 와이파이에 있을 것. 공유기·사내망의 클라이언트 격리(AP isolation)가 켜져 있으면 WebRTC가 안 붙는다.
- macOS 방화벽이 켜져 있으면 Docker의 8189 수신을 허용한다.

## 실행

저장소 루트에서 실행한다.

```bash
# 1) 환경변수. WEBRTC_ADDITIONAL_HOSTS에 PC 내부 IP를 넣는다.
cp .env.example .env
ipconfig getifaddr en0        # macOS 와이파이 IP 예: 192.168.0.10 (유선이면 en1 등)
# .env 의 WEBRTC_ADDITIONAL_HOSTS=192.168.0.10 으로 수정

# 2) 스택 기동
docker compose -f infra/compose.yml --env-file .env up -d
docker compose -f infra/compose.yml logs -f mediamtx   # 설정 오류가 없는지 확인

# 3) PC에서 확인: http://localhost:8000

# 4) HTTPS 터널(다른 터미널). 출력되는 https://<랜덤>.trycloudflare.com 주소를 쓴다.
cloudflared tunnel --url http://localhost:8000

# 정리
docker compose -f infra/compose.yml down
```

- `--env-file .env`를 빼면 루트의 `.env`를 읽지 않는다(compose 파일이 `infra/`에 있어서).
- 와이파이 IP가 바뀌면 `.env`를 고치고 `up -d`를 다시 실행한다.
- `getDisplayMedia`/`getUserMedia`는 HTTPS(또는 localhost)에서만 동작한다. 휴대폰은 반드시 터널 주소로 연다.
- 터널 주소는 실행할 때마다 바뀐다.
- 앱(#5 이후)을 휴대폰으로 확인할 때는 Vite 개발 서버에 터널을 연다(`cloudflared tunnel --url http://localhost:5173`). Vite가 `/api`는 Spring으로, `/media`는 이 Caddy로 넘긴다.
- Quick Tunnel은 SSE를 전달하지 않는다. 터널 주소로 보면 방송 시작·종료·정보 변경이 바로 반영되지 않으니(시청 중 끊기면 종료는 반영된다) PC 시청은 `http://localhost:5173`으로 한다.

## 테스트 페이지 (#2 스파이크 기록)

송출 인증이 붙기 전(#2)에 쓰던 페이지다. 지금은 토큰이 없어 송출이 거절되고, 시청 페이지는 LIVE 방송의 path로만 재생된다.

| 페이지 | 내용 |
|---|---|
| `publish-pc.html` | `getDisplayMedia`(소리 포함) + 마이크를 Web Audio로 섞어 오디오 트랙 하나로 WHIP 송출. 화면 소리가 없으면 마이크만 |
| `publish-mobile.html` | 카메라(전면/후면) + 마이크 WHIP 송출, Wake Lock |
| `watch.html` | WHEP 재생, 실패하면 HLS 폴백(`HLS 강제` 체크 가능). `?path=rec/pc1&hls=1`로 바로 열 수 있다 |
| `recordings.html` | `/vod` 아래 MP4 목록과 `<video>` 재생 |

송출 페이지와 시청 페이지는 `getStats`로 실제 협상된 코덱을 보여준다. 영상이 `video/H264`로 나오면 `setCodecPreferences`가 적용된 것이다. 녹화 MP4는 송출을 끝내야(세그먼트가 닫혀야) 생긴다.

## 수용 기준별 확인 체크리스트

각 항목을 직접 확인하고 결과 칸을 채운다. 기기·브라우저 버전도 함께 적는다. (2026-11-06 첫 확인 때는 기기·브라우저 버전을 기록하지 않았다)

### 1. 한 명령으로 기동, 터널 HTTPS에서 테스트 페이지 열림
- [x] `docker compose -f infra/compose.yml --env-file .env up -d` 후 `http://localhost:8000` 열림
- [x] `cloudflared tunnel --url http://localhost:8000`의 https 주소가 PC와 휴대폰에서 열림, 첫 페이지의 `secure context(HTTPS): true`

결과: 2026-11-06 사용자 확인. 로컬 스택 기동과 Cloudflare Quick Tunnel HTTPS 접속이 PC·휴대폰에서 됨.

### 2. PC 화면 공유 + 탭 소리 + 마이크 → 같은 와이파이 휴대폰에서 WHEP 시청
1. PC Chrome에서 터널 주소의 `publish-pc.html`, path `rec/pc1`, 송출 시작
2. 공유 대상으로 소리가 나는 탭(예: 유튜브)을 고르고 "탭 오디오 공유" 켜기, 마이크 권한 허용
3. 로그의 `오디오 소스: 화면 소리 + 마이크` 확인
4. 휴대폰에서 `watch.html?path=rec/pc1` → 재생, `재생 방식: WHEP (WebRTC)` 확인
- [x] 영상 보임
- [x] 탭 소리와 마이크 소리가 둘 다 들림
- [x] 탭 오디오 공유를 끄고 송출해도 마이크만으로 송출됨

결과: 2026-11-06 사용자 확인. 휴대폰(같은 와이파이)에서 WHEP로 실시간 시청, 탭 소리·마이크 모두 들림. MediaMTX 로그상 `rec/pc1`이 `2 tracks (H264, Opus)`로 송출됨.

### 3. 휴대폰 카메라 + 마이크 송출 → PC 시청
1. 휴대폰에서 `publish-mobile.html`, path `live/m1`, 전면/후면 선택 후 송출 시작
2. PC에서 `watch.html?path=live/m1`
- [x] Android Chrome 송출 → PC 시청(영상+소리)
- [x] iOS Safari 송출 → PC 시청(영상+소리)
- [x] 송출 중 화면이 꺼지지 않음(`Wake Lock: 켜짐`)
- [x] 후면 카메라 선택이 동작함

결과: 2026-11-06 사용자 확인. 휴대폰 카메라 송출(`rec/mobile1`)을 PC에서 시청. 측정값: WHEP, H264 720×1280 30fps, Opus 음량 변화 확인, 미디어는 PC 내부 IP `:8189/udp` 직결.

### 4. `setCodecPreferences`로 H264 강제
- [x] PC 송출 페이지 코덱 표시에 `video: video/H264`
- [x] Android Chrome 송출 페이지에 `video/H264`
- [x] iOS Safari 송출 페이지에 `video/H264`
- [x] (참고) MediaMTX 로그에 `is publishing to path ..., 2 tracks (H264, Opus)`

결과: 2026-11-06 확인. PC·휴대폰 송출 모두 MediaMTX에 H264+Opus로 들어옴.

### 5. WHEP 실패 시 HLS 폴백, LTE에서 터널로 HLS 시청
1. PC에서 송출 중인 상태로 둔다
2. 휴대폰 와이파이를 끄고 LTE로 터널 주소의 `watch.html?path=rec/pc1` → 재생
- [x] WHEP가 시간 초과(약 8초)로 실패하고 `재생 방식: HLS (...)`로 바뀜
- [x] LTE에서 HLS 영상·소리가 나옴(지연 수 초 예상)
- [x] iOS Safari는 `HLS (네이티브)`로 재생됨(Opus 포함 HLS 재생 여부 기록)
- [x] `HLS 강제` 체크로 와이파이에서도 HLS 재생됨

결과: 2026-11-06 사용자 확인. LTE에서 WHEP 실패 후 HLS로 폴백되어 재생됨.

### 6. 녹화 → faststart MP4 → iOS Safari 재생
1. `rec/<id>` path로 30초 이상 송출 후 종료
2. `recordings.html`에서 새 파일 확인(MediaMTX 로그의 `[remux] ... -> /vod/...`)
- [x] PC Chrome에서 재생, 탐색(seek) 동작
- [x] Android Chrome에서 재생
- [x] iOS Safari에서 영상과 소리 재생. 안 되면 증상(무음, 재생 불가 등)과 AAC 트랜스코딩 필요 여부를 적는다

결과: 2026-11-06 사용자 확인. 녹화 MP4가 생성되고 iOS Safari 포함 재생됨. AAC 트랜스코딩은 필요 없음.

### 7. 문서
- [x] 이 문서의 결과 칸을 채우고, 결정이 바뀐 점이 있으면 `docs/decisions.md`에 반영

## 알려진 점과 문제 해결

- **HLS 변형**: 기본 LL-HLS는 브라우저 WebRTC 송출에서 part 길이가 흔들려 iOS 오류 경고가 나서 일반 fMP4 HLS(`hlsVariant: fmp4`)로 바꿨다. 지연이 더 길다.
- **WHEP이 휴대폰에서 안 붙음**: `.env`의 `WEBRTC_ADDITIONAL_HOSTS`가 현재 PC IP인지, 같은 와이파이인지, 방화벽·AP 격리 여부를 확인한다. `watch.html`이 자동으로 HLS로 넘어가므로 재생 방식 표시를 꼭 본다.
- **WHIP 송출 거절**: `overridePublisher: no`라 같은 path에 이미 송출 중이면 거절된다. 다른 path를 쓰거나 기존 송출을 끈다. `rec/`·`live/` 이외의 path도 거절된다.
- **Chrome 자동 재생 차단**: 재생 버튼을 한 번 더 누른다.
- **녹화 파일**: 원본은 `infra/data/recordings`, 재생용은 `infra/data/vod`. 지우려면 스택을 내린 뒤 `infra/data`를 지운다.
- **MediaMTX 버전**: `latest-ffmpeg` 태그를 쓴다(스파이크 시점 v1.21.3). 방송 상태 전이 훅은 `runOnAvailable`/`runOnUnavailable`을 쓴다(옛 이름 `runOnReady`/`runOnNotReady`는 이 버전 설정에 없다).

## 토스페이먼츠 테스트 키 (후원, #21)

후원 결제는 토스페이먼츠 **테스트 모드**의 결제위젯을 쓴다. 테스트 키로는 실제 돈이 나가지 않는다. 키 발급과 등록은 사람이 한다.

1. [토스페이먼츠 개발자센터](https://developers.tosspayments.com)에 가입·로그인한다. 전자결제 이용 신청(사업자 등록 필요)은 하지 않는다.
2. [API 키] 메뉴의 **주문서형·결제창형 연동 키**(결제위젯 연동 키)를 쓴다. 이용 신청 전에는 문서 예제와 같은 공용 테스트 키(`test_gck_docs_…`, `test_gsk_docs_…`)가 보이고, 이 키로 테스트 결제가 된다. 같은 화면의 API 개별 연동 키(`test_ck_`·`test_sk_`)와 보안 키는 쓰지 않는다.
3. 결제 UI(`DEFAULT`)와 약관 UI(`AGREEMENT`)는 기본으로 있다.
4. 키를 넣는다.
   - **시크릿 키**: 서버 환경변수 `TOSS_SECRET_KEY`. VM은 저장소 루트 `.env`, 로컬 bootRun은 `server/.env`에 넣는다. 저장소에는 넣지 않는다. VM은 넣은 뒤 `docker compose up -d app`으로 다시 띄운다.
   - **클라이언트 키**: 프론트 빌드 환경변수 `VITE_TOSS_CLIENT_KEY`. 비밀값이 아니라 운영 값은 `client/.env.production`에 저장소째 둔다. 로컬은 `client/.env.local`에 넣는다(`client/.env.example` 참고).
   - 클라이언트 키가 비어 있으면 후원 패널이 "결제 설정이 없어 지금은 후원할 수 없습니다"를 띄우고, 시크릿 키가 비어 있으면 결제 승인이 거절된다(409).
5. 확인: 다른 계정의 LIVE 방송 상세에서 "후원하기" → 금액·메시지 → 결제위젯에서 카드 결제(테스트 모드라 카드 정보 없이 진행되거나 아무 값이나 통과한다) → 성공 페이지에 "후원했습니다"가 뜨고 DB `donation.status`가 `DONE`이 된다. 결제가 실패하면 실패 페이지가 뜨고 `CANCELED`가 된다. 결제창을 그냥 닫으면 토스가 주문 ID를 주지 않아 `PENDING`으로 남는다(내역·합계는 `DONE`만 센다).

successUrl·failUrl은 프론트 출처(`/donations/success`, `/donations/fail`)라 개발자센터에 따로 등록하지 않는다. 웹훅 등록은 #22에서 다룬다.
