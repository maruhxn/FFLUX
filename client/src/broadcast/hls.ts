/**
 * HLS 시청(WHEP 폴백). Safari(iOS 포함)는 브라우저 기본 HLS, 그 밖에는 hls.js를 쓴다.
 * 최근 Chrome도 canPlayType이 참이지만 Opus가 든 HLS를 재생하지 못해 Safari만 기본 재생을 쓴다(스파이크 #2에서 확인).
 */
export async function hlsPlay(
  hlsUrl: string,
  video: HTMLVideoElement,
  onFailed: () => void,
  isCancelled: () => boolean,
): Promise<() => void> {
  const isSafari = /^((?!chrome|android|crios|fxios|edg).)*safari/i.test(navigator.userAgent)
  if (isSafari && video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = hlsUrl
    video.addEventListener('error', onFailed, { once: true })
    return () => {
      video.removeEventListener('error', onFailed)
      video.removeAttribute('src')
      // src를 지운 뒤 load()를 불러야 내려받기와 재생이 멈춘다.
      video.load()
    }
  }
  // HLS로 넘어갈 때만 불러온다.
  const { default: Hls } = await import('hls.js')
  // 불러오는 사이에 화면을 떠났으면 video에 붙이지 않는다(다음 연결 시도의 video를 흔들지 않게).
  if (isCancelled()) return () => undefined
  if (!Hls.isSupported()) throw new Error('이 브라우저는 HLS를 재생할 수 없습니다.')
  const hls = new Hls()
  hls.on(Hls.Events.ERROR, (_, data) => data.fatal && onFailed())
  hls.loadSource(hlsUrl)
  hls.attachMedia(video)
  return () => hls.destroy()
}
