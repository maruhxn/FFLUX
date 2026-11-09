# 설계 결정 사항

`요구사항명세서.md`를 구현하기 위해 합의한 결정 사항이다. 일정: 1인 개발, 11/19 마감, 필수·선택 요구사항 전부 구현.

## 1. 인프라

- GCP Compute Engine VM 1대(서울 `asia-northeast3`, `e2-standard-2`) + Docker Compose: Spring Boot, MySQL 8, MediaMTX(`bluenviron/mediamtx:latest-ffmpeg`), Caddy.
  - 처음엔 AWS였으나 바꿨다. Oracle Cloud Always Free는 가입(카드 확인)이 되지 않았고, GCP의 상시 무료 VM(`e2-micro`, 1GB, 미국 리전)은 스택을 올리기에 작다. 그래서 신규 크레딧(90일)으로 서울 리전 VM을 쓴다. 마감(11/19)과 평가 기간이 크레딧 기간 안에 들어온다.
  - 외부 IP는 고정(예약) IP로 잡아 `webrtcAdditionalHosts`와 DNS에 쓴다. 방화벽은 VPC 방화벽 규칙으로 연다.
- 도메인 `fflux.kro.kr` 하나 + Caddy 자동 HTTPS(`getDisplayMedia`/`getUserMedia`가 HTTPS에서만 동작). 모든 경로를 같은 호스트에 두므로 서브도메인은 쓰지 않는다. `kro.kr`은 Public Suffix List에 없어 Let's Encrypt 발급 한도를 다른 `kro.kr` 사용자와 공유한다. 발급이 막히면 Caddy의 ZeroSSL 폴백에 기댄다.
- 같은 도메인(same-origin)으로 묶는다: 프론트 정적 파일, `/api` → Spring, `/media` → MediaMTX WHIP/WHEP/HLS.
- `/internal/*`은 Caddy에서 라우팅하지 않고, Spring 8080 포트는 호스트에 publish하지 않는다(네트워크 격리로 내부 API 보호).
- WebRTC 미디어 포트 UDP/TCP 8189를 VM에서 직접 개방. TURN 없이 시작하고, VM 배포 후 LTE 확인에서 실패하면 coturn 추가.
- 비밀값은 VM `.env`에만 두고 저장소에는 `.env.example`만 커밋.
- 개발 단계 비용을 줄이려고 개발은 로컬에서 한다. 같은 Docker Compose 스택을 PC에 띄우고, 휴대폰 접속용 HTTPS는 무료 터널(Cloudflare Quick Tunnel 또는 ngrok)로 붙인다.
  - 터널은 HTTP만 전달하므로 WHIP/WHEP 시그널링과 HLS는 터널로, WebRTC 미디어는 같은 와이파이에서 PC 내부 IP(`webrtcAdditionalHosts`)로 직접 연결한다.
  - 로컬에서는 같은 와이파이의 WebRTC 송출·시청과 LTE의 HLS 시청까지만 확인한다. LTE의 WebRTC 시청과 TURN 필요 여부는 VM 배포 단계에서 확인한다.
- VM은 PC 송출·시청·종료가 갖춰진 뒤 처음 올려 LTE를 확인한다. 크레딧으로 운영하므로 확인 이후에는 평가가 끝날 때까지 켜 둔다.
- 배포는 처음엔 VM에서 `git pull && docker compose up -d --build`, CI/CD(GitHub Actions)는 마무리 단계에서 추가.

## 2. 스트리밍

- MediaMTX 셀프호스팅. 송출은 WHIP, 시청은 WHEP(WebRTC)이며 실패 시 HLS로 자동 폴백.
- 송출 코덱은 `setCodecPreferences`로 H264 강제, 오디오는 Opus.
- PC 송출: 화면 공유 소리(탭/시스템)와 마이크를 Web Audio API로 믹싱해 오디오 트랙 하나로 송출.
- 모바일 송출: 별도 앱 없이 모바일 웹에서 카메라+마이크 송출. 송출 중 Wake Lock으로 화면 꺼짐 방지.
  - 진입은 주소 하나(`/broadcasts/:id/publish`)이고, 화면 공유를 못 하거나(`getDisplayMedia` 없음) 주 입력이 터치(`pointer: coarse`)인 기기는 카메라 송출 화면이 열린다.
  - 전·후면 카메라는 송출 전과 송출 중 모두 바꿀 수 있다. 송출 중에는 연결을 유지하고 `replaceTrack`으로 트랙을 갈아 끼운다(마이크는 복제본으로). iOS는 카메라를 하나만 열 수 있어 지금 카메라를 끈 뒤 반대쪽을 연다.
  - 카메라 트랙이 끝나면(운영체제의 카메라 회수) PC의 "공유 중지"처럼 종료를 요청한다. 앱 전환 때 꺼진 것일 수 있으므로 송출 끊김으로 안내한다.
  - 이 탭이 종료를 요청하지 않았는데 방송이 끝나면 송출 화면에 송출 끊김 안내(앱 전환, 화면 꺼짐, 네트워크 끊김)를 보여 준다.
