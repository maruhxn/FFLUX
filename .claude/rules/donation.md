---
paths:
  - "server/modules/donation/**"
---

# 후원 (토스페이먼츠 테스트 모드)

## 흐름

1. 후원 요청: 로그인 사용자가 방송과 금액을 고른다. 서버가 방송의 스트리머를 조회해 `Donation(PENDING, orderId, amount, broadcastId, donorId, streamerId)`를 만든다. 후원 대상은 클라이언트 입력이 아니라 서버가 조회한 스트리머다. 후원자가 그 스트리머 본인이면 403으로 거절한다(자기 방송 후원 불가).
2. 결제창(카드) 결제 후 successUrl로 복귀하면 서버가 확정한다: 요청 금액 == PENDING 금액, 토스 승인 API 성공, 후원 대상 일치. 모두 맞으면 `DONE`, 하나라도 다르면 확정하지 않는다.
3. 웹훅도 같은 확정 경로를 탄다. 결제 상태 웹훅은 서명이 없으므로 본문을 믿지 않고, `paymentKey`로 토스 결제 조회 API를 불러 상태·금액·`orderId`를 확인한다. 승인 재요청에 `ALREADY_PROCESSED_PAYMENT`가 오면 마찬가지로 조회 API로 확인한다.
- failUrl로 돌아오면 `CANCELED`로 바꾼다. 상태는 `PENDING → DONE | CANCELED`이고, 이탈한 PENDING은 그대로 둔다(만료 스케줄러 없음).
4. `DONE`으로 바꾼 쪽(조건부 UPDATE 1행)만 `DonationConfirmed`를 발행한다. 확정은 트랜잭션 밖이라 `realtime`은 `fallbackExecution`으로 받고, 방송이 LIVE일 때만 SSE `donation`을 보낸다.

## 멱등

- `orderId`에 유니크 제약을 둔다.
- 확정은 조건부 UPDATE(`WHERE status = 'PENDING'`) 한 번으로 한다. successUrl과 웹훅, 같은 웹훅의 재전송이 겹쳐도 적립은 한 번이다. 이미 `DONE`이면 성공으로 응답한다.

## 기타

- 후원 가능 방송 상태: `LIVE`, `ENDED`.
- 금액: 1,000/5,000/10,000원 프리셋 + 직접 입력. 서버에서 1,000~1,000,000원 범위를 검증한다.
- 메시지: 선택, 최대 100자, 공백만이면 없음. 후원 요청 때 PENDING에 저장하고 확정 단계에서는 받지 않는다. 내역과 SSE 알림(닉네임, 금액, 메시지)에 담는다.
- 방송별 내역(닉네임 스냅샷, 금액, 메시지, 시각, 최신순 커서)과 합계는 비로그인도 본다.
- 방송별 후원 내역과 합계는 `DONE`만 집계한다.
- 토스 시크릿 키는 환경변수로만 받는다.
