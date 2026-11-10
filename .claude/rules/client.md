---
paths:
  - "client/**"
---

# 프론트엔드

- 스택: React Router(라우팅), TanStack Query(서버 상태), Tailwind CSS. 클라이언트 전역 상태 라이브러리는 쓰지 않는다.
- 테스트: Vitest(`npm test`). 브라우저 미디어 API에 기대지 않는 순수 로직만 단위 테스트한다(예: `broadcast/pip.test.ts`). 미디어 동작은 브라우저에서 확인한다.
- API·미디어는 `api/client.ts`의 `apiUrl`로 API 서버 주소(`VITE_API_ORIGIN`, 운영은 `https://api.fflux.kro.kr`)를 붙여 부른다. 로컬은 비워 두고 Vite 프록시로 같은 출처다. 쿠키 세션에 기대므로 `fetch`는 `credentials: 'include'`, SSE는 `withCredentials`로 연다. 토큰을 브라우저 저장소에 두지 않는다.
- 화면은 반응형 하나다. 송출 화면만 PC용(화면 공유)과 모바일용(카메라)으로 나눈다.

## 송출

- 시작: 녹화 여부를 받는 모달 → `publish-token` 발급 → WHIP 요청(`Authorization: Bearer <token>`).
- 영상 코덱은 `RTCRtpTransceiver.setCodecPreferences`로 H264를 강제한다. 오디오는 Opus.
- PC: `getDisplayMedia`(탭/시스템 소리 포함)와 `getUserMedia`(마이크)의 오디오를 Web Audio API로 섞어 오디오 트랙 하나로 보낸다. 화면 공유 소리가 없어도 마이크만으로 송출되게 한다.
- PC 캠 PiP(선택, Chrome/Edge): 화면 + 캠을 Worker(`MediaStreamTrackProcessor` → `OffscreenCanvas` → `MediaStreamTrackGenerator`)에서 영상 트랙 하나로 합성한다(`broadcast/pip.ts`). 메인 스레드 타이머로 합성하지 않는다(백그라운드 탭에서 멈춘다). 송출 종료는 합성 트랙이 아니라 준비물의 `sourceVideo`(화면 트랙)가 끝날 때 판단한다.
- 모바일: `getUserMedia` 카메라+마이크. 송출 중 Wake Lock을 걸고, 탭이 다시 보이면 다시 건다. 진입 주소는 PC와 같고 `publishesCamera()`(화면 공유 불가 또는 `pointer: coarse`)로 화면이 갈린다.
- 카메라 전환(전·후면)은 송출 중에도 된다. 연결은 그대로 두고 `RTCRtpSender.replaceTrack`으로 트랙을 갈아 끼운다. 지금 카메라를 먼저 끄고(iOS는 카메라 하나만 열린다), 마이크는 복제본으로 오디오 sender까지 바꾼다. 카메라 트랙이 끝나 종료할 때는 앱 전환일 수 있으므로 송출 끊김으로 안내한다.
- 종료: 종료 API를 먼저 호출하고(서버가 ENDED 처리 후 kick), 응답 후 로컬 트랙과 PeerConnection을 정리한다.
- 송출 세션(WHIP 연결, 화면·캠·마이크 트랙, 캠 PiP 합성 Worker)은 페이지 컴포넌트가 아니라 앱 전체 저장소(`broadcast/publishSession.ts`)가 들고 있다. 사이트 안 페이지 이동으로는 송출을 끊지 않는다. 끊는 경우는 종료 요청(방송 종료 버튼, 브라우저 "공유 중지", 카메라 회수)과 송출 끊김(연결 실패, 탭 닫기, 앱 전환)뿐이고, 송출 중에는 `beforeunload` 확인창을 띄운다. 이 탭이 종료를 요청하지 않았는데 끝나면 송출 끊김으로 기록해 송출 화면에 이유를 안내한다. 서버가 종료 응답보다 SSE의 ENDED를 먼저 보낼 수 있으므로 종료 요청 전에 "종료 중"을 표시해 둔다.

## 시청

- LIVE 방송은 WHEP로 재생하고, 연결 실패 시 HLS로 폴백한다.
- 방송별 SSE(`/api/broadcasts/{id}/events`)를 구독해 상태 변화(ENDED 시 플레이어 종료 화면), 채팅, 후원 알림을 반영한다.
- 다시보기는 녹화 MP4를 `<video>`로 재생하고, 채팅은 보여주지 않으며 댓글을 보여준다. 다시보기 목록은 홈 목록의 "다시보기" 탭이고, 상세는 `/broadcasts/:id`가 ENDED + `READY` 녹화일 때 플레이어 자리에 `<video>`를 그린다.
- 끝난 방송은 SSE를 구독하지 않는다. 녹화 처리 상태는 스트리머 화면만 상세를 다시 읽어 따라가고(`waitsForRecording`, `useRecordingWait`), 시청자 화면은 종료 화면에 머문다(처리 중 녹화는 스트리머에게만 온다).
- LIVE 후원 알림은 시청 상세와 송출 화면의 플레이어 위 토스트로 차례로 띄운다.

## 지원 환경

PC 송출 Chrome/Edge 최신, 화면 + 선택적 캠 PiP(시스템 소리는 Windows만), 모바일 송출 Android Chrome·iOS Safari, 시청은 최신 브라우저 전부. 브라우저별 분기를 추가하면 README의 지원 환경도 갱신한다.