- 지원 환경(README에 명시)
  - PC 송출: Chrome/Edge 최신 (시스템 소리는 Windows만, macOS는 탭 소리만)
  - 모바일 송출: Android Chrome, iOS Safari
  - 시청: 최신 브라우저 전부

## 3. 방송 도메인과 상태

- 방송 1건 = 1회성 세션. 상태는 `READY → LIVE → ENDED`이고 ENDED에서 다시 송출할 수 없다.
- 방송 상태 전이는 방송 행을 `SELECT … FOR UPDATE`로 잠근 뒤 엔티티 메서드가 판단한다(전이 / 이미 목표 상태면 no-op 성공 / 위반). 삭제된 방송은 전이 대상이 아니다. 동시에 들어오는 종료 요청과 송출 끊김은 잠금이 순서를 정한다.
- 값 하나를 한 번만 바꾸는 단순 갱신(송출 토큰 1회 소비, 후원 확정)은 조건부 UPDATE로 한다.
- `READY → ENDED` 전이는 없다. 송출하지 않은 방송은 삭제로만 정리한다.
- 방송 등록 시 `READY`로 생성된다. 한 스트리머가 `READY` 방송을 여러 개 가질 수 있지만, 동시에 `LIVE`인 방송은 하나뿐이다.
  - 송출 토큰 발급과 `publish` 인증에서 같은 스트리머의 다른 `LIVE` 방송이 있으면 409/거절.
  - 두 방송을 거의 동시에 송출하면 둘 다 검사를 통과해 `LIVE`가 될 수 있다. 1인 개발 일정상 이 경합은 받아들인다.

### 송출 토큰

1. 스트리머가 `방송 시작` 모달에서 녹화 여부를 고르고 `POST /api/broadcasts/{id}/publish-token`(body: `record`)을 호출한다.
2. Spring이 세션 인증, 스트리머 본인 여부, `READY` 상태, 이미 소비된 토큰 없음, 같은 스트리머의 다른 `LIVE` 방송 없음을 검증한다. 토큰을 한 번 쓴 방송은 LIVE가 되기 전이라도 재발급하지 않는다(재발급이 `mediaPath`를 바꾸면 늦게 온 `available` 훅이 방송을 찾지 못한다). 송출 연결이 실패한 방송은 새로 등록한다.
3. 같은 방송의 미소비 토큰을 무효화하고, 암호학적 랜덤 토큰을 새로 발급한다(TTL 약 1분, 방송 ID에 바인딩, DB에는 SHA-256 해시만 저장).
4. 원본 토큰과 WHIP URL을 반환한다.

### MediaMTX 인증 콜백 (`authHTTPAddress` → `POST /internal/mediamtx/auth`)

| action | 처리 |
|---|---|
| `publish` | 토큰 해시 존재·미만료·미소비, 바인딩된 방송 ID와 path 일치, 방송 `READY`·미삭제, 같은 스트리머의 다른 `LIVE` 방송 없음 확인 후 조건부 UPDATE로 원자적 소비(영향 행 1개일 때만 성공) |
| `read` | 토큰 불필요, 방송이 `LIVE`·미삭제일 때만 허용 |
| `api` | `authHTTPExclude`로 제외, API 포트는 내부망에만 노출 |
| 그 외 | 거절 |

- 중복 송출은 MediaMTX `overridePublisher: no`로 막는다. 따라서 `PUBLISHING` 상태와 `broadcast_session` 테이블은 두지 않는다.
- MediaMTX path: 녹화하면 `rec/{broadcastId}`, 녹화하지 않으면 `live/{broadcastId}`. 방송에 `mediaPath`를 저장하고 인증 시 대조한다.

