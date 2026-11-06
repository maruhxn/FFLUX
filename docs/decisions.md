# 설계 결정 사항

`요구사항명세서.md`를 구현하기 위해 합의한 결정 사항이다. 일정: 1인 개발, 11/19 마감, 필수·선택 요구사항 전부 구현.

## 1. 인프라

- AWS VM 1대 + Docker Compose: Spring Boot, MySQL 8, MediaMTX(`bluenviron/mediamtx:latest-ffmpeg`), Caddy.
- 도메인 `fflux.kro.kr` 하나 + Caddy 자동 HTTPS(`getDisplayMedia`/`getUserMedia`가 HTTPS에서만 동작). 모든 경로를 같은 호스트에 두므로 서브도메인은 쓰지 않는다. `kro.kr`은 Public Suffix List에 없어 Let's Encrypt 발급 한도를 다른 `kro.kr` 사용자와 공유한다. 발급이 막히면 Caddy의 ZeroSSL 폴백에 기댄다.
- 같은 도메인(same-origin)으로 묶는다: 프론트 정적 파일, `/api` → Spring, `/media` → MediaMTX WHIP/WHEP/HLS.
- `/internal/*`은 Caddy에서 라우팅하지 않고, Spring 8080 포트는 호스트에 publish하지 않는다(네트워크 격리로 내부 API 보호).
- WebRTC 미디어 포트 UDP/TCP 8189를 VM에서 직접 개방. TURN 없이 시작하고, AWS 배포 후 LTE 확인에서 실패하면 coturn 추가.
- 비밀값은 VM `.env`에만 두고 저장소에는 `.env.example`만 커밋.
- 개발 단계 비용을 줄이려고 개발은 로컬에서 한다. 같은 Docker Compose 스택을 PC에 띄우고, 휴대폰 접속용 HTTPS는 무료 터널(Cloudflare Quick Tunnel 또는 ngrok)로 붙인다.
  - 터널은 HTTP만 전달하므로 WHIP/WHEP 시그널링과 HLS는 터널로, WebRTC 미디어는 같은 와이파이에서 PC 내부 IP(`webrtcAdditionalHosts`)로 직접 연결한다.
  - 로컬에서는 같은 와이파이의 WebRTC 송출·시청과 LTE의 HLS 시청까지만 확인한다. LTE의 WebRTC 시청과 TURN 필요 여부는 AWS 배포 단계에서 확인한다.
- AWS VM은 PC 송출·시청·종료가 갖춰진 뒤 처음 올려 LTE를 확인하고, 쓰지 않을 때는 멈춰 둔다.
- 배포는 처음엔 VM에서 `git pull && docker compose up -d --build`, CI/CD(GitHub Actions)는 마무리 단계에서 추가.

## 2. 스트리밍

- MediaMTX 셀프호스팅. 송출은 WHIP, 시청은 WHEP(WebRTC)이며 실패 시 HLS로 자동 폴백.
- 송출 코덱은 `setCodecPreferences`로 H264 강제, 오디오는 Opus.
- PC 송출: 화면 공유 소리(탭/시스템)와 마이크를 Web Audio API로 믹싱해 오디오 트랙 하나로 송출.
- 모바일 송출: 별도 앱 없이 모바일 웹에서 카메라+마이크 송출. 송출 중 Wake Lock으로 화면 꺼짐 방지.
- 지원 환경(README에 명시)
  - PC 송출: Chrome/Edge 최신 (시스템 소리는 Windows만, macOS는 탭 소리만)
  - 모바일 송출: Android Chrome, iOS Safari
  - 시청: 최신 브라우저 전부

## 3. 방송 도메인과 상태

- 방송 1건 = 1회성 세션. 상태는 `READY → LIVE → ENDED`이고 ENDED에서 다시 송출할 수 없다.
- 상태 전이는 조건부 UPDATE(`WHERE status = ... AND deleted_at IS NULL`)로 멱등하게 처리. 이미 목표 상태면 no-op으로 성공한다.
- `READY → ENDED` 전이는 없다. 송출하지 않은 방송은 삭제로만 정리한다.
- 방송 등록 시 `READY`로 생성된다. 한 스트리머가 `READY` 방송을 여러 개 가질 수 있지만, 동시에 `LIVE`인 방송은 하나뿐이다.
  - 송출 토큰 발급과 `publish` 인증에서 같은 스트리머의 다른 `LIVE` 방송이 있으면 409/거절.
  - 두 방송을 거의 동시에 송출하면 둘 다 검사를 통과해 `LIVE`가 될 수 있다. 1인 개발 일정상 이 경합은 받아들인다.

### 송출 토큰

1. 스트리머가 `방송 시작` 모달에서 녹화 여부를 고르고 `POST /api/broadcasts/{id}/publish-token`(body: `record`)을 호출한다.
2. Spring이 세션 인증, 스트리머 본인 여부, `READY` 상태, 같은 스트리머의 다른 `LIVE` 방송 없음을 검증한다.
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

### 상태 전이 트리거

