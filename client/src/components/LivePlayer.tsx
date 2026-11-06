import { useEffect, useRef, useState } from 'react'
import { hlsPlay } from '../broadcast/hls'
import { whepPlay } from '../broadcast/whep'
import { closeSession } from '../broadcast/webrtc'

type Mode = 'connecting' | 'webrtc' | 'hls' | 'disconnected'

const MODE_LABEL: Record<Mode, string> = {
  connecting: '연결 중',
  webrtc: 'WebRTC',
  hls: 'HLS',
  disconnected: '연결 끊김',
}

/**
 * LIVE 방송 플레이어. WHEP로 연결하고, 처음 연결이 안 되면 HLS로 넘어간다.
 * 자동재생 정책 때문에 소리를 끈 채 시작한다. 재생 중 끊기면 "다시 연결"로 처음부터 다시 붙는다.
 */
export function LivePlayer({ whepUrl, hlsUrl }: { whepUrl: string; hlsUrl: string }) {
  const containerRef = useRef<HTMLDivElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const [mode, setMode] = useState<Mode>('connecting')
  const [muted, setMuted] = useState(true)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    // 이번 연결 시도의 상태. 화면을 떠나면 cancelled가 되고, stop은 연결된 뒤 채운다.
    const run: { cancelled: boolean; stop: (() => void) | null } = { cancelled: false, stop: null }
    const disconnect = () => !run.cancelled && setMode('disconnected')
    setMode('connecting')
    // 다시 연결할 때도 자동재생이 막히지 않도록 소리를 끈 채 시작한다.
    video.muted = true
    setMuted(true)

    async function connect(video: HTMLVideoElement) {
      try {
        const { session, stream } = await whepPlay(whepUrl, disconnect)
        run.stop = () => void closeSession(session)
        if (!run.cancelled) {
          video.srcObject = stream
          setMode('webrtc')
        }
      } catch {
        if (run.cancelled) return
        try {
          run.stop = await hlsPlay(hlsUrl, video, disconnect, () => run.cancelled)
          if (!run.cancelled) setMode('hls')
        } catch {
          disconnect()
        }
      }
      // 연결하는 사이에 화면을 떠났으면 바로 정리한다.
      if (run.cancelled) run.stop?.()
    }

    void connect(video)
    return () => {
      run.cancelled = true
      run.stop?.()
      video.srcObject = null
    }
  }, [whepUrl, hlsUrl, attempt])

  function toggleMute() {
    const video = videoRef.current
    if (!video) return
    video.muted = !video.muted
    setMuted(video.muted)
    // 소리를 켜는 클릭은 사용자 동작이라 멈춘 재생도 다시 시작할 수 있다.
    void video.play().catch(() => undefined)
  }

  function enterFullscreen() {
    const video = videoRef.current as (HTMLVideoElement & { webkitEnterFullscreen?: () => void }) | null
    // 브라우저가 전체 화면을 거절하면(사용자 동작이 아닌 경우 등) 그대로 둔다.
    if (containerRef.current?.requestFullscreen) containerRef.current.requestFullscreen().catch(() => undefined)
    // iOS Safari는 요소 전체 화면이 없고 video만 전체 화면이 된다.
    else video?.webkitEnterFullscreen?.()
  }

  return (
    <div ref={containerRef} className="relative aspect-video overflow-hidden rounded-lg bg-black">
      <video ref={videoRef} autoPlay muted playsInline className="h-full w-full" />
      {mode === 'connecting' && (
        <div className="absolute inset-0 flex items-center justify-center text-neutral-400">연결 중…</div>
      )}
      {mode === 'disconnected' && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/70 text-neutral-300">
          <p>방송 연결이 끊겼습니다.</p>
          <button
            type="button"
            onClick={() => setAttempt((n) => n + 1)}
            className="rounded-md bg-emerald-500 px-3 py-1.5 text-sm font-semibold text-neutral-950"
          >
            다시 연결
          </button>
        </div>
      )}
      <div className="absolute inset-x-0 bottom-0 flex items-center justify-between gap-2 bg-gradient-to-t from-black/80 p-2 text-sm">
        <span className="rounded bg-black/60 px-2 py-0.5 text-xs text-neutral-300">{MODE_LABEL[mode]}</span>
        <div className="flex gap-2">
          <button type="button" onClick={toggleMute} className="rounded-md bg-black/60 px-3 py-1 hover:bg-black/80">
            {muted ? '소리 켜기' : '소리 끄기'}
          </button>
          <button type="button" onClick={enterFullscreen} className="rounded-md bg-black/60 px-3 py-1 hover:bg-black/80">
            전체 화면
          </button>
        </div>
      </div>
    </div>
  )
}
