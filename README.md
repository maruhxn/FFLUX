# FFLUX: 실시간 방송 플랫폼

치지직 같은 실시간 라이브 방송 웹 서비스다. PC 화면 공유 송출, 모바일 카메라 송출, 시청, 방송 관리, 인증·인가를 갖췄다.

## 서비스 주소

- **https://fflux.kro.kr**
- PC와 휴대폰 모두 같은 주소로 접속한다(반응형 웹, 별도 앱 없음).
- API·미디어 서버는 `https://api.fflux.kro.kr`이다. 화면에서 직접 열 일은 없다.

## 테스트 계정

로그인 화면(오른쪽 위 "로그인")의 **"스트리머로 로그인"**, **"시청자로 로그인"** 버튼을 누르면 비밀번호 없이 바로 로그인된다.

| 버튼 | 계정 | 용도 |
|---|---|---|
| 스트리머로 로그인 | `streamer`(닉네임 "테스트스트리머") | 방송 등록·수정·삭제, 송출, 종료 |
| 시청자로 로그인 | `viewer`(닉네임 "테스트시청자") | 다른 사람 방송 시청, 시청자 수, 권한 거절 확인 |

- 직접 가입("회원가입")해서 써도 된다. 아이디는 영문 소문자·숫자·`_` 4~20자, 비밀번호는 8~64자다.
- PC와 휴대폰에서 각각 다른 계정으로 로그인하면 송출과 시청을 함께 확인하기 쉽다.
- 로그아웃은 오른쪽 위 "로그아웃"이다. 로그인 상태는 서버 세션(쿠키)이라 새로고침하거나 서버가 재시작돼도 유지된다.

## 지원 환경

| 기능 | 환경 |
|---|---|
| PC 송출 | Chrome/Edge 최신. 화면 공유에 캠을 우하단 PiP로 겹쳐 보낼 수 있다(선택). 화면 소리는 Windows에서 시스템 소리, macOS에서는 탭 소리만 |
| 모바일 송출 | Android Chrome, iOS Safari. 카메라와 마이크로 송출하고, 송출 중 화면이 꺼지지 않게 한다(Wake Lock). 다른 앱으로 전환하거나 화면을 끄면 송출이 끊겨 방송이 종료된다 |
| 시청 | 최신 브라우저 전부. WebRTC(WHEP)로 연결하고, 안 되면 HLS로 자동 전환한다. Safari(iOS 포함)는 브라우저 기본 HLS, 그 밖의 브라우저는 hls.js로 재생한다 |

실서버에서 PC 송출 → 휴대폰 LTE 시청, 휴대폰 LTE 카메라 송출 → PC 시청을 WebRTC로 확인했다.

## 기능 확인 절차

### 1. 방송 등록·수정·삭제

1. "스트리머로 로그인" 후 위쪽 "방송 등록"에서 제목(50자 이하)과 설명(선택, 500자 이하)을 넣고 등록한다.
2. 등록하면 방송 상세 화면으로 간다. 상태는 "방송 대기 중"(READY)이다. "내 방송"에서 내가 등록한 방송을 모아 볼 수 있다.
3. 상세 화면의 "수정"·"삭제"는 그 방송의 스트리머에게만 보인다. 송출 중(LIVE)인 방송은 삭제할 수 없다.

### 2. PC 송출 (화면 공유)

1. PC Chrome/Edge에서 내 방송 상세의 **"방송 시작"**을 누른다.
2. "녹화하기" 여부를 고르고 "다음"을 누른다. 녹화를 켜면 방송이 끝난 뒤 다시보기로 볼 수 있다(8. 다시보기).
3. "공유할 화면 고르기"로 화면·창·탭을 고른다. 소리를 섞으려면 공유 창에서 "탭 오디오 공유"(macOS) 또는 "시스템 오디오 공유"(Windows)를 켠다. 마이크 소리는 항상 함께 나간다.
4. (선택) "캠 같이 보내기"를 켜면 화면 우하단에 캠 영상이 겹쳐 나간다. 캠이 여러 개면 고를 수 있다.
5. 미리보기를 확인하고 **"송출 시작"**을 누른다. 미디어 서버가 영상을 받으면 몇 초 안에 상태가 "방송 중"(LIVE)으로 바뀌고, 경과 시간(HH:MM:SS)과 시청자 수가 나온다.

### 3. 모바일 송출 (카메라)

1. 휴대폰 브라우저에서 내 방송 상세의 **"방송 시작"**을 누른다. 터치 기기는 카메라 송출 화면이 열린다.
2. "카메라 켜기"로 카메라·마이크 권한을 허락한다. 필요하면 "전면 카메라로"·"후면 카메라로"로 바꾼다(송출 중에도 바꿀 수 있다).
3. **"송출 시작"**을 누르면 PC와 같이 LIVE가 된다. 송출 중에는 화면이 꺼지지 않는다.

### 4. 시청

