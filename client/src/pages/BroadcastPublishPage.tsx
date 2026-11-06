import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { useMe } from '../auth/auth'
import { useBroadcast, useIssuePublishToken, type Broadcast } from '../broadcast/broadcast'
import { useBroadcastEvents } from '../broadcast/events'
import { captureScreen, releaseCapture, type Capture } from '../broadcast/capture'
import { startPublish, usePublishSession } from '../broadcast/publishSession'
import { EndBroadcastButton } from '../components/EndBroadcastButton'
import { StatusBadge } from '../components/StatusBadge'

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

type Step = 'record' | 'capture'

/** 송출은 앱 전체의 송출 세션이 들고 있어 이 화면을 떠나도 이어진다. 다시 들어오면 진행 중인 송출을 보여 준다. */
function PublishView({ broadcast }: { broadcast: Broadcast }) {
  const session = usePublishSession()
  if (session?.broadcastId === broadcast.id) return <PublishingView broadcast={broadcast} stream={session.stream} audioLabel={session.audioLabel} />
  if (session) {
    return (
      <p className="text-neutral-400">
        다른 방송을{' '}
        <Link to={`/broadcasts/${session.broadcastId}/publish`} className="text-emerald-400 underline">
          송출 중
        </Link>
        입니다. 그 방송을 먼저 종료하세요.
      </p>
    )
  }
  if (broadcast.status === 'ENDED') return <p className="text-neutral-400">방송이 종료되었습니다.</p>
  if (broadcast.status === 'LIVE') return <p className="text-neutral-400">다른 탭이나 기기에서 송출 중인 방송입니다.</p>
  return <PrepareView broadcast={broadcast} />
}

/** 녹화 여부 → 화면 선택·미리보기 → 송출 시작. 토큰은 약 1분만 유효하므로 화면을 고른 뒤 송출 직전에 받는다. */
function PrepareView({ broadcast }: { broadcast: Broadcast }) {
  const [step, setStep] = useState<Step>('record')
  const [record, setRecord] = useState(true)
  const [capture, setCapture] = useState<Capture | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  // 송출을 시작하면 준비물의 정리는 송출 세션이 맡는다.
  const handedOff = useRef(false)
  const mounted = useRef(true)
  const issueToken = useIssuePublishToken(broadcast.id)

  useEffect(() => {
    if (!capture) return
    return () => {
      if (!handedOff.current) releaseCapture(capture)
    }
  }, [capture])

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  async function selectScreen() {
    setMessage(null)
    try {
      const next = await captureScreen()
      // 송출 전에 "공유 중지"를 누르면 준비물만 버린다.
      next.stream.getVideoTracks()[0].addEventListener('ended', () => setCapture((c) => (c === next ? null : c)))
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
      const { token, whipUrl } = await issueToken.mutateAsync(record)
      // 송출 세션이 생기는 순간 이 화면이 내려가므로, 정리 책임을 먼저 넘겨 두고 실패하면 되찾는다.
      handedOff.current = true
      await startPublish(broadcast.id, capture, whipUrl, token)
    } catch (e) {
      handedOff.current = false
      // 연결하는 사이에 화면을 떠났으면 정리할 곳이 여기뿐이다.
      if (!mounted.current) {
        releaseCapture(capture)
        return
      }
      setMessage(e instanceof Error ? e.message : '송출을 시작하지 못했습니다.')
    } finally {
      setStarting(false)
    }
  }

  return (
    <div className="space-y-4">
      <Title broadcast={broadcast} status={broadcast.status} />

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

      <Preview stream={capture?.stream ?? null} />

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

      {message && <p className="text-sm text-red-400">{message}</p>}
    </div>
  )
}

/** 송출 중. LIVE로 바뀌는지 SSE로 따라간다. */
function PublishingView({ broadcast, stream, audioLabel }: { broadcast: Broadcast; stream: MediaStream; audioLabel: string }) {
  const { data: latest } = useBroadcast(broadcast.id)
  useBroadcastEvents(broadcast.id, true)
  const current = latest ?? broadcast

  return (
    <div className="space-y-4">
      <Title broadcast={current} status={current.status} />
      <Preview stream={stream} />
      <p className="text-sm text-neutral-300">
        {current.status === 'LIVE' ? '방송 중입니다.' : '송출을 연결했습니다. 미디어 서버가 영상을 받으면 방송이 시작됩니다.'} 다른
        페이지로 옮겨도 송출은 이어집니다. 소리: {audioLabel}
      </p>
      <EndBroadcastButton broadcastId={broadcast.id} />
    </div>
  )
}

function Title({ broadcast, status }: { broadcast: Broadcast; status: Broadcast['status'] }) {
  return (
    <div className="flex items-center gap-2">
      <StatusBadge status={status} />
      <h1 className="text-xl font-bold break-words">{broadcast.title}</h1>
    </div>
  )
}

function Preview({ stream }: { stream: MediaStream | null }) {
  const ref = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    if (ref.current) ref.current.srcObject = stream
  }, [stream])
  return <video ref={ref} autoPlay muted playsInline className="aspect-video w-full rounded-lg bg-neutral-900" />
}