- `READY → LIVE`: MediaMTX `runOnReady` 훅(실제 트랙 수신 시점)이 `/internal/mediamtx/ready`를 호출.
- `LIVE → ENDED`(비정상 종료): `runOnNotReady` 훅에서 즉시 ENDED. 모바일 앱 전환 시 방송이 종료됨을 README에 명시.
- `LIVE → ENDED`(정상 종료): 종료 API 호출 → 세션·스트리머 본인 검증 → ENDED → MediaMTX API로 송출 세션 kick → 클라이언트가 트랙과 PeerConnection 정리. `LIVE`가 아닌 방송에 종료 API를 호출하면 409(이미 `ENDED`면 성공).

### 방송 관리 규칙

- 수정: `READY`, `LIVE`에서 스트리머만. `ENDED`는 수정 불가. `LIVE` 중 수정은 SSE `broadcast-updated` 이벤트로 시청자 화면에 바로 반영한다.
- 삭제: `LIVE` 중에는 409. `READY`/`ENDED`는 soft delete. 녹화와 댓글은 함께 숨기고 후원 기록은 보존. 삭제 시 미소비 송출 토큰을 무효화한다.
- 목록 노출
  - 메인: `LIVE` 방송
  - 지난 방송: `ENDED` 방송, 다시보기가 있으면 배지
  - 내 방송: `READY` 포함, 스트리머 본인만
  - soft delete된 방송은 어디에도 노출하지 않음

## 4. 실시간 갱신

- 방송별 SSE 하나(`GET /api/broadcasts/{id}/events`)로 상태 변화, 방송 정보 수정, 채팅, 후원 알림을 보낸다. 쓰기는 REST.
- Spring MVC `SseEmitter`, 서버 1대이므로 인메모리 허브.

## 5. 녹화와 다시보기

- 녹화 여부는 방송 시작 시 선택(`broadcast.recordingEnabled`). `rec/` path에만 MediaMTX `record: yes`.
- `recordSegmentDuration`을 길게(예: 12h) 잡아 방송 1건 = 파일 1개.
- `runOnRecordSegmentComplete` 훅에서 `ffmpeg -c copy -movflags +faststart`로 MP4 리먹싱 후 Spring에 통지.
- `Recording`: broadcastId, 파일 경로, 길이, 생성 시각, 상태(`PROCESSING → READY / FAILED`). 다시보기 목록에는 `READY`만 노출.
- Caddy가 녹화 디렉터리를 정적 파일로 서빙(Range 지원), 프론트는 `<video>`로 재생.
- iOS Safari의 Opus 포함 MP4/HLS 재생은 스파이크에서 확인하고, 안 되면 AAC 트랜스코딩을 추가.

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

- Spring Security 세션 방식(HttpOnly 쿠키), Spring Session JDBC로 MySQL에 세션 저장.
- 회원 필드: `username`(유니크), `password`(BCrypt), `nickname`. 이메일 인증·소셜 로그인 없음.
- 테스트 계정 `streamer`, `viewer`를 시드로 넣고 로그인 화면에 "테스트 계정으로 로그인" 버튼 제공.
- 에러 응답은 `ProblemDetail` + `@RestControllerAdvice`로 통일.
  - 400 검증 실패, 401 비로그인, 403 스트리머·작성자 아님, 404 대상 없음, 409 상태 위반

## 9. 서버 아키텍처

### Gradle 멀티모듈 (모듈러 모놀리스)

```
server/
├─ app/            # @SpringBootApplication, Security·설정, 모듈 간 어댑터(조립 지점)
├─ common/         # shared kernel: 이벤트 계약, 공통 예외, @LoginMember 등
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
2. domain은 허용 목록만 참조한다: Kotlin/Java 표준 라이브러리, `jakarta.persistence`, Hibernate, `org.springframework..`, `common`, 자기 모듈 domain. 단, KSP가 생성한 QueryDSL Q 클래스는 이 규칙에서 제외한다.
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
- 자격 증명 확인은 `member`의 provided 유스케이스(`AuthenticateMember`).
- 세션 principal에는 `memberId`, `nickname`만 담는다.
- 다른 모듈의 web 포트는 `common`의 `@LoginMember memberId: Long`으로 현재 사용자를 받는다. Spring Security 타입은 알지 못한다.
- 스트리머·작성자 권한 확인은 각 모듈의 application 서비스가 한다.

## 10. 프론트엔드

- React + Vite + TypeScript, React Router, TanStack Query, Tailwind CSS. 별도 상태 관리 라이브러리는 쓰지 않는다.
- 반응형 화면 하나, 송출 화면만 PC용(화면 공유)과 모바일용(카메라)으로 분리.

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
6. AWS 배포와 LTE 실기기 확인(PC 송출·시청·종료가 갖춰진 직후), 필요하면 coturn
7. 필수 기능 QA, 인가 테스트, README, 실기기 확인 ← 첫 마일스톤
8. 녹화와 다시보기
9. 채팅
10. 댓글
11. 후원
12. 테스트 보강, README(실행 방법·기능 확인 절차), CI/CD, 최종 QA