1. 다른 브라우저나 휴대폰에서 메인 화면("지금 방송 중")의 LIVE 방송을 연다. 로그인하지 않아도 된다.
2. 플레이어가 바로 재생된다. 처음에는 소리가 꺼져 있으니 "소리 켜기"를 누른다. "전체 화면"도 된다.
3. 플레이어 왼쪽 아래 표시로 연결 방식(WebRTC/HLS)을 확인한다. WebRTC가 안 되는 네트워크에서는 HLS로 자동 전환된다.
4. 스트리머가 제목·설명을 수정하면 시청 화면에 바로 반영된다.

### 5. 방송 종료

- **종료 요청**: 스트리머가 상세 또는 송출 화면에서 **"방송 종료"**를 누른다. PC에서 브라우저의 "공유 중지"를 눌러도 종료 요청으로 처리한다. 몇 초 안에 송출이 멈추고 시청 화면이 "방송이 종료되었습니다"로 바뀐다. 송출 화면의 카메라·마이크·화면 공유도 정리된다.
- **송출 끊김**: 송출 탭을 닫거나 네트워크가 끊긴다. 휴대폰에서는 다른 앱으로 전환하거나 화면을 끈다. 미디어 서버에 영상이 들어오지 않게 되면 방송이 바로 종료된다.
- 종료된 방송은 "지난 방송"에 나오고, 다시 송출할 수 없다.

### 6. 시청자 수

메인 목록의 LIVE 카드, 방송 상세, 송출 화면에 지금 시청자 수가 보인다.

- **로그인한 시청자만 센다.** 비로그인으로 보면 숫자가 올라가지 않으므로 "시청자로 로그인"으로 확인한다.
- 같은 계정으로 여러 탭을 열어도 1명이고, 스트리머 본인은 세지 않는다.
- 상세·송출 화면은 5초 안에 바뀐 값을 받는다. 시청자가 나간 것은 서버가 연결 끊김을 알아챈 뒤 반영된다(실서버 측정 약 15~20초).

### 7. 권한

서버가 모든 요청에서 로그인과 스트리머 여부를 확인한다(화면에서 버튼만 숨기는 것이 아니다). 실서버에서 아래 표대로 응답하는 것을 확인했다.

| 동작 | 비로그인 | 로그인(다른 회원) | 스트리머 |
|---|---|---|---|
| 방송 목록·상세 조회, 시청 | 가능 | 가능 | 가능 |
| 방송 등록 | 401 | 가능 | 가능 |
| 방송 수정·삭제, 송출 시작, 종료 요청 | 401 | 403 | 가능 |

송출은 서버가 방송마다 발급한 1회용 송출 토큰이 있어야 미디어 서버가 받는다. LIVE가 아닌 방송은 시청 요청도 거절한다.

다른 회원에게는 수정·삭제 버튼이 보이지 않으므로, 서버의 거절은 API로 직접 확인할 수 있다(`<방송 ID>`는 스트리머가 등록한 방송의 상세 주소 `/broadcasts/<ID>`의 숫자).

```bash
A=https://api.fflux.kro.kr
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $A/api/broadcasts/<방송 ID>          # 비로그인: 401
curl -s -c viewer.jar -o /dev/null -X POST $A/api/auth/test-login \
  -H 'Content-Type: application/json' -d '{"account":"viewer"}'
curl -s -b viewer.jar -o /dev/null -w '%{http_code}\n' -X DELETE $A/api/broadcasts/<방송 ID>  # 다른 회원: 403
```

### 8. 다시보기

1. 스트리머가 "방송 시작"에서 **"녹화하기"를 켜고** 송출한 뒤 방송을 종료한다.
2. 종료 직후 스트리머의 방송 상세에는 "녹화 처리 중"이 보이고, 처리가 끝나면 새로고침하지 않아도 그 자리에서 녹화가 재생된다. 처리에 실패하면 스트리머에게만 "녹화 처리 실패"가 보인다.
3. 메인 화면의 **"다시보기"** 탭에서 방송을 고른다. 로그인하지 않아도 된다. 목록에는 제목, 스트리머 닉네임, 녹화 길이, 방송 날짜가 최신순으로 나오고 "더 보기"로 이어서 불러온다.
4. 방송 상세에서 녹화가 재생된다. 재생 막대로 원하는 위치로 바로 이동할 수 있다.
5. 녹화를 끄고 한 방송은 다시보기 탭에 나오지 않고, 상세는 "방송이 종료되었습니다"로 보인다. 방송을 삭제하면 다시보기도 사라진다.

## 실행 방법 (로컬)

### 필요한 것

- JDK 25(`.sdkmanrc`: `sdk env install`), Node.js 24, Docker Desktop
- 휴대폰 확인용 HTTPS 터널: `brew install cloudflared`

### 환경변수

비밀값은 저장소에 없다. 예시 파일을 복사해서 쓴다.

```bash
cp .env.example .env                 # 로컬 미디어 스택(MediaMTX, Caddy)
cp server/.env.example server/.env   # Spring, 로컬 MySQL
```

루트 `.env`(로컬에서 쓰는 키):

```dotenv
# MediaMTX가 브라우저에 알려줄 WebRTC 호스트: PC 내부 IP (macOS: ipconfig getifaddr en0)
WEBRTC_ADDITIONAL_HOSTS=192.168.0.10
HTTP_PORT=8000
FFLUX_SERVER_URL=http://host.docker.internal:8080
```

