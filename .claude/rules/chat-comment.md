---
paths:
  - "server/modules/chat/**"
  - "server/modules/comment/**"
---

# 채팅과 댓글

채팅과 댓글은 별개 모듈이고 규칙이 다르다.

| | 채팅 (`chat`) | 댓글 (`comment`) |
|---|---|---|
| 작성 조건 | 방송 `LIVE` | 방송 `ENDED` + `READY` 녹화 존재 (아니면 409) |
| 수정·삭제 | 작성자는 못 한다. 스트리머가 채팅 가리기·채팅 금지를 한다 | 작성자만 (스트리머도 불가) |
| 전달 | REST 작성 → `ChatPosted` 이벤트 → SSE | REST |
| 조회 | 입장 시 최근 50개 + 이후 SSE. 다시보기 화면에는 노출하지 않음 | 커서 기반 페이지네이션, 최신순 |

- 방송 상태와 녹화 존재 여부는 자기 모듈 `application.required` 포트로 조회한다(구현은 `app` 어댑터).
- 작성자 닉네임은 작성 시점에 스냅샷으로 저장한다.
- 치지직처럼 라이브에는 채팅, 다시보기에는 댓글이다. 녹화 없는 방송에는 댓글이 없다.
- 채팅 다시보기는 만들지 않는다(후순위 후보).
- 채팅 본문 1~200자(공백만은 거절). 최근 50개는 `GET /api/broadcasts/{id}/chats`(비로그인 허용, 오래된순, LIVE가 아니면 빈 목록, 없거나 삭제된 방송은 404, 응답 `{chats, restriction}`), 이후는 SSE `chat`(`{id, authorId, authorNickname, content, hidden}`, 목록 항목과 같은 모양).
- 채팅 관리(`docs/decisions.md` 6절): 채팅 가리기 `POST /api/chats/{id}/hide`(`Chat.hiddenAt`, 가린 채팅은 닉네임·본문 null, SSE `chat-hidden`). 채팅 금지 `POST /api/chats/{id}/restrict`(`{duration: THIRTY_SECONDS | ONE_HOUR | UNTIL_LIFTED}`, 스트리머 단위, 덮어쓰기, 자기 자신 409), 해제 `DELETE /api/chat-restrictions/{memberId}`, 목록 `GET /api/chat-restrictions`(로그인한 스트리머 자신의 지금 금지 중인 회원). 금지·해제는 스트리머의 LIVE 방송 SSE `chat-restriction`. 금지된 회원의 작성은 403(404 다음, 409 앞).
- 댓글은 `broadcast_id`에 속한다. soft delete된 방송의 댓글은 노출하지 않는다.
- 댓글 본문 1~500자(공백만은 거절), 수정 시 "(수정됨)" 표시, 페이지 20개, 목록은 비로그인 허용.
- 댓글 삭제는 soft delete(`deletedAt`). 목록에 자리는 남기고 응답은 `deleted: true`, 본문·닉네임 `null`. 삭제된 댓글의 수정·재삭제는 409(403이 먼저). 댓글 수에는 세지 않는다.
- 답글: 댓글에 다는 댓글이고 한 단계만 둔다. `Comment`의 부모 댓글 ID로 두고 댓글과 같은 규칙을 따른다. 답글의 답글, 삭제된 댓글에 답글은 409. 원댓글을 지워도 답글은 남는다.
- 원댓글 목록은 최신순 20개(답글 제외), 답글은 원댓글별로 오래된순 20개. 댓글 수는 원댓글 + 답글(삭제 제외), 원댓글마다 답글 수를 담는다. "@닉네임"은 화면이 채우는 글자이고 서버는 해석하지 않는다.
- 채팅은 요구사항 밖이라 녹화·댓글·후원 다음, 여유가 있을 때 만든다.
