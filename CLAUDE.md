# FFLUX

**답변 언어: 사용자에게 보이는 모든 설명(답변, 요약, 질문, 진행 상황 안내)은 한국어로 쓴다.** 코드, 명령어, 파일 경로, 식별자만 원문 그대로 둔다.

치지직 같은 실시간 라이브 방송 웹 서비스. PC 화면 공유 송출, 모바일 카메라 송출, 시청, 방송 관리, 인증·인가가 필수이고 채팅·댓글·후원·녹화/다시보기가 선택이다. 요구사항 원문은 `요구사항명세서.md`, 모든 설계 결정과 그 이유는 `docs/decisions.md`에 있다. 결정과 다르게 구현해야 할 상황이면 먼저 사용자에게 묻고, 합의되면 `docs/decisions.md`와 해당 rule을 함께 고친다.

평가 우선순위: 필수 기능의 실제 동작 > 인증·인가(스트리머·작성자 검증) > 코드 구조·예외 처리·방송 상태 관리 > README > 선택 기능.

## 구성

- `client/`: React + Vite + TypeScript, React Router, TanStack Query, Tailwind CSS. 반응형 SPA 하나로 PC와 모바일을 모두 지원한다(네이티브 앱 없음).
- `server/`: Kotlin + Spring Boot, Gradle 멀티모듈 모듈러 모놀리스. MySQL 8, Spring Data JPA, Spring Security 세션 + Spring Session JDBC.
- 미디어: MediaMTX 셀프호스팅. 송출 WHIP, 시청 WHEP(실패 시 HLS 폴백), 녹화 fMP4 → faststart MP4.
- 배포: GCP VM 1대(서울) + Docker Compose(Spring, MySQL, MediaMTX, 녹화 다시 처리, Caddy). 프론트는 Vercel(`fflux.kro.kr`), API·미디어는 VM(`api.fflux.kro.kr`)이고, VM의 Caddy가 HTTPS와 라우팅을 맡는다(`/api` → Spring, `/media` → MediaMTX). 프론트는 API를 교차 출처(CORS + 쿠키)로 부른다.

## 핵심 도메인

- **방송**은 1회성 세션이다: `READY → LIVE → ENDED`. ENDED는 종착 상태라 다시 송출하지 않는다. 상태 전이의 기준은 MediaMTX 훅(실제 미디어 수신)이다.
- **스트리머** = 방송을 등록·송출하는 회원(`streamerId`). 요구사항의 "방송자", "방송 소유자"와 같다. 후원 대상도 그 방송의 스트리머이고, 자기 방송에는 후원할 수 없다. 용어는 `GLOSSARY.md`를 따른다.
- **채팅**은 LIVE 방송의 실시간 메시지, **댓글**은 다시보기(ENDED + 녹화 READY)에 다는 글이다. 둘은 별개 모듈이다.
- 방송 목록 조회와 시청은 비로그인 허용, 그 외 쓰기는 로그인 필요, 수정·삭제·송출 제어는 서버에서 스트리머·작성자를 확인한다.

## 작업 순서

`docs/decisions.md` 12절의 순서를 따른다. 첫 마일스톤은 필수 기능이 실서버(HTTPS)와 실기기(PC, 휴대폰 LTE)에서 동작하는 것이다. 미디어 동작은 로컬 데스크톱 브라우저만으로 완료 판정하지 않는다. 개발은 로컬 + HTTPS 터널로 하고, VM은 PC 송출·시청·종료가 갖춰진 뒤 LTE 확인부터 쓴다.

## 보안

비밀번호, 토큰, 결제 시크릿 키는 저장소에 넣지 않는다. 운영 실제 값은 GitHub Actions Secrets(비밀값)·Variables(그 밖의 운영 설정)에만 두고, CD가 배포마다 VM `.env`를 새로 쓴다. 새 환경변수를 추가하면 `.env.example`에 키와 예시 값을, `cd.yml`의 `.env` 생성 단계에 그 키를 함께 추가한다.

## Agent skills

### Issue tracker

이슈는 GitHub Issues(`maruhxn/FFLUX`)에서 `gh` CLI로 관리한다. See `docs/agents/issue-tracker.md`.

### Triage labels

기본 5개 라벨(`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`)을 그대로 쓴다. See `docs/agents/triage-labels.md`.

### Domain docs

single-context: 루트 `GLOSSARY.md`와 `docs/adr/`를 쓴다. 기존 설계 결정은 `docs/decisions.md`에 있다. See `docs/agents/domain.md`.
