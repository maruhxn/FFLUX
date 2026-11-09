---
paths:
  - "server/app/**"
  - "server/common/**"
  - "server/modules/member/**"
  - "server/**/port/web/**"
---

# 인증과 인가

- Spring Security 세션 방식(HttpOnly 쿠키), Spring Session JDBC로 MySQL에 저장한다. JWT는 쓰지 않는다.
- CSRF는 `SameSite=Lax` 세션 쿠키와 CORS 출처 허용 목록(`CORS_ALLOWED_ORIGINS`, 밖의 `Origin`은 403)으로 막고 CSRF 토큰은 쓰지 않는다. 쓰기 API를 GET으로 만들지 않는다(Lax 쿠키는 다른 사이트의 GET 이동에는 실린다).
- 401·403은 필터 단계(`AuthenticationEntryPoint`, `AccessDeniedHandler`)에서도 MVC 예외 처리와 같은 ProblemDetail 형식으로 응답한다.
- Security 설정(필터 체인, 401/403 처리)은 `app`에만 둔다. 자격 증명 확인은 `member`의 provided 유스케이스 `MemberAuthenticator`가 한다.
- 세션 principal은 `common`의 `LoginMember(memberId, nickname)`다. 다른 모듈의 web 포트는 `member: LoginMember` 파라미터로 받고 Spring Security 타입을 직접 쓰지 않는다. `LoginMember`는 `java.security.Principal`을 구현하지 않는다(구현하면 MVC 기본 리졸버가 가로챈다).
- `/api`는 기본 거절이다. 비로그인으로 열 핸들러 메서드에 `@PublicApi`를 붙인다. Security 설정에 공개 URL을 나열하지 않는다. `@PublicApi` 메서드는 `LoginMember`를 nullable(`LoginMember?`)로만 받는다(비로그인이면 null).
- 회원 필드: `username`(유니크), `password`(BCrypt), `nickname`.
- 테스트 계정 `streamer`, `viewer`를 시드로 넣는다. 비밀번호는 환경변수로만 받는다. 로그인 화면의 "테스트 계정으로 로그인"은 `POST /api/auth/test-login`으로 이 두 계정에만 비밀번호 없이 로그인한다.

## 접근 규칙

| 요청 | 비로그인 | 로그인(타인) | 스트리머·작성자 |
|---|---|---|---|
| 방송 목록·상세, 시청, 다시보기, 댓글·채팅·후원 목록 | 허용 | 허용 | 허용 |
| 방송 등록, 채팅·댓글 작성 | 401 | 허용 | 허용 |
| 후원 | 401 | 허용 | 403(자기 방송) |
| 방송 수정·삭제, 송출 토큰, 방송 종료 | 401 | 403 | 허용 |
| 댓글 수정·삭제 | 401 | 403 | 허용 |

- 스트리머·작성자 확인은 URL 경로 보호가 아니라 엔티티 메서드가 행위자 ID를 받아 `streamerId`/`authorId`와 비교해 한다(`server-code-convention.md` 7절). application 서비스는 엔티티를 찾아(없으면 404) 위임한다.
- 새 API를 추가하면 이 표의 어느 행에 속하는지 정한다. "비로그인 허용" 행이면 `@PublicApi`를 붙이고, 인가 매트릭스 테스트에 해당 조합을 추가한다.
