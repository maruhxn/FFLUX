import { apiUrl } from '../api/client'
import { closeSession, resourceUrlOf, waitIceGathering, type WebRtcSession } from './webrtc'

const DISCONNECTED_GRACE_MS = 5000

/**
 * WHEP 시청(MediaMTX → 브라우저). [timeoutMs] 안에 연결되지 않으면 실패로 보고 정리한다.
 * 연결된 뒤 끊기면 [onFailed]를 부른다. 받은 [stream]을 video에 붙이는 일은 부르는 쪽이 한다
 * (취소된 연결 시도가 video를 덮어쓰지 않도록).
 */
export async function whepPlay(
  whepUrl: string,
  onFailed: () => void,
  timeoutMs = 8000,
): Promise<{ session: WebRtcSession; stream: MediaStream }> {
  const pc = new RTCPeerConnection()
  // MediaMTX에 세션이 생긴 뒤 실패하면 그 세션도 지운다.
  let resourceUrl: string | null = null
  try {
    pc.addTransceiver('video', { direction: 'recvonly' })
    pc.addTransceiver('audio', { direction: 'recvonly' })
    const stream = new MediaStream()
    pc.addEventListener('track', (e) => stream.addTrack(e.track))
    await pc.setLocalDescription(await pc.createOffer())
    await waitIceGathering(pc)
    const response = await fetch(apiUrl(whepUrl), {
      method: 'POST',
      headers: { 'Content-Type': 'application/sdp' },
      body: pc.localDescription?.sdp,
    })
    if (response.status !== 201) throw new Error(`WHEP 연결 실패 (HTTP ${response.status})`)
    resourceUrl = resourceUrlOf(response)
    await pc.setRemoteDescription({ type: 'answer', sdp: await response.text() })
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('WebRTC 연결 시간 초과')), timeoutMs)
      const check = () => {
        if (pc.connectionState === 'connected') {
          clearTimeout(timer)
          resolve()
        } else if (pc.connectionState === 'failed') {
          clearTimeout(timer)
          reject(new Error('WebRTC 연결 실패'))
        }
      }
      pc.addEventListener('connectionstatechange', check)
      check()
    })
    // disconnected는 잠깐 흔들렸다 돌아오기도 하지만, failed로 바뀌기까지 수십 초가 걸릴 수 있어 이어지면 끊긴 것으로 본다.
    let disconnectedTimer: number | undefined
    pc.addEventListener('connectionstatechange', () => {
      window.clearTimeout(disconnectedTimer)
      if (pc.connectionState === 'failed') onFailed()
      else if (pc.connectionState === 'disconnected') disconnectedTimer = window.setTimeout(onFailed, DISCONNECTED_GRACE_MS)
    })
    return { session: { pc, resourceUrl }, stream }
  } catch (e) {
    await closeSession({ pc, resourceUrl })
    throw e
  }
}