### 방송 상태 전이 module

`broadcast` 모듈 application 계층에 방송 상태 전이와 송출·시청 허가 판단을 맡는 module 하나를 둔다. 진입점(MediaMTX 인증 콜백, 훅 컨트롤러, 종료 API)은 이 module을 부르기만 한다.

| 호출하는 곳 | 메서드 | 입력 | 결과 |
|---|---|---|---|
| MediaMTX `publish` 인증 | 송출 허가 | media path, 원본 토큰 | 허용/거절(예외 없음). 같은 트랜잭션에서 토큰을 조건부 UPDATE로 소비 |
| MediaMTX `read` 인증 | 시청 허가 | media path | 허용/거절 |
| `available` 훅 | 송출 시작 확인 | media path | 없음. `READY → LIVE`, 할 수 없으면 경고 로그 |
| `unavailable` 훅 | 송출 끊김 | media path | 없음. `LIVE → ENDED`, 할 수 없으면 경고 로그 |
| 종료 API | 종료 요청 | 방송 ID, 회원 ID | 성공, 또는 403/404/409 예외 |

- media path를 방송으로 찾는 일은 이 module 안에서 한다.
- 이벤트(`BroadcastStatusChanged`)는 실제로 전이가 일어났을 때만 발행한다.
- kick은 종료 요청에서만, 전이 트랜잭션이 커밋된 뒤 같은 호출 안에서 부른다. 실패하면 로그만 남기고 종료 요청은 성공으로 응답한다(상태는 이미 ENDED이고, 이후 송출 끊김 훅은 no-op).
- 훅은 상태가 맞지 않거나 방송이 없어도 항상 200으로 응답하고 경고 로그만 남긴다. 409는 종료 요청에만 쓴다.
- 송출 토큰 자체(해시 저장, TTL, 발급·무효화)는 이 module 밖에 둔다.

### 상태 전이 트리거

- `READY → LIVE`: MediaMTX `runOnAvailable` 훅(스트림을 읽을 수 있게 된 시점, 실제 트랙 수신)이 `/internal/mediamtx/available`을 호출.
- `LIVE → ENDED`(송출 끊김): `runOnUnavailable` 훅이 `/internal/mediamtx/unavailable`을 호출해 즉시 ENDED. 모바일 앱 전환 시 방송이 종료됨을 README에 명시.
- `LIVE → ENDED`(종료 요청): 종료 API 호출 → 세션·스트리머 본인 검증 → ENDED → MediaMTX API로 송출 세션 kick → 클라이언트가 트랙과 PeerConnection 정리. `LIVE`가 아닌 방송에 종료 API를 호출하면 409(이미 `ENDED`면 성공).
- 방송을 끝내는 것은 종료 요청과 송출 끊김뿐이다. 스트리머가 사이트 안에서 다른 페이지로 옮겨도 송출은 이어진다(송출 연결을 송출 화면이 아니라 앱 전체에 하나 둔다). 탭 닫기·새로고침은 브라우저가 연결을 끊으므로 막을 수 없어 확인창만 띄우고, 실제로 닫히면 송출 끊김으로 ENDED. 브라우저의 "공유 중지"는 종료 요청으로 본다.

### 방송 관리 규칙

- 수정: `READY`, `LIVE`에서 스트리머만. `ENDED`는 수정 불가. `LIVE` 중 수정은 SSE `broadcast-updated` 이벤트로 시청자 화면에 바로 반영한다.
- 삭제: `LIVE` 중에는 409. `READY`/`ENDED`는 soft delete. 녹화와 댓글은 함께 숨기고 후원 기록은 보존. 삭제 시 미소비 송출 토큰을 무효화한다.
- 목록 노출
  - 메인: `LIVE` 방송
  - 지난 방송: `ENDED` 방송, 다시보기가 있으면 배지
  - 내 방송: `READY` 포함, 스트리머 본인만
  - soft delete된 방송은 어디에도 노출하지 않음
  - 세 목록 모두 최신순(방송 ID 내림차순) 커서 페이지네이션, 한 번에 20개
