---
paths:
  - "server/modules/broadcast/**"
  - "server/modules/recording/**"
  - "server/modules/realtime/**"
---

# 방송 상태, 송출 인가, 녹화

## 상태 머신

`READY → LIVE → ENDED` 세 상태만 둔다. 각 전이의 트리거는 하나로 정해져 있다. `READY → ENDED`는 없다.

| 전이 | 트리거 |
|---|---|
| `READY → LIVE` | MediaMTX `runOnReady` 훅 → `/internal/mediamtx/ready` (실제 트랙 수신 시점) |
| `LIVE → ENDED` 비정상 | `runOnNotReady` 훅 → 즉시 ENDED (유예·재접속 없음) |
| `LIVE → ENDED` 정상 | 종료 API: 세션·스트리머 본인 검증 → ENDED → MediaMTX API로 송출 세션 kick |

- 프론트 요청으로 LIVE를 만들지 않는다. LIVE는 훅으로만 된다.
- 정상 종료와 `runOnNotReady`는 경쟁할 수 있다. 둘 다 조건부 UPDATE(`WHERE status = 'LIVE'`)로 처리하고, 이미 ENDED면 성공으로 응답한다. `READY` 방송에 종료 API를 호출하면 409.
- 모든 상태 전이 UPDATE에 `deleted_at IS NULL`을 넣는다.
- 한 스트리머의 `LIVE` 방송은 동시에 하나다. 송출 토큰 발급과 `publish` 인증에서 검사한다. 거의 동시 송출 시의 경합은 허용한다(`docs/decisions.md` 3절).
- 수정은 `READY`/`LIVE`에서만(`LIVE` 중 수정은 `BroadcastUpdated` 이벤트로 SSE 반영), 삭제는 `READY`/`ENDED`에서만(LIVE면 409). 삭제 시 미소비 송출 토큰을 무효화한다. 삭제는 soft delete이고 `BroadcastDeleted` 이벤트로 녹화·댓글을 숨긴다. 후원 기록은 보존한다.

## 송출 토큰

1. `POST /api/broadcasts/{id}/publish-token` (body: `record`): 세션 인증, 스트리머 본인, `READY`, 같은 스트리머의 다른 `LIVE` 방송 없음 확인.
2. 같은 방송의 미소비 토큰을 무효화하고 `SecureRandom` 토큰을 새로 발급한다. TTL 약 1분, 방송 ID에 바인딩, DB에는 SHA-256 해시만 저장한다.
3. `record` 값을 `broadcast.recordingEnabled`에 저장하고, `mediaPath`를 `rec/{id}`(녹화) 또는 `live/{id}`(미녹화)로 정한다.
4. 원본 토큰과 WHIP URL을 반환한다. 클라이언트는 WHIP 요청의 `Authorization: Bearer`로 보낸다.

## MediaMTX 인증 콜백 (`POST /internal/mediamtx/auth`)

| action | 처리 |
|---|---|
| `publish` | 토큰 해시 존재·미만료·미소비, 바인딩된 방송의 `mediaPath`와 요청 path 일치, 방송 `READY`·미삭제, 같은 스트리머의 다른 `LIVE` 방송 없음. 통과하면 조건부 UPDATE(`consumed_at IS NULL AND expires_at > now`)로 소비하고, 영향 행이 정확히 1개일 때만 허용 |
| `read` | 토큰 없이 허용하되 방송이 `LIVE`·미삭제일 때만 |
| 그 외 | 거절 (`api`는 MediaMTX 설정의 `authHTTPExclude`로 콜백에서 빠져 있다) |

중복 송출은 MediaMTX `overridePublisher: no`가 막는다. 송출 연결 상태를 따로 저장하는 테이블은 두지 않는다.

## 녹화

- `rec/` path만 녹화된다. 세그먼트가 끝나면 MediaMTX `runOnRecordSegmentComplete` 훅이 ffmpeg로 faststart MP4 리먹싱을 마친 뒤 `recording` 모듈의 internal API를 호출한다.
- `Recording` 상태는 `PROCESSING → READY / FAILED`. 다시보기 목록과 재생에는 `READY`만 노출한다.
- 파일 경로는 DB에 저장하고, 재생 URL은 Caddy 정적 서빙 경로로 만든다.

## 실시간 (SSE)

- 방송별 SSE 하나: `GET /api/broadcasts/{id}/events`. 이벤트 종류: 방송 상태 변화, 방송 정보 수정(`broadcast-updated`), 채팅, 후원.
- `realtime` 모듈은 `common` 이벤트를 받아 인메모리 허브로 해당 방송 구독자에게 보낸다. 다른 모듈은 SSE를 직접 다루지 않고 이벤트만 발행한다.
