import { useSyncExternalStore } from 'react'
import { api } from '../api/client'
import type { BroadcastDetail } from './broadcast'
import { BROADCAST_STATUS_EVENT, openBroadcastEvents, parseStatusMessage } from './events'
import { flipCamera, releaseCapture, type Capture, type Facing } from './capture'
import { closeSession, type WebRtcSession } from './webrtc'
import { whipPublish } from './whip'

// 송출 세션은 앱 전체에 하나다. 송출 화면이 아니라 여기 두므로 사이트 안에서 페이지를 옮겨도 송출이 이어진다.
// 방송이 끝나는 경우는 스트리머의 종료 요청(endBroadcast, "공유 중지", 카메라 회수)과 송출 끊김(네트워크, 탭 닫기, 앱 전환)뿐이다.

export type PublishSession = { broadcastId: number; stream: MediaStream; audioLabel: string; facing: Facing | null }

type Active = {
  broadcastId: number
  capture: Capture
  whip: WebRtcSession
  events: EventSource
  wakeLock: WakeLockSentinel | null
  // 이 탭이 종료를 요청했다. 서버가 응답보다 SSE의 ENDED를 먼저 보낼 수 있어 요청 전에 표시해 둔다.
  ending: boolean
}

let active: Active | null = null
let snapshot: PublishSession | null = null
// 이 탭이 종료 요청을 하지 않았는데 송출이 끝난 방송. 송출 화면이 끊긴 이유를 안내한다.
let lostBroadcastId: number | null = null
const listeners = new Set<() => void>()

