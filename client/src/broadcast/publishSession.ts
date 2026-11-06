import { useSyncExternalStore } from 'react'
import { api } from '../api/client'
import { releaseCapture, type Capture } from './capture'
import { closeSession, type WebRtcSession } from './webrtc'
import { whipPublish } from './whip'

// 송출 세션은 앱 전체에 하나다. 송출 화면이 아니라 여기 두므로 사이트 안에서 페이지를 옮겨도 송출이 이어진다.
// 방송이 끝나는 경우는 스트리머의 종료 요청(endBroadcast)과 송출 끊김(네트워크, 탭 닫기, "공유 중지")뿐이다.

export type PublishSession = { broadcastId: number; stream: MediaStream; audioLabel: string }

type Active = PublishSession & { capture: Capture; whip: WebRtcSession; statusTimer: number }

// 다른 탭·기기의 종료 요청이나 송출 끊김으로 방송이 끝났는지 이 간격으로 확인한다(SSE는 #9).
const STATUS_CHECK_MS = 5000

let active: Active | null = null
let snapshot: PublishSession | null = null
const listeners = new Set<() => void>()

function setActive(next: Active | null) {
  active = next
  snapshot = next && { broadcastId: next.broadcastId, stream: next.stream, audioLabel: next.audioLabel }
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** 지금 이 탭에서 송출 중인 방송. 없으면 null. */
export function usePublishSession(): PublishSession | null {
  return useSyncExternalStore(subscribe, () => snapshot)
}

/** 탭을 닫거나 새로고침하면 송출이 끊겨 방송이 끝나므로 브라우저 확인창을 띄운다(문구는 브라우저가 정한다). */
function confirmUnload(e: BeforeUnloadEvent) {
  e.preventDefault()
}

/** 로컬 송출 자원(연결, 화면·마이크)을 정리한다. 서버 상태는 바꾸지 않는다. */
async function release() {
  const current = active
  if (!current) return
  setActive(null)
  window.clearInterval(current.statusTimer)
  window.removeEventListener('beforeunload', confirmUnload)
  await closeSession(current.whip)
  releaseCapture(current.capture)
}

/**
 * [capture]를 [whipUrl]로 송출하고 앱 전체의 송출 세션으로 둔다. 성공하면 [capture]의 정리도 이 세션이 맡는다.
 * 브라우저의 "공유 중지"는 종료 요청으로, 연결 실패는 송출 끊김(서버가 ENDED로 바꾼다)으로 본다.
 */
export async function startPublish(broadcastId: number, capture: Capture, whipUrl: string, token: string) {
  const whip = await whipPublish(whipUrl, token, capture.stream)
  const statusTimer = window.setInterval(async () => {
    const broadcast = await api<{ status: string }>(`/api/broadcasts/${broadcastId}`).catch(() => null)
    if (broadcast?.status === 'ENDED' && active?.whip === whip) void release()
  }, STATUS_CHECK_MS)
  setActive({ broadcastId, stream: capture.stream, audioLabel: capture.audioLabel, capture, whip, statusTimer })
  window.addEventListener('beforeunload', confirmUnload)
  const video = capture.stream.getVideoTracks()[0]
  const stopSharing = () => void endBroadcast(broadcastId).catch(release)
  // 연결하는 사이에 이미 "공유 중지"를 눌렀으면 ended 이벤트가 다시 오지 않는다.
  if (video.readyState === 'ended') stopSharing()
  else video.addEventListener('ended', stopSharing)
  whip.pc.addEventListener('connectionstatechange', () => {
    if (whip.pc.connectionState === 'failed' && active?.whip === whip) void release()
  })
}

/** 종료 요청: 서버가 ENDED로 바꾸고 송출 세션을 끊은 뒤, 이 탭의 송출 자원을 정리한다. */
export async function endBroadcast(broadcastId: number) {
  await api<void>(`/api/broadcasts/${broadcastId}/end`, { method: 'POST' })
  if (active?.broadcastId === broadcastId) await release()
}
