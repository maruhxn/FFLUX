---
paths:
  - "server/app/**"
  - "server/common/**"
  - "server/modules/member/**"
  - "server/**/port/web/**"
---

# 인증과 인가

- Spring Security 세션 방식(HttpOnly 쿠키), Spring Session JDBC로 MySQL에 저장한다. JWT는 쓰지 않는다.
- Security 설정(필터 체인, 401/403 처리)은 `app`에만 둔다. 자격 증명 확인은 `member`의 provided 유스케이스 `AuthenticateMember`가 한다.
- 세션 principal에는 `memberId`, `nickname`만 담는다. 다른 모듈의 web 포트는 `@LoginMember memberId: Long`으로 받고 Spring Security 타입을 직접 쓰지 않는다.
- 회원 필드: `username`(유니크), `password`(BCrypt), `nickname`.
- 테스트 계정 `streamer`, `viewer`를 시드로 넣는다. 로그인 화면의 "테스트 계정으로 로그인"이 이 계정을 쓴다.

## 접근 규칙

| 요청 | 비로그인 | 로그인(타인) | 스트리머·작성자 |
|---|---|---|---|
| 방송 목록·상세, 시청, 다시보기, 댓글·채팅·후원 목록 | 허용 | 허용 | 허용 |
| 방송 등록, 채팅·댓글 작성 | 401 | 허용 | 허용 |
| 후원 | 401 | 허용 | 403(자기 방송) |
| 방송 수정·삭제, 송출 토큰, 방송 종료 | 401 | 403 | 허용 |
| 댓글 수정·삭제 | 401 | 403 | 허용 |

- 스트리머·작성자 확인은 URL 경로 보호가 아니라 application 서비스에서 엔티티의 `streamerId`/`authorId`와 비교해 한다.
- 새 API를 추가하면 이 표의 어느 행에 속하는지 정하고, 인가 매트릭스 테스트에 해당 조합을 추가한다.
