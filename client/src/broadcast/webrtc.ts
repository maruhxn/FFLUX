// WHIP 송출과 WHEP 시청이 함께 쓰는 WebRTC 세션 도구.

export type WebRtcSession = { pc: RTCPeerConnection; resourceUrl: string | null }

/** ICE 후보 수집을 기다린다. MediaMTX는 trickle ICE 없이 offer 하나로 후보를 받는다. */
export function waitIceGathering(pc: RTCPeerConnection, timeoutMs = 3000) {
  if (pc.iceGatheringState === 'complete') return Promise.resolve()
  return new Promise<void>((resolve) => {
    const done = () => {
      pc.removeEventListener('icegatheringstatechange', check)
      resolve()
    }
    const check = () => pc.iceGatheringState === 'complete' && done()
    pc.addEventListener('icegatheringstatechange', check)
    setTimeout(done, timeoutMs)
  })
}

/** WHIP·WHEP 응답의 Location(세션 리소스 URL, 종료 시 DELETE). Caddy가 절대 경로에 /media 접두사를 붙여 준다. */
export function resourceUrlOf(response: Response): string | null {
  const location = response.headers.get('Location')
  return location ? new URL(location, window.location.href).href : null
}

/** WHIP·WHEP 세션을 끝낸다: 리소스 DELETE 후 PeerConnection 정리. */
export async function closeSession(session: WebRtcSession) {
  if (session.resourceUrl) {
    await fetch(session.resourceUrl, { method: 'DELETE' }).catch(() => undefined)
  }
  session.pc.close()
}
