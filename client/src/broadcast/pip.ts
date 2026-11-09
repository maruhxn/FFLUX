// PC 송출의 캠 PiP: 화면 공유 영상 우하단에 캠을 겹쳐 영상 트랙 하나로 만든다(Chrome/Edge).
// 합성은 Worker가 OffscreenCanvas로 한다. 탭이 백그라운드여도 타이머처럼 느려지지 않도록, 프레임이 도착할 때마다 그린다.

export type PipRect = { x: number; y: number; width: number; height: number }

/** 캠을 화면 가로·세로 1/4 상자 안에 비율대로 맞춰 우하단에 둔다. 여백은 화면 짧은 변의 3%다. */
export function pipRect(screenWidth: number, screenHeight: number, camWidth: number, camHeight: number): PipRect {
  const scale = Math.min(screenWidth / 4 / camWidth, screenHeight / 4 / camHeight)
  const width = Math.round(camWidth * scale)
  const height = Math.round(camHeight * scale)
  const margin = Math.round(Math.min(screenWidth, screenHeight) * 0.03)
  return { x: screenWidth - width - margin, y: screenHeight - height - margin, width, height }
}

/** 합성한 영상 트랙. [stop]은 합성을 멈추고 이 트랙을 끝낸다(원본 화면·캠 트랙은 건드리지 않는다). */
export type Pip = { track: MediaStreamTrack; stop: () => void }

/** 이 브라우저가 프레임 단위 트랙 처리(MediaStreamTrackProcessor·Generator)를 지원하는지. 없으면 캠 PiP를 쓸 수 없다. */
export function supportsPip() {
  return 'MediaStreamTrackProcessor' in window && 'MediaStreamTrackGenerator' in window
}

/** [screen] 위에 [cam]을 겹친 영상 트랙을 만든다. 캠 트랙이 끝나면 화면만 이어서 내보낸다. */
export function composePip(screen: MediaStreamTrack, cam: MediaStreamTrack): Pip {
  const screenFrames = new MediaStreamTrackProcessor({ track: screen }).readable
  const camFrames = new MediaStreamTrackProcessor({ track: cam }).readable
  const generator = new MediaStreamTrackGenerator({ kind: 'video' })
  const worker = new Worker(new URL('./pipWorker.ts', import.meta.url), { type: 'module' })
  worker.postMessage({ screenFrames, camFrames, output: generator.writable }, [
    screenFrames,
    camFrames,
    generator.writable,
  ])
  return {
    track: generator,
    stop: () => {
      worker.terminate()
      generator.stop()
    },
  }
}