`server/.env`:

```dotenv
MYSQL_PASSWORD=change-me
MYSQL_ROOT_PASSWORD=change-me-root
TEST_STREAMER_PASSWORD=change-me-streamer
TEST_VIEWER_PASSWORD=change-me-viewer
SESSION_COOKIE_SECURE=false
TEST_ACCOUNTS_ENABLED=true
MEDIAMTX_API_URL=http://localhost:9997
```

### 실행

```bash
# 1) 미디어 스택: MediaMTX + Caddy(:8000)
docker compose -f infra/compose.yml --env-file .env up -d

# 2) 서버(:8080). 로컬 MySQL은 spring-boot-docker-compose가 server/compose.yaml로 자동으로 띄운다
cd server && ./gradlew :app:bootRun

# 3) 클라이언트(:5173). /api는 Spring, /media는 Caddy로 넘긴다
cd client && npm ci && npm run dev
```

- PC에서 `http://localhost:5173`을 연다.
- 휴대폰은 카메라·화면 공유 API가 HTTPS에서만 동작하므로 `cloudflared tunnel --url http://localhost:5173`이 주는 `https://…trycloudflare.com` 주소로 연다. 같은 와이파이에서만 WebRTC가 붙는다. 자세한 내용은 [`infra/README.md`](infra/README.md)에 있다.

### 테스트

```bash
cd server && ./gradlew test                          # 통합 테스트(Testcontainers MySQL), ArchUnit
cd client && npm run lint && npm test && npm run build
```

## 배포

| 구성 | 위치 |
|---|---|
| 프론트 | Vercel(`fflux.kro.kr`), `main` 푸시마다 자동 배포 |
| API·미디어 | GCP VM 1대(서울) + Docker Compose(Spring, MySQL, MediaMTX, Caddy), `api.fflux.kro.kr` |
| CI/CD | GitHub Actions. CI(서버·클라이언트 검사)가 성공하면 CD가 서버·인프라 변경분만 VM에 배포 |

VM 생성, 방화벽, 환경변수, 손 배포·되돌리기 방법은 [`docs/deploy.md`](docs/deploy.md)에 있다. 설계 결정과 그 이유는 [`docs/decisions.md`](docs/decisions.md)에 있다.

## 기술 스택

- 클라이언트: React, TypeScript, Vite, React Router, TanStack Query, Tailwind CSS, hls.js
- 서버: Kotlin, Spring Boot(Gradle 멀티모듈 모듈러 모놀리스), Spring Security 세션 + Spring Session JDBC, Spring Data JPA, Flyway, MySQL 8
- 미디어: MediaMTX(송출 WHIP, 시청 WHEP, HLS 폴백)
- 인프라: Docker Compose, Caddy(자동 HTTPS), GCP Compute Engine, Vercel, GitHub Actions

## 오픈소스 출처

라이브러리와 서버 소프트웨어로 쓴 오픈소스다. 각 라이선스를 따른다.

| 이름 | 용도 | 라이선스 |
|---|---|---|
| [MediaMTX](https://github.com/bluenviron/mediamtx) | 미디어 서버(WHIP/WHEP/HLS, 녹화) | MIT |
| [Caddy](https://github.com/caddyserver/caddy) | 리버스 프록시, 자동 HTTPS | Apache-2.0 |
| [Kotlin](https://github.com/JetBrains/kotlin) | 서버 언어 | Apache-2.0 |
| [Spring Boot](https://github.com/spring-projects/spring-boot), Spring Security, Spring Session, Spring Data JPA | 서버 프레임워크, 인증·세션, 영속성 | Apache-2.0 |
| [Hibernate ORM](https://github.com/hibernate/hibernate-orm) | JPA 구현체 | Apache-2.0(7.x) |
| [Flyway](https://github.com/flyway/flyway) | DB 마이그레이션 | Apache-2.0 |
| [MySQL](https://www.mysql.com/) | 데이터베이스 | GPL-2.0 |
| [React](https://github.com/facebook/react) | UI | MIT |
| [Vite](https://github.com/vitejs/vite) | 클라이언트 빌드 | MIT |
| [React Router](https://github.com/remix-run/react-router) | 라우팅 | MIT |
| [TanStack Query](https://github.com/TanStack/query) | 서버 상태 관리 | MIT |
| [Tailwind CSS](https://github.com/tailwindlabs/tailwindcss) | 스타일 | MIT |
| [hls.js](https://github.com/video-dev/hls.js) | HLS 재생(Safari 외) | Apache-2.0 |
| [Testcontainers](https://github.com/testcontainers/testcontainers-java) | 통합 테스트용 MySQL | MIT |
| [ArchUnit](https://github.com/TNG/ArchUnit) | 모듈 의존 규칙 테스트 | Apache-2.0 |
| [Vitest](https://github.com/vitest-dev/vitest), [oxlint](https://github.com/oxc-project/oxc) | 클라이언트 테스트, 린트 | MIT |
