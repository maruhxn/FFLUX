# FFLUX

치지직 같은 실시간 라이브 방송 웹 서비스. PC 화면 공유 송출, 모바일 카메라 송출, 시청, 방송 관리, 인증·인가가 필수이고 채팅·댓글·후원·녹화/다시보기가 선택이다. 요구사항 원문은 `요구사항명세서.md`, 모든 설계 결정과 그 이유는 `docs/decisions.md`에 있다. 결정과 다르게 구현해야 할 상황이면 먼저 사용자에게 묻고, 합의되면 `docs/decisions.md`와 해당 rule을 함께 고친다.

평가 우선순위: 필수 기능의 실제 동작 > 인증·인가(소유자 검증) > 코드 구조·예외 처리·방송 상태 관리 > README > 선택 기능.

## 구성

- `client/`: React + Vite + TypeScript, React Router, TanStack Query, Tailwind CSS. 반응형 SPA 하나로 PC와 모바일을 모두 지원한다(네이티브 앱 없음).
- `server/`: Kotlin + Spring Boot, Gradle 멀티모듈 모듈러 모놀리스. MySQL 8, Spring Data JPA, Spring Security 세션 + Spring Session JDBC.
- 미디어: MediaMTX 셀프호스팅. 송출 WHIP, 시청 WHEP(실패 시 HLS 폴백), 녹화 fMP4 → faststart MP4.
- 배포: AWS VM 1대 + Docker Compose(Spring, MySQL, MediaMTX, Caddy). 도메인은 `fflux.kro.kr` 하나(서브도메인 없음)이고, Caddy가 HTTPS와 same-origin 라우팅을 맡는다(`/api` → Spring, `/media` → MediaMTX).

## 핵심 도메인

- **방송**은 1회성 세션이다: `READY → LIVE → ENDED`. ENDED는 종착 상태라 다시 송출하지 않는다. 상태 전이의 기준은 MediaMTX 훅(실제 미디어 수신)이다.
- **방송자** = 방송 소유자(`ownerId`). 후원 대상도 방송 소유자다.
- **채팅**은 LIVE 방송의 실시간 메시지, **댓글**은 다시보기(ENDED + 녹화 READY)에 다는 글이다. 둘은 별개 모듈이다.
- 방송 목록 조회와 시청은 비로그인 허용, 그 외 쓰기는 로그인 필요, 수정·삭제·송출 제어는 서버에서 소유자·작성자를 확인한다.

## 작업 순서

`docs/decisions.md` 12절의 순서를 따른다. 첫 마일스톤은 필수 기능이 실서버(HTTPS)와 실기기(PC, 휴대폰 LTE)에서 동작하는 것이다. 미디어 동작은 로컬 데스크톱 브라우저만으로 완료 판정하지 않는다.

## 보안

비밀번호, 토큰, 결제 시크릿 키는 저장소에 넣지 않는다. 실제 값은 VM `.env`에만 두고, 새 환경변수를 추가하면 `.env.example`에 키와 예시 값을 함께 추가한다.
