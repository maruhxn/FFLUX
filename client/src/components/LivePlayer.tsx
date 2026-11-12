import { useEffect, useEffectEvent, useRef, useState, type ReactNode } from 'react'
import { hlsPlay } from '../broadcast/hls'
import { closeSession } from '../broadcast/webrtc'
import { whepPlay } from '../broadcast/whep'

type Mode = 'connecting' | 'webrtc' | 'hls' | 'disconnected'

// 마우스나 터치가 이만큼 멈춰 있으면 조작 막대를 숨긴다.
const CONTROLS_IDLE_MS = 3000

/**
 * LIVE 방송 플레이어. WHEP로 연결하고, 처음 연결이 안 되면 HLS로 넘어간다.
 * 자동재생 정책 때문에 소리를 끈 채 시작한다. 재생 중 끊기면 "다시 연결"로 처음부터 다시 붙는다.
 * 끊길 때마다 [onDisconnect]를 부른다(방송이 끝나서 끊긴 것인지 확인하는 데 쓴다).
 */
export function LivePlayer({
  whepUrl,
  hlsUrl,
  onDisconnect,
  className = '',
}: {
  whepUrl: string
  hlsUrl: string
  onDisconnect: () => void
  className?: string
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const [mode, setMode] = useState<Mode>('connecting')
  const [muted, setMuted] = useState(true)
  const [volume, setVolume] = useState(1)
  const [paused, setPaused] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const controls = useAutoHide(CONTROLS_IDLE_MS)
  const notifyDisconnect = useEffectEvent(onDisconnect)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    // 이번 연결 시도의 상태. 화면을 떠나면 cancelled가 되고, stop은 연결된 뒤 채운다.
    const run: { cancelled: boolean; stop: (() => void) | null } = { cancelled: false, stop: null }
    const disconnect = () => {
      if (run.cancelled) return
      setMode('disconnected')
      notifyDisconnect()
    }
    setMode('connecting')
    // 다시 연결할 때도 자동재생이 막히지 않도록 소리를 끈 채 시작한다.
    video.muted = true

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

  function togglePlay() {
    const video = videoRef.current
    if (!video) return
    if (!video.paused) {
      video.pause()
      return
    }
    // 라이브라 멈췄던 자리가 아니라 지금 시점으로 돌아간다(HLS. WebRTC는 원래 실시간이다).
    if (video.seekable.length > 0) video.currentTime = video.seekable.end(video.seekable.length - 1)
    void video.play().catch(() => undefined)
  }

  function toggleMute() {
    const video = videoRef.current
    if (!video) return
    video.muted = !video.muted
    if (!video.muted && video.volume === 0) video.volume = 1
    // 소리를 켜는 클릭은 사용자 동작이라 멈춘 재생도 다시 시작할 수 있다.
    void video.play().catch(() => undefined)
  }

  function changeVolume(value: number) {
    const video = videoRef.current
    if (!video) return
    video.volume = value
    video.muted = value === 0
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) {
      void document.exitFullscreen().catch(() => undefined)
      return
    }
    const video = videoRef.current as (HTMLVideoElement & { webkitEnterFullscreen?: () => void }) | null
    // 브라우저가 전체 화면을 거절하면(사용자 동작이 아닌 경우 등) 그대로 둔다.
    if (containerRef.current?.requestFullscreen) containerRef.current.requestFullscreen().catch(() => undefined)
    // iOS Safari는 요소 전체 화면이 없고 video만 전체 화면이 된다.
    else video?.webkitEnterFullscreen?.()
  }

  const playing = mode === 'webrtc' || mode === 'hls'
  const showControls = controls.visible || paused || !playing
  const silent = muted || volume === 0

  return (
    <div
      ref={containerRef}
      onMouseMove={controls.poke}
      onTouchStart={controls.poke}
      className={`relative aspect-video overflow-hidden bg-black ${showControls ? '' : 'cursor-none'} ${className}`}
    >
      <video
        ref={videoRef}
        autoPlay
        muted
        playsInline
        onClick={togglePlay}
        onPlay={() => setPaused(false)}
        onPause={() => setPaused(true)}
        onVolumeChange={(e) => {
          setMuted(e.currentTarget.muted)
          setVolume(e.currentTarget.volume)
        }}
        className="h-full w-full object-contain"
      />

      <span className="pointer-events-none absolute top-3 right-3 rounded-md bg-red-600 px-2.5 py-0.5 text-sm font-extrabold tracking-wide text-white sm:text-base">
        LIVE
      </span>

      {mode === 'connecting' && <Overlay>연결 중…</Overlay>}
      {mode === 'disconnected' && (
        <Overlay>
          <p>방송 연결이 끊겼습니다.</p>
          <button
            type="button"
            onClick={() => setAttempt((n) => n + 1)}
            className="rounded-md bg-emerald-500 px-3 py-1.5 text-sm font-semibold text-neutral-950"
          >
            다시 연결
          </button>
        </Overlay>
      )}

      <div
        className={`absolute inset-x-0 bottom-0 flex items-center justify-between bg-gradient-to-t from-black/80 to-transparent px-3 pt-8 pb-2 text-white transition-opacity ${
          showControls ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
      >
        <div className="flex items-center gap-1 sm:gap-2">
          <IconButton label={paused ? '재생' : '일시정지'} onClick={togglePlay}>
            {paused ? <PlayIcon /> : <PauseIcon />}
          </IconButton>
          <div className="flex items-center">
            <IconButton label={silent ? '소리 켜기' : '소리 끄기'} onClick={toggleMute}>
              {silent ? <MutedIcon /> : <VolumeIcon />}
            </IconButton>
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={silent ? 0 : volume}
              onChange={(e) => changeVolume(Number(e.target.value))}
              aria-label="음량"
              className="hidden w-20 accent-white sm:block"
            />
          </div>
          <span className="ml-1 flex items-center gap-1.5 text-sm">
            실시간
            <span className="h-2 w-2 rounded-full bg-red-500" />
            {/* 연결 방식. 평가 때 HLS 폴백을 눈으로 확인할 수 있게 둔다. */}
            {playing && <span className="text-xs text-neutral-400">{mode === 'webrtc' ? 'WebRTC' : 'HLS'}</span>}
          </span>
        </div>
        <IconButton label="전체 화면" onClick={toggleFullscreen}>
          <FullscreenIcon />
        </IconButton>
      </div>
    </div>
  )
}

/** 마우스나 터치가 멈추고 [idleMs]가 지나면 visible이 false가 된다. */
function useAutoHide(idleMs: number) {
  const [visible, setVisible] = useState(true)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => {
    timer.current = window.setTimeout(() => setVisible(false), idleMs)
    return () => window.clearTimeout(timer.current)
  }, [idleMs])

  function poke() {
    setVisible(true)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setVisible(false), idleMs)
  }

  return { visible, poke }
}

function Overlay({ children }: { children: ReactNode }) {
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/70 text-neutral-300">
      {children}
    </div>
  )
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="flex h-9 w-9 items-center justify-center rounded-md hover:bg-white/15"
    >
      {children}
    </button>
  )
}

const iconProps = { width: 24, height: 24, viewBox: '0 0 24 24', fill: 'currentColor', 'aria-hidden': true }

function PlayIcon() {
  return (
    <svg {...iconProps}>
      <path d="M8 5.5v13l10.5-6.5z" />
    </svg>
  )
}

function PauseIcon() {
  return (
    <svg {...iconProps}>
      <rect x="6.5" y="5" width="3.5" height="14" rx="1" />
      <rect x="14" y="5" width="3.5" height="14" rx="1" />
    </svg>
  )
}

function VolumeIcon() {
  return (
    <svg {...iconProps}>
      <path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" />
      <path d="M15.5 8.5a5 5 0 0 1 0 7" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M18 6a8.5 8.5 0 0 1 0 12" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function MutedIcon() {
  return (
    <svg {...iconProps}>
      <path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" />
      <path d="M15.5 9.5l5 5m0-5l-5 5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function FullscreenIcon() {
  return (
    <svg {...iconProps} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5" />
    </svg>
  )
}
