// 캠 PiP 합성 Worker. 화면·캠 프레임을 읽어 가장 최근 것끼리 OffscreenCanvas에 그리고, 출력 트랙에 쓴다.
import { pipRect } from './pip'

type Init = {
  screenFrames: ReadableStream<VideoFrame>
  camFrames: ReadableStream<VideoFrame>
  output: WritableStream<VideoFrame>
}

/**
 * 둘 중 어느 쪽 프레임이 와도 그리되 30fps를 넘지 않는다. 화면이 멈춰 있어도 캠이 움직이면 갱신된다.
 * 간격 안에 들어와 건너뛴 변화는 간격이 지나면 한 번 더 그린다(화면만 남아 프레임이 드물 때 마지막 변화를 놓치지 않게).
 */
const FRAME_INTERVAL_MS = 1000 / 30

self.onmessage = (e: MessageEvent<Init>) => void run(e.data)

async function run({ screenFrames, camFrames, output }: Init) {
  const writer = output.getWriter()
  const canvas = new OffscreenCanvas(1, 1)
  const context = canvas.getContext('2d')!
  let screen: VideoFrame | null = null
  let cam: VideoFrame | null = null
  let lastAt = -Infinity
  let writing = false
  // 아직 그리지 않은 새 프레임이 있는지와, 간격이 지나면 다시 그릴 예약.
  let dirty = false
  let retry: ReturnType<typeof setTimeout> | null = null

  async function draw() {
    const now = performance.now()
    if (!screen || !dirty || writing) return
    if (now - lastAt < FRAME_INTERVAL_MS) {
      retry ??= setTimeout(() => {
        retry = null
        void draw().catch(() => undefined)
      }, FRAME_INTERVAL_MS - (now - lastAt))
      return
    }
    lastAt = now
    dirty = false
    writing = true
    const { displayWidth: width, displayHeight: height } = screen
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width
      canvas.height = height
    }
    context.drawImage(screen, 0, 0, width, height)
    if (cam) {
      const rect = pipRect(width, height, cam.displayWidth, cam.displayHeight)
      context.drawImage(cam, rect.x, rect.y, rect.width, rect.height)
    }
    const frame = new VideoFrame(canvas, { timestamp: Math.round(now * 1000) })
    try {
      await writer.write(frame)
    } finally {
      frame.close()
      writing = false
    }
    // 쓰는 동안 들어온 변화를 그린다.
    void draw().catch(() => undefined)
  }

  /** 프레임을 받을 때마다 최근 프레임을 바꾸고 그린다. 스트림이 끝나면 돌아온다. */
  async function pump(frames: ReadableStream<VideoFrame>, keep: (frame: VideoFrame | null) => void) {
    const reader = frames.getReader()
    for (;;) {
      const { value, done } = await reader.read()
      keep(done ? null : value)
      dirty = true
      void draw().catch(() => undefined)
      if (done) return
    }
  }

  const keepScreen = (frame: VideoFrame | null) => {
    screen?.close()
    screen = frame
  }
  const keepCam = (frame: VideoFrame | null) => {
    cam?.close()
    cam = frame
  }
  // 캠이 끝나면 화면만 그린다. 화면이 끝나면 출력도 끝낸다.
  void pump(camFrames, keepCam).catch(() => keepCam(null))
  await pump(screenFrames, keepScreen).catch(() => keepScreen(null))
  await writer.close().catch(() => undefined)
}
