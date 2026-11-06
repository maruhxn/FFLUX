// PC 송출 준비물: 화면 공유 영상과, 화면 소리·마이크를 Web Audio로 섞은 오디오 트랙 하나.

/** 화면 공유 소리와 마이크를 담은 송출 준비물. 오디오는 Web Audio로 섞어 트랙 하나로 만든다. */
export type Capture = { stream: MediaStream; sources: MediaStream[]; audioContext: AudioContext | null; audioLabel: string }

/** 화면 소리가 없으면 마이크만, 마이크를 쓸 수 없으면 화면 소리만 나간다. */
export async function captureScreen(): Promise<Capture> {
  const display = await navigator.mediaDevices.getDisplayMedia({
    video: { frameRate: 30 },
    audio: true,
    // Chrome 전용 옵션: Windows에서 시스템 소리 공유를 고를 수 있게 한다.
    systemAudio: 'include',
  } as DisplayMediaStreamOptions)
  const mic = await navigator.mediaDevices
    .getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
    .catch(() => null)
  const audioTracks = [...display.getAudioTracks(), ...(mic?.getAudioTracks() ?? [])]
  const labels = [display.getAudioTracks().length > 0 && '화면 소리', mic && '마이크'].filter(Boolean)
  let audioContext: AudioContext | null = null
  const tracks: MediaStreamTrack[] = [display.getVideoTracks()[0]]
  if (audioTracks.length > 0) {
    audioContext = new AudioContext()
    const destination = audioContext.createMediaStreamDestination()
    for (const track of audioTracks) audioContext.createMediaStreamSource(new MediaStream([track])).connect(destination)
    tracks.push(destination.stream.getAudioTracks()[0])
  }
  return {
    stream: new MediaStream(tracks),
    sources: [display, ...(mic ? [mic] : [])],
    audioContext,
    audioLabel: labels.length > 0 ? labels.join(' + ') : '없음(영상만)',
  }
}

export function releaseCapture(capture: Capture) {
  capture.sources.forEach((s) => s.getTracks().forEach((t) => t.stop()))
  void capture.audioContext?.close()
}
