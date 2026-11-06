// WHIP 송출(브라우저 → MediaMTX). 스파이크(infra/spike/spike.js)에서 확인한 방식 그대로다.
import { resourceUrlOf, waitIceGathering, type WebRtcSession } from './webrtc'

/** 송출 트랜시버가 원하는 코덱만 협상하게 한다(영상 H264, 오디오 Opus). 지원하지 않으면 브라우저 기본값을 쓴다. */
function preferCodec(transceiver: RTCRtpTransceiver, kind: 'video' | 'audio', mimeType: string) {
  if (typeof transceiver.setCodecPreferences !== 'function') return
  const codecs = RTCRtpSender.getCapabilities(kind)?.codecs ?? []
  const wanted = codecs.filter((c) => c.mimeType.toLowerCase() === mimeType.toLowerCase())
  if (wanted.length === 0) return
  // packetization-mode=1 + baseline(42e01f)을 앞에 둬 호환성이 가장 넓은 H264를 먼저 고르게 한다.
  const score = (c: RTCRtpCodec) => {
    const f = c.sdpFmtpLine ?? ''
    return (f.includes('packetization-mode=1') ? 2 : 0) + (f.includes('profile-level-id=42e01f') ? 1 : 0)
  }
  transceiver.setCodecPreferences([...wanted].sort((a, b) => score(b) - score(a)))
}

/** [stream]을 [whipUrl]로 송출한다. 송출 토큰은 `Authorization: Bearer`로 보내고, MediaMTX가 서버에 확인한다. */
export async function whipPublish(whipUrl: string, token: string, stream: MediaStream): Promise<WebRtcSession> {
  const pc = new RTCPeerConnection()
  try {
    for (const track of stream.getTracks()) {
      const transceiver = pc.addTransceiver(track, { direction: 'sendonly', streams: [stream] })
      if (track.kind === 'video') preferCodec(transceiver, 'video', 'video/H264')
      if (track.kind === 'audio') preferCodec(transceiver, 'audio', 'audio/opus')
    }
    await pc.setLocalDescription(await pc.createOffer())
    await waitIceGathering(pc)
    const response = await fetch(whipUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/sdp', Authorization: `Bearer ${token}` },
      body: pc.localDescription?.sdp,
    })
    if (response.status !== 201) {
      throw new Error(
        response.status === 401 ? '송출 허가를 받지 못했습니다. 다시 시도하세요.' : `송출 연결 실패 (HTTP ${response.status})`,
      )
    }
    const resourceUrl = resourceUrlOf(response)
    await pc.setRemoteDescription({ type: 'answer', sdp: await response.text() })
    return { pc, resourceUrl }
  } catch (e) {
    pc.close()
    throw e
  }
}
