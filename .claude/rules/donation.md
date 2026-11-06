---
paths:
  - "server/modules/donation/**"
---

# 후원 (토스페이먼츠 테스트 모드)

## 흐름

1. 후원 요청: 로그인 사용자가 방송과 금액을 고른다. 서버가 방송의 스트리머를 조회해 `Donation(PENDING, orderId, amount, broadcastId, donorId, streamerId)`를 만든다. 후원 대상은 클라이언트 입력이 아니라 서버가 조회한 스트리머다. 후원자가 그 스트리머 본인이면 403으로 거절한다(자기 방송 후원 불가).
2. 결제위젯 결제 후 successUrl로 복귀하면 서버가 확정한다: 요청 금액 == PENDING 금액, 토스 승인 API 성공, 후원 대상 일치. 모두 맞으면 `DONE`, 하나라도 다르면 확정하지 않는다.
3. 웹훅도 같은 확정 경로를 탄다.
4. `DONE`이 되면 `DonationConfirmed` 이벤트를 발행한다(LIVE면 SSE 알림).

## 멱등

- `orderId`에 유니크 제약을 둔다.
- 확정은 조건부 UPDATE(`WHERE status = 'PENDING'`) 한 번으로 한다. successUrl과 웹훅, 같은 웹훅의 재전송이 겹쳐도 적립은 한 번이다. 이미 `DONE`이면 성공으로 응답한다.

## 기타

- 후원 가능 방송 상태: `LIVE`, `ENDED`.
- 금액: 1,000/5,000/10,000원 프리셋 + 직접 입력. 서버에서 최소 금액과 양수 여부를 검증한다.
- 방송별 후원 내역과 합계는 `DONE`만 집계한다.
- 토스 시크릿 키는 환경변수로만 받는다.
