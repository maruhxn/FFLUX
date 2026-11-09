// 송출 준비물. PC는 화면 공유 영상(캠을 켜면 우하단에 PiP로 합성)과, 화면 소리·마이크를 Web Audio로 섞은 오디오 트랙 하나.
// 모바일은 카메라와 마이크.
import { composePip, type Pip } from './pip'

export type Facing = 'user' | 'environment'

/**
 * 송출할 [stream]과 그 원본들. 끝낼 때 [sources]의 트랙을 모두 멈추고 [pip] 합성도 멈춘다.
 * [sourceVideo]는 끝나면 송출을 끝내는 원본 영상 트랙이다(화면 공유의 "공유 중지", 카메라 회수). 캠 PiP면 합성 트랙이 아니라 화면 트랙이다.
 * [facing]은 카메라 송출일 때 지금 쓰는 카메라(전면/후면)이고, 화면 공유면 null이다.
 */
export type Capture = {
  stream: MediaStream
  sources: MediaStream[]
  sourceVideo: MediaStreamTrack
  pip: Pip | null
  audioContext: AudioContext | null
  audioLabel: string
  facing: Facing | null
}

/** 화면 공유를 못 하거나(모바일 브라우저) 주 입력이 터치인 기기는 카메라로 송출한다. */
export function publishesCamera() {
  return typeof navigator.mediaDevices?.getDisplayMedia !== 'function' || window.matchMedia('(pointer: coarse)').matches
}

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
    sourceVideo: display.getVideoTracks()[0],
    pip: null,
    audioContext,
    audioLabel: labels.length > 0 ? labels.join(' + ') : '없음(영상만)',
    facing: null,
  }
}

const cameraVideo = (facing: Facing): MediaTrackConstraints => ({
  facingMode: facing,
  width: { ideal: 1280 },
  height: { ideal: 720 },
  frameRate: { ideal: 30 },
})

/** 모바일 송출 준비물: [facing] 카메라 영상과 마이크. */
export async function captureCamera(facing: Facing): Promise<Capture> {
  const stream = await navigator.mediaDevices.getUserMedia({
    video: cameraVideo(facing),
    audio: { echoCancellation: true, noiseSuppression: true },
  })
  return {
    stream,
    sources: [stream],
    sourceVideo: stream.getVideoTracks()[0],
    pip: null,
    audioContext: null,
    audioLabel: '마이크',
    facing,
  }
}

/**
 * 카메라 송출 준비물을 반대쪽 카메라로 바꾼 새 준비물을 돌려주고, [capture]는 정리한다(마이크는 복제해 이어 쓴다).
 * 카메라를 하나만 열 수 있는 기기(iOS)가 있어 지금 카메라를 먼저 끈다. 반대쪽을 못 열면 원래 카메라를 다시 열고,
 * 그것도 안 되면 실패한다.
 */
export async function flipCamera(capture: Capture): Promise<Capture> {
  if (!capture.facing) throw new Error('카메라 송출이 아닙니다.')
  const current = capture.facing
  const next: Facing = current === 'user' ? 'environment' : 'user'
  const audio = capture.stream.getAudioTracks().map((t) => t.clone())
  releaseCapture(capture)
  const open = (facing: Facing) => navigator.mediaDevices.getUserMedia({ video: cameraVideo(facing) })
  try {
    const [facing, video] = await open(next)
      .then((s) => [next, s] as const)
      .catch(() => open(current).then((s) => [current, s] as const))
    const stream = new MediaStream([...video.getVideoTracks(), ...audio])
    return { ...capture, stream, sources: [stream], sourceVideo: video.getVideoTracks()[0], facing }
  } catch (e) {
    audio.forEach((t) => t.stop())
    throw e
  }
}

/** PC 송출에 겹칠 캠. [deviceId]가 없으면 기본 캠을 연다. PiP는 작게 그리므로 해상도는 낮게 받는다. */
export function openCam(deviceId: string | null): Promise<MediaStream> {
  return navigator.mediaDevices.getUserMedia({
    video: { ...(deviceId ? { deviceId: { exact: deviceId } } : {}), width: { ideal: 640 }, height: { ideal: 360 }, frameRate: { ideal: 30 } },
  })
}

/** 화면 공유 준비물에 [cam]을 PiP로 겹친 준비물. 정리하면 화면·마이크·캠과 합성을 모두 멈춘다. */
export function withPip(screen: Capture, cam: MediaStream): Capture {
  const pip = composePip(screen.sourceVideo, cam.getVideoTracks()[0])
  return {
    ...screen,
    stream: new MediaStream([pip.track, ...screen.stream.getAudioTracks()]),
    sources: [...screen.sources, cam],
    pip,
  }
}

export function releaseCapture(capture: Capture) {
  capture.pip?.stop()
  capture.sources.forEach((s) => s.getTracks().forEach((t) => t.stop()))
  void capture.audioContext?.close()
}
