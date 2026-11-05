---
paths:
  - "client/**"
---

# 프론트엔드

- 스택: React Router(라우팅), TanStack Query(서버 상태), Tailwind CSS. 클라이언트 전역 상태 라이브러리는 쓰지 않는다.
- API는 same-origin 상대 경로(`/api/...`)로 호출하고 쿠키 세션에 기댄다. 토큰을 브라우저 저장소에 두지 않는다.
- 화면은 반응형 하나다. 송출 화면만 PC용(화면 공유)과 모바일용(카메라)으로 나눈다.

## 송출

- 시작: 녹화 여부를 받는 모달 → `publish-token` 발급 → WHIP 요청(`Authorization: Bearer <token>`).
- 영상 코덱은 `RTCRtpTransceiver.setCodecPreferences`로 H264를 강제한다. 오디오는 Opus.
- PC: `getDisplayMedia`(탭/시스템 소리 포함)와 `getUserMedia`(마이크)의 오디오를 Web Audio API로 섞어 오디오 트랙 하나로 보낸다. 화면 공유 소리가 없어도 마이크만으로 송출되게 한다.
- 모바일: `getUserMedia` 카메라+마이크. 송출 중 Wake Lock을 건다.
- 종료: 종료 API를 먼저 호출하고(서버가 ENDED 처리 후 kick), 응답 후 로컬 트랙과 PeerConnection을 정리한다.

## 시청

- LIVE 방송은 WHEP로 재생하고, 연결 실패 시 HLS로 폴백한다.
- 방송별 SSE(`/api/broadcasts/{id}/events`)를 구독해 상태 변화(ENDED 시 플레이어 종료 화면), 채팅, 후원 알림을 반영한다.
- 다시보기는 녹화 MP4를 `<video>`로 재생하고, 채팅은 보여주지 않으며 댓글을 보여준다.

## 지원 환경

PC 송출 Chrome/Edge 최신(시스템 소리는 Windows만), 모바일 송출 Android Chrome·iOS Safari, 시청은 최신 브라우저 전부. 브라우저별 분기를 추가하면 README의 지원 환경도 갱신한다.
