import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { useMe } from '../auth/auth'
import { useBroadcast, useIssuePublishToken, type Broadcast } from '../broadcast/broadcast'
import { closeWhip, whipPublish, type WhipSession } from '../broadcast/whip'
import { StatusBadge } from '../components/StatusBadge'

/** 화면 공유 소리와 마이크를 담은 송출 준비물. 오디오는 Web Audio로 섞어 트랙 하나로 만든다. */
type Capture = { stream: MediaStream; sources: MediaStream[]; audioContext: AudioContext | null; audioLabel: string }

/** 화면 소리가 없으면 마이크만, 마이크를 쓸 수 없으면 화면 소리만 나간다. */
async function captureScreen(): Promise<Capture> {
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

function releaseCapture(capture: Capture) {
  capture.sources.forEach((s) => s.getTracks().forEach((t) => t.stop()))
  void capture.audioContext?.close()
}

/** PC 송출(`/broadcasts/:id/publish`). 스트리머만, READY 방송에서 시작한다. */
export function BroadcastPublishPage() {
  const id = Number(useParams().id)
  const { data: me, isPending } = useMe()
  const { data: broadcast, error } = useBroadcast(id)

  if (isPending) return null
  if (!me) return <Navigate to="/login" replace />
  if (error) return <p className="text-red-400">{error.message}</p>
  if (!broadcast) return null
  if (broadcast.streamerId !== me.memberId) return <p className="text-red-400">방송의 스트리머만 송출할 수 있습니다.</p>
  return <PublishView broadcast={broadcast} />
}

type Step = 'record' | 'capture' | 'publishing' | 'stopped'

function PublishView({ broadcast }: { broadcast: Broadcast }) {
  const [step, setStep] = useState<Step>('record')
  const [record, setRecord] = useState(true)
  const [capture, setCapture] = useState<Capture | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const sessionRef = useRef<WhipSession | null>(null)
  const previewRef = useRef<HTMLVideoElement>(null)
  const issueToken = useIssuePublishToken(broadcast.id)
  // 송출 중에는 LIVE로 바뀌는지 보려고 몇 초마다 다시 읽는다(SSE는 #9).
  const { data: latest } = useBroadcast(broadcast.id, step === 'publishing' ? 3000 : undefined)
  const status = latest?.status ?? broadcast.status

  useEffect(() => {
    if (previewRef.current) previewRef.current.srcObject = capture?.stream ?? null
  }, [capture])

  // 화면을 떠나거나 다른 화면을 고르면 이전 송출 준비물을 정리한다.
  useEffect(() => {
    if (!capture) return
    return () => releaseCapture(capture)
  }, [capture])

  useEffect(
    () => () => {
      if (sessionRef.current) void closeWhip(sessionRef.current)
    },
    [],
  )

  async function selectScreen() {
    setMessage(null)
    try {
      const next = await captureScreen()
      // 사용자가 브라우저의 "공유 중지"를 누르면 송출도 끝낸다.
      next.stream.getVideoTracks()[0].addEventListener('ended', () => void stop())
      setCapture(next)
    } catch {
      setMessage('화면 공유를 시작하지 못했습니다.')
    }
  }

  async function start() {
    if (!capture) return
    setMessage(null)
    setStarting(true)
    try {
      // 토큰은 약 1분만 유효하므로 화면을 고른 뒤 송출 직전에 받는다.
      const { token, whipUrl } = await issueToken.mutateAsync(record)
      sessionRef.current = await whipPublish(whipUrl, token, capture.stream)
      setStep('publishing')
    } catch (e) {
      setMessage(e instanceof Error ? e.message : '송출을 시작하지 못했습니다.')
    } finally {
      setStarting(false)
    }
  }

  // 방송 종료(#8)가 붙기 전이라 송출만 끊는다. 이 방송은 같은 토큰 흐름으로 다시 송출할 수 없다.
  async function stop() {
    if (sessionRef.current) await closeWhip(sessionRef.current)
    sessionRef.current = null
    setCapture(null)
    setStep('stopped')
  }

  if (step === 'record' && broadcast.status !== 'READY') {
    return <p className="text-neutral-400">방송 대기 중인 방송만 송출을 시작할 수 있습니다.</p>
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <StatusBadge status={status} />
        <h1 className="text-xl font-bold break-words">{broadcast.title}</h1>
      </div>

      {step === 'record' && (
        <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-sm space-y-4 rounded-lg bg-neutral-900 p-6">
            <h2 className="text-lg font-semibold">방송 시작</h2>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={record} onChange={(e) => setRecord(e.target.checked)} />
              녹화하기 (끝난 뒤 다시보기로 남습니다)
            </label>
            <div className="flex justify-end gap-2">
              <Link
                to={`/broadcasts/${broadcast.id}`}
                className="rounded-md border border-neutral-700 px-3 py-1.5 text-sm hover:bg-neutral-800"
              >
                취소
              </Link>
              <button
                type="button"
                onClick={() => setStep('capture')}
                className="rounded-md bg-emerald-500 px-3 py-1.5 text-sm font-semibold text-neutral-950"
              >
                다음
              </button>
            </div>
          </div>
        </div>
      )}

      <video ref={previewRef} autoPlay muted playsInline className="aspect-video w-full rounded-lg bg-neutral-900" />

      {step === 'capture' && (
        <div className="space-y-3">
          <p className="text-sm text-neutral-400">
            녹화: {record ? '함' : '안 함'} · 마이크 소리가 함께 나갑니다. 공유 창에서 "탭 오디오 공유"(macOS) 또는 "시스템
            오디오 공유"(Windows)를 켜면 화면 소리도 섞입니다.
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={selectScreen}
              className="rounded-md border border-neutral-700 px-3 py-1.5 text-sm hover:bg-neutral-800"
            >
              {capture ? '화면 다시 고르기' : '공유할 화면 고르기'}
            </button>
            <button
              type="button"
              onClick={start}
              disabled={!capture || starting}
              className="rounded-md bg-emerald-500 px-3 py-1.5 text-sm font-semibold text-neutral-950 disabled:opacity-50"
            >
              송출 시작
            </button>
          </div>
          {capture && <p className="text-xs text-neutral-500">소리: {capture.audioLabel}</p>}
        </div>
      )}

      {step === 'publishing' && (
        <div className="space-y-3">
          <p className="text-sm text-neutral-300">
            {status === 'LIVE' ? '방송 중입니다.' : '송출을 연결했습니다. 미디어 서버가 영상을 받으면 방송이 시작됩니다.'}
          </p>
          <button
            type="button"
            onClick={() => void stop()}
            className="rounded-md border border-red-800 px-3 py-1.5 text-sm text-red-400 hover:bg-red-950"
          >
            송출 중지
          </button>
        </div>
      )}

      {step === 'stopped' && (
        <p className="text-sm text-neutral-300">
          송출을 중지했습니다. 방송 종료 처리는 아직 연결되지 않아 방송 상태는 바뀌지 않을 수 있습니다.
        </p>
      )}

      {message && <p className="text-sm text-red-400">{message}</p>}
    </div>
  )
}