function setActive(next: Active | null) {
  active = next
  snapshot = next && {
    broadcastId: next.broadcastId,
    stream: next.capture.stream,
    audioLabel: next.capture.audioLabel,
    facing: next.capture.facing,
  }
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

/** 이 탭에서 송출하다가 종료 요청 없이 끝난(송출 끊김) 방송. 없으면 null. */
export function useLostBroadcastId(): number | null {
  return useSyncExternalStore(subscribe, () => lostBroadcastId)
}

/** 탭을 닫거나 새로고침하면 송출이 끊겨 방송이 끝나므로 브라우저 확인창을 띄운다(문구는 브라우저가 정한다). */
function confirmUnload(e: BeforeUnloadEvent) {
  e.preventDefault()
}

/** 카메라 송출 중에는 화면이 꺼지지 않게 한다. 지원하지 않거나 거절되면 그대로 둔다. */
async function requestWakeLock(): Promise<WakeLockSentinel | null> {
  return (await navigator.wakeLock?.request('screen').catch(() => null)) ?? null
}

/** 백그라운드로 가면 브라우저가 Wake Lock을 풀므로, 다시 보이면 다시 건다. */
async function renewWakeLock() {
  const current = active
  if (document.visibilityState !== 'visible' || !current?.capture.facing) return
  if (current.wakeLock && !current.wakeLock.released) return
  const wakeLock = await requestWakeLock()
  if (active === current) current.wakeLock = wakeLock
  else void wakeLock?.release()
}

/** 송출이 이어지지 못하면 종료를 요청한다. 요청이 실패해도 로컬 자원은 정리한다. */
const endOrRelease = (broadcastId: number) => void endBroadcast(broadcastId).catch(release)

/** 로컬 송출 자원(연결, 화면·카메라·마이크, Wake Lock)을 정리한다. 서버 상태는 바꾸지 않는다. 종료 요청 없이 끝났으면 송출 끊김으로 기록한다. */
async function release() {
  const current = active
  if (!current) return
  if (!current.ending) lostBroadcastId = current.broadcastId
  setActive(null)
  current.events.close()
  window.removeEventListener('beforeunload', confirmUnload)
  document.removeEventListener('visibilitychange', renewWakeLock)
  void current.wakeLock?.release()
  await closeSession(current.whip)
  releaseCapture(current.capture)
}

/**
 * 영상 트랙이 끝나면("공유 중지", 운영체제의 카메라 회수) 송출을 이어갈 수 없으므로 종료를 요청한다.
 * 카메라는 앱 전환 때 꺼질 수 있어 스트리머가 직접 끈 것이 아니므로 송출 끊김으로 안내한다.
 */
function watchVideoEnded(broadcastId: number, capture: Capture) {
  const video = capture.sourceVideo
  const onEnded = () => {
    if (active?.capture !== capture) return
    if (capture.facing) lostBroadcastId = broadcastId
    endOrRelease(broadcastId)
  }
  // 연결하는 사이에 이미 끝났으면 ended 이벤트가 다시 오지 않는다.
  if (video.readyState === 'ended') onEnded()
  else video.addEventListener('ended', onEnded)
}

/**
 * [capture]를 [whipUrl]로 송출하고 앱 전체의 송출 세션으로 둔다. 성공하면 [capture]의 정리도 이 세션이 맡는다.
 * 브라우저의 "공유 중지"는 종료 요청으로, 연결 실패는 송출 끊김(서버가 ENDED로 바꾼다)으로 본다.
 */
export async function startPublish(broadcastId: number, capture: Capture, whipUrl: string, token: string) {
  const whip = await whipPublish(whipUrl, token, capture.stream)
  // 다른 탭·기기의 종료 요청이나 송출 끊김으로 방송이 끝나면 이 탭의 송출 자원도 정리한다.
  // 다시 연결되면 끊긴 사이 끝났을 수 있으니 상세로 확인한다.
  const releaseIfEnded = (status: string | undefined) => {
    if (status === 'ENDED' && active?.whip === whip) void release()
  }
  const events = openBroadcastEvents(broadcastId)
  events.addEventListener(BROADCAST_STATUS_EVENT, (e) => releaseIfEnded(parseStatusMessage(e).status))
  events.addEventListener('open', async () => {
    const broadcast = await api<BroadcastDetail>(`/api/broadcasts/${broadcastId}`).catch(() => null)
    releaseIfEnded(broadcast?.status)
  })
  lostBroadcastId = null
  setActive({ broadcastId, capture, whip, events, wakeLock: null, ending: false })
  window.addEventListener('beforeunload', confirmUnload)
  document.addEventListener('visibilitychange', renewWakeLock)
  void renewWakeLock()
  watchVideoEnded(broadcastId, capture)
  whip.pc.addEventListener('connectionstatechange', () => {
    if (whip.pc.connectionState === 'failed' && active?.whip === whip) void release()
  })
}

/**
 * 카메라 송출 중 전면/후면을 바꾼다. 송출 연결은 그대로 두고 보내는 트랙만 갈아 끼운다(마이크도 복제본으로 바뀐다).
 * 반대쪽을 못 열어 원래 카메라로 돌아왔으면 false다. 카메라를 다시 열지 못하거나 트랙을 바꾸지 못하면
 * 송출을 이어갈 수 없으므로 종료를 요청한다.
 */
export async function flipPublishingCamera(): Promise<boolean> {
  const current = active
  if (!current?.capture.facing) return false
  const before = current.capture.facing
  let capture: Capture | null = null
  try {
    capture = await flipCamera(current.capture)
    // 바꾸는 사이에 송출이 끝났으면 새 카메라만 끈다.
    if (active !== current) {
      releaseCapture(capture)
      return false
    }
    const tracks = capture.stream.getTracks()
    await Promise.all(
      current.whip.pc.getSenders().map((sender) => {
        const track = tracks.find((t) => t.kind === sender.track?.kind)
        return track && sender.replaceTrack(track)
      }),
    )
  } catch {
    if (capture) releaseCapture(capture)
    endOrRelease(current.broadcastId)
    throw new Error('카메라를 바꾸지 못해 방송을 종료합니다.')
  }
  current.capture = capture
  setActive(current)
  watchVideoEnded(current.broadcastId, capture)
  return capture.facing !== before
}

/** 종료 요청: 서버가 ENDED로 바꾸고 송출 세션을 끊은 뒤, 이 탭의 송출 자원을 정리한다. */
export async function endBroadcast(broadcastId: number) {
  const current = active?.broadcastId === broadcastId ? active : null
  if (current) current.ending = true
  try {
    await api<void>(`/api/broadcasts/${broadcastId}/end`, { method: 'POST' })
  } catch (e) {
    if (current) current.ending = false
    throw e
  }
  if (active === current) await release()
}