- 방송 정보: 제목(필수, 50자 이하, 앞뒤 공백 제거), 설명(선택, 500자 이하)
- 스트리머 닉네임은 방송 등록 시점 스냅샷으로 방송에 저장한다(채팅·댓글 작성자 닉네임과 같은 방식). 목록·상세가 `member` 모듈을 조회하지 않는다. 닉네임 변경 기능은 없다.

## 4. 실시간 갱신

- 방송별 SSE 하나(`GET /api/broadcasts/{id}/events`)로 상태 변화, 방송 정보 수정, 채팅, 후원 알림을 보낸다. 쓰기는 REST.
- Spring MVC `SseEmitter`, 서버 1대이므로 인메모리 허브.

### 시청자 수

- 방송별 SSE 구독 중 로그인한 회원만 회원 ID 단위로 센다. 같은 회원이 같은 방송을 여러 탭으로 봐도 1명이고, 그 회원의 마지막 연결이 끊기면 빠진다. 비로그인 구독과 그 방송의 스트리머는 세지 않는다. 비로그인 시청자를 같은 브라우저 단위로 묶으려면 익명 쿠키나 세션이 필요해서, 그 비용 대신 로그인 회원만 세기로 했다. 비로그인 시청자가 0명으로 보이는 점은 README에 적는다.
- MediaMTX reader 수는 쓰지 않는다. HLS 폴백 시청자가 muxer 하나로 묶여 1로 잡히기 때문이다.
- 방송 상태와 관계없이 세고 보낸다. 화면은 LIVE일 때만 보여 준다. 허브가 방송 상태를 따로 추적하지 않기 위해서이고, READY 상세에서 기다리던 회원이 시작 순간 바로 잡힌다.
- 상세·송출 화면: SSE `viewer-count`(`{count}`) 이벤트. 5초 주기로 값이 바뀐 방송에만 보내고, 새 구독자는 연결 직후 현재 값을 한 번 받는다. 끊긴 연결은 쓰기에 실패해야 빠지므로 줄어든 값은 늦게 반영된다. 이 지연을 줄이려고 heartbeat를 5초로 둔다. 로컬 측정: 서버 직결 2초, Caddy 경유 17초(Caddy도 클라이언트 쪽 쓰기가 실패해야 upstream을 닫는다), Vite 개발 프록시 경유 40~70초(개발 환경에서만).
- 목록·상세 응답: LIVE 방송의 `viewerCount`에 조회 시점 값을 담는다(LIVE가 아니면 null). 목록은 실시간 갱신이 없다.
- 구독 API는 비로그인을 허용하는 공개 API이므로, 공개 API도 로그인 회원을 `LoginMember?`(nullable)로는 받을 수 있게 했다.
- 저장하지 않는다(메모리 값). 최고·누적 기록도 없다. 서버 재시작 후에는 SSE 재연결로 회복된다.

### 방송 경과 시간

- 기준은 송출 시작 확인(`available` 훅)으로 LIVE가 된 시각이다. 방송에 `startedAt`으로 저장하고 상세·목록 응답에 담는다. 등록 시각(`createdAt`)은 등록 후 송출까지 기다린 시간이 섞여서 쓰지 않는다.
- 화면은 `startedAt`과 기기 시계의 차를 1초마다 `HH:MM:SS 스트리밍 중`으로 그린다(24시간을 넘으면 시간이 계속 늘어난다). `startedAt`은 시간대 없는 서울 시각이므로 화면이 `+09:00`을 붙여 읽는다(기기 시간대와 무관). 기기 시계가 틀리면 그만큼 틀리고, 음수면 0으로 보인다. 서버가 경과 초를 계산해 주는 방식은 응답이 시각이 아니라 수치가 되어 택하지 않았다.
- 상세와 송출 화면에만 보여 준다. 목록 카드에는 없다.

## 5. 녹화와 다시보기

- 녹화 여부는 방송 시작 시 선택(`broadcast.recordingEnabled`). `rec/` path에만 MediaMTX `record: yes`.
- `recordSegmentDuration`을 길게(예: 12h) 잡아 방송 1건 = 파일 1개.
- `runOnRecordSegmentComplete` 훅에서 `ffmpeg -c copy -movflags +faststart`로 MP4 리먹싱 후 Spring에 통지.
- `Recording`: broadcastId, 파일 경로, 길이, 생성 시각, 상태(`PROCESSING → READY / FAILED`). 다시보기 목록에는 `READY`만 노출.
- Caddy가 녹화 디렉터리를 정적 파일로 서빙(Range 지원), 프론트는 `<video>`로 재생.
- iOS Safari에서 Opus 포함 녹화 MP4 재생을 로컬 스파이크(#2)에서 확인했다. AAC 트랜스코딩은 하지 않는다.
- HLS 폴백은 LL-HLS 대신 일반 fMP4 HLS(`hlsVariant: fmp4`)를 쓴다. 브라우저 WHIP 송출에서 LL-HLS part 길이가 흔들려 iOS 호환 경고가 났기 때문이다(지연은 더 길다).

## 6. 채팅과 댓글

| | 채팅 | 댓글 |
|---|---|---|
| 작성 가능 조건 | 방송 `LIVE` | 방송 `ENDED` + `READY` 녹화 존재 (아니면 409) |
| 수정·삭제 | 불가 | 작성자만 |
| 전달 | REST 작성 + SSE 실시간 반영 | REST |
| 조회 | 입장 시 최근 50개 + 이후 SSE | 커서 기반 페이지네이션(최신순) |
| 저장 | DB 저장, 다시보기에서는 숨김 | DB 저장 |

- 작성자 닉네임은 작성 시점 스냅샷으로 저장.
- 치지직처럼 라이브에는 채팅, 다시보기에는 댓글을 둔다. 녹화하지 않은 방송에는 댓글이 없으므로 README 확인 절차에 "녹화를 켜고 방송 → 다시보기에서 댓글 확인"을 적는다.
- 댓글 수정·삭제는 작성자만 한다. 스트리머도 남의 댓글을 지울 수 없다.
- 후순위 후보: 채팅 다시보기(라이브 당시 채팅을 녹화 재생 시점에 맞춰 표시). 녹화 시작 시각과 채팅 시각을 맞추는 작업이 필요해 지금은 하지 않는다.

## 7. 후원

- 토스페이먼츠 테스트 모드.
- 흐름: 서버가 `Donation(PENDING, orderId, amount, broadcastId, donorId, streamerId)` 생성 → 결제위젯 결제 → successUrl 복귀 → 서버가 PENDING 금액과 요청 금액 일치, 토스 승인 API 결과, 후원 대상 확인 → `DONE`.
- 웹훅도 수신. `orderId` 유니크 + `PENDING → DONE` 1회 전이로 같은 승인 통지가 반복돼도 한 번만 적립.
- 후원 가능 시점: `LIVE`, `ENDED` 모두. 대상은 방송의 스트리머로 고정.
- 스트리머는 자기 방송에 후원할 수 없다. 서버에서 403으로 거절하고, 자기 방송 화면에서는 후원 버튼을 숨긴다.
- 금액: 1,000/5,000/10,000원 프리셋 + 직접 입력. 방송별 후원 내역과 합계 표시, LIVE 중에는 SSE로 알림.

## 8. 인증과 에러

- Spring Security 세션 방식(HttpOnly 쿠키), Spring Session JDBC로 MySQL에 세션 저장. 세션 테이블은 Flyway가 만든다.
- CSRF는 세션 쿠키 `SameSite=Lax`로 막고 Spring Security CSRF 토큰은 쓰지 않는다. 모든 경로가 같은 도메인이고, 최신 브라우저는 다른 사이트의 POST·PUT·DELETE에 Lax 쿠키를 싣지 않는다. 운영(HTTPS)에서는 쿠키에 `Secure`를 붙인다.
- 로그인 API: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, 가입은 `POST /api/members`. 로그인 시 세션 ID를 바꾼다(세션 고정 방지).
- 회원 필드: `username`(유니크, 영문 소문자·숫자·`_` 4~20자), `password`(BCrypt, 8~64자), `nickname`(2~12자). 이메일 인증·소셜 로그인 없음.
- 테스트 계정 `streamer`, `viewer`를 앱 기동 시 시드한다. 비밀번호는 환경변수(`TEST_STREAMER_PASSWORD`, `TEST_VIEWER_PASSWORD`)로만 받고, 비어 있으면 그 계정은 만들지 않는다.
- 로그인 화면의 "테스트 계정으로 로그인" 버튼은 `POST /api/auth/test-login`(body: `account`)을 부른다. 평가자가 비밀번호 없이 들어오게 하려는 것이라, 이 두 계정에만 비밀번호 없는 로그인을 허용한다(`TEST_ACCOUNTS_ENABLED`로 끌 수 있음).
- 에러 응답은 `ProblemDetail` + `@RestControllerAdvice`로 통일.
  - 400 검증 실패, 401 비로그인, 403 스트리머·작성자 아님, 404 대상 없음, 409 상태 위반

## 9. 서버 아키텍처

### Gradle 멀티모듈 (모듈러 모놀리스)

```
server/
├─ app/            # @SpringBootApplication, Security·설정, 모듈 간 어댑터(조립 지점)
├─ common/         # shared kernel: 이벤트 계약, 공통 예외, LoginMember·@PublicApi 등
└─ modules/
   ├─ member/      # 회원가입·로그인·인증
   ├─ broadcast/   # 방송 CRUD·상태 머신·송출 토큰·MediaMTX 인증/훅/kick
   ├─ recording/   # 녹화 훅·Recording·다시보기
   ├─ chat/
   ├─ comment/
   ├─ donation/    # 토스 연동 포함
   └─ realtime/    # 방송별 SSE 허브
```

- `modules/*`는 `common`에만 의존하고, `app`만 모든 모듈에 의존한다. 모듈 간 참조 금지는 Gradle 컴파일 단계에서 강제된다.
- 모듈 간 동기 조회: 필요한 모듈이 자기 `application.required`에 포트를 선언하고, `app` 모듈 어댑터가 상대 모듈의 `application.provided`를 호출해 구현한다.
- 모듈 간 비동기 전파: `common`의 이벤트 계약(원시 타입·ID만)을 `EventPublisherPort`로 발행하고, 수신 모듈의 `port.event`에서 `@TransactionalEventListener(AFTER_COMMIT)`로 받아 자기 provided 유스케이스를 호출한다.
- 모듈 경계를 넘는 엔티티 참조는 ID 값만 둔다. JPA 연관관계와 DB FK는 두지 않는다. 같은 모듈 안에서는 허용한다.

### 모듈 내부 패키지 (헥사고날)

```
com.maruhxn.fflux.<module>
├─ port
│  ├─ web            # Controller, request/response DTO
│  ├─ persistence    # Spring Data 리포지토리 (required 포트 구현)
│  ├─ event          # 이벤트 리스너 (인바운드)
│  └─ <external>     # mediamtx, toss 등 외부 클라이언트 / internal 훅 컨트롤러
├─ application
│  ├─ provided       # 유스케이스 인터페이스 + Command/Result DTO
│  ├─ required       # 아웃바운드 포트 인터페이스
│  └─ (root)         # 유스케이스 구현 서비스, @Transactional
└─ domain            # JPA 엔티티(=도메인 모델), enum, 값 객체, 도메인 예외
```

- 의존 흐름은 항상 밖(port)에서 안(domain)으로 향한다.
- domain에서는 예외적으로 JPA 엔티티를 도메인 모델로 쓴다(엔티티와 모델 일치 허용).
- 영속성 어댑터는 `interface BroadcastJpaRepository : JpaRepository<Broadcast, Long>, BroadcastRepository` 형태로 required 포트를 구현한다.
- provided 유스케이스는 엔티티가 아니라 Result DTO를 반환한다.

### ArchUnit 규칙 (`app` 모듈의 `ArchitectureTest`)

1. domain은 port와 application을 참조하지 않는다.
2. domain은 허용 목록만 참조한다: Kotlin/Java 표준 라이브러리(Kotlin 컴파일러가 붙이는 `org.jetbrains.annotations` 포함), `jakarta.persistence`, Hibernate, `org.springframework..`, `common`, 자기 모듈 domain. 단, KSP가 생성한 QueryDSL Q 클래스는 이 규칙에서 제외한다.
3. application은 port를 참조하지 않는다.
4. `port.web`은 `application.required`를 참조하지 않는다.
5. `port.persistence` 외의 port는 `@Entity` 클래스를 참조하지 않는다. enum, 값 객체 등 그 밖의 domain 클래스는 참조할 수 있다.
6. 모듈끼리 서로 참조하지 않는다(`common`, `app` 제외).
7. `app`의 어댑터는 각 모듈의 `application.provided`/`application.required`만 참조한다.

### 영속성과 시간

- 스키마는 Flyway 마이그레이션으로 관리하고 `ddl-auto=validate`.
- 동적 쿼리(조건 조합, 커서 페이지네이션)는 QueryDSL(OpenFeign 포크, KSP 생성)로 쓰고 `port.persistence`의 커스텀 리포지토리에만 둔다. 단순 조회와 조건부 UPDATE는 Spring Data 메서드·`@Query`.
- 시간대는 `Asia/Seoul` 하나로 고정(JVM, `Clock`, MySQL, Hibernate JDBC). 시각 타입은 `LocalDateTime`.
- 세부 규칙은 `.claude/rules/server-code-convention.md`.

### 인증 배치

- Spring Security 설정(필터 체인, 세션, 401/403 처리)은 `app`.
- 자격 증명 확인은 `member`의 provided 유스케이스(`MemberAuthenticator`).
- 세션 principal은 `common`의 값 타입 `LoginMember(memberId, nickname)`이다.
- 다른 모듈의 web 포트는 컨트롤러 파라미터 `member: LoginMember`로 현재 사용자를 받는다. 이 파라미터가 있으면 로그인이 필요하다는 뜻이고(`LoginMember?`면 비로그인은 null), Spring Security 타입은 알지 못한다.
- `/api`는 기본 거절이다. 비로그인으로 열 핸들러 메서드에만 `common`의 `@PublicApi`를 붙이고, `app`의 인가 규칙이 요청을 처리할 핸들러의 이 어노테이션을 보고 허용한다. 공개 경로를 Security 설정에 URL로 나열하지 않으므로 새 API가 `app`을 고치지 않는다. URL 목록 방식은 모듈마다 `app`을 고쳐야 해서, 시그니처만으로 401을 정하고 URL은 모두 여는 방식은 실수가 열린 쪽으로 나서 택하지 않았다.
- `@PublicApi` 메서드는 `LoginMember`를 nullable(`LoginMember?`)로만 받는다(앱 시작 시 검사). 로그인 여부와 상관없이 열되 로그인했으면 회원을 알아야 하는 API(방송별 SSE의 시청자 수)에 쓴다.
- 스트리머·작성자 권한 확인은 각 모듈의 엔티티 메서드가 행위자 ID를 받아 한다(403 → 409 순서). application 서비스는 엔티티를 찾아 위임한다.

## 10. 프론트엔드

- React + Vite + TypeScript, React Router, TanStack Query, Tailwind CSS. 별도 상태 관리 라이브러리는 쓰지 않는다.
- 반응형 화면 하나, 송출 화면만 PC용(화면 공유)과 모바일용(카메라)으로 분리.
- 송출 중에는 헤더에 "송출 중" 표시를 두어 어느 페이지에서든 송출 화면(방송 종료 버튼)으로 돌아가게 한다.

## 11. 테스트

- 인가 매트릭스(비로그인/스트리머·작성자/타인 × API)를 Testcontainers MySQL 통합 테스트로 검증.
- 상태 전이, 토큰 소비, 후원 멱등을 테스트.
- 프론트·미디어 E2E는 자동화하지 않고 README의 수동 확인 절차로 대체.

## 12. 작업 순서

1. 로컬 미디어 스파이크(로컬 Docker Compose + HTTPS 터널): PC 화면 공유+마이크 송출 → 휴대폰 시청(같은 와이파이), 휴대폰 카메라 송출 → PC 시청, H264 강제, HLS 폴백(LTE 포함), 녹화 MP4 재생(iOS 포함)
2. 멀티모듈 뼈대, `common`, ArchUnit
3. 인증
4. 방송 도메인: CRUD, soft delete, 상태 머신, 송출 토큰, MediaMTX 인증 콜백·훅, kick
5. 송출·시청 UI(PC/모바일), 종료 흐름, SSE 상태 반영, Wake Lock
6. GCP VM 배포와 LTE 실기기 확인(PC 송출·시청·종료가 갖춰진 직후), 필요하면 coturn
7. 필수 기능 QA, 인가 테스트, README, 실기기 확인 ← 첫 마일스톤
8. 녹화와 다시보기
9. 채팅
10. 댓글
11. 후원
12. 테스트 보강, README(실행 방법·기능 확인 절차), CI/CD, 최종 QA
