import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { useMe } from '../auth/auth'
import { useBroadcast, useIssuePublishToken, type Broadcast } from '../broadcast/broadcast'
import { useBroadcastEvents } from '../broadcast/events'
import {
  captureCamera,
  captureScreen,
  flipCamera,
  openCam,
  publishesCamera,
  releaseCapture,
  withPip,
  type Capture,
  type Facing,
} from '../broadcast/capture'
import { supportsPip } from '../broadcast/pip'
import {
  flipPublishingCamera,
  startPublish,
  useLostBroadcastId,
  usePublishSession,
  type PublishSession,
} from '../broadcast/publishSession'
import { EndBroadcastButton } from '../components/EndBroadcastButton'
import { ActionBar, Hints, Row, STAGE_BUTTON, StageMessage, Studio } from '../components/Studio'

/** 송출(`/broadcasts/:id/publish`). PC는 화면 공유, 모바일은 카메라로 보낸다. 스트리머만, READY 방송에서 시작한다. */
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
  const lostBroadcastId = useLostBroadcastId()
  if (session?.broadcastId === broadcast.id) return <PublishingView broadcast={broadcast} session={session} />
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
  if (lostBroadcastId === broadcast.id) {
    return (
      <p className="text-neutral-400">
        방송이 종료되었습니다. 이 기기에서 종료하지 않았다면 송출이 끊긴 것입니다(앱 전환, 화면 꺼짐, 네트워크 끊김).
      </p>
    )
  }
  if (broadcast.status === 'ENDED') return <p className="text-neutral-400">방송이 종료되었습니다.</p>
  if (broadcast.status === 'LIVE') return <p className="text-neutral-400">다른 탭이나 기기에서 송출 중인 방송입니다.</p>
  return <PrepareView broadcast={broadcast} />
}

/**
 * 녹화 여부 → 화면(카메라) 선택·미리보기 → 송출 시작. 토큰은 약 1분만 유효하므로 화면을 고른 뒤 송출 직전에 받는다.
 * PC는 캠을 켜면 화면 우하단에 캠을 겹친 합성 영상을 미리 보고 그대로 송출한다.
 */
function PrepareView({ broadcast }: { broadcast: Broadcast }) {
  const [camera] = useState(publishesCamera)
  const [pipAvailable] = useState(() => !camera && supportsPip())
  const [step, setStep] = useState<Step>('record')
  const [record, setRecord] = useState(true)
  const [capture, setCapture] = useState<Capture | null>(null)
  const [cam, setCam] = useState<MediaStream | null>(null)
  const [cams, setCams] = useState<MediaDeviceInfo[]>([])
  // 화면과 캠이 모두 있으면 둘을 합성한 준비물.
  const [pipCapture, setPipCapture] = useState<Capture | null>(null)
  // 캠 열기 요청 차례. 열리는 사이에 끄거나 다른 캠을 고르면 늦게 열린 캠은 버린다.
  const camRequest = useRef(0)
  const outgoing = pipCapture ?? capture
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
    if (!cam) return
    return () => {
      if (!handedOff.current) cam.getTracks().forEach((t) => t.stop())
    }
  }, [cam])

  // 화면이나 캠이 바뀌면 다시 합성한다. 원본 화면·캠은 위의 정리가 맡고, 여기서는 합성만 멈춘다.
  useEffect(() => {
    if (!capture || !cam) return
    const next = withPip(capture, cam)
    // 합성 Worker(외부 자원)를 만들고 정리하는 동기화라 이펙트에서 상태를 바꾼다.
    // oxlint-disable-next-line react/set-state-in-effect
    setPipCapture(next)
    return () => {
      if (!handedOff.current) next.pip?.stop()
      setPipCapture(null)
    }
  }, [capture, cam])

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  function adopt(next: Capture) {
    // 송출 전에 "공유 중지"를 누르거나 카메라가 꺼지면 준비물만 버린다.
    next.sourceVideo.addEventListener('ended', () => setCapture((c) => (c === next ? null : c)))
    setCapture(next)
  }

  async function selectSource() {
    setMessage(null)
    try {
      adopt(camera ? await captureCamera('user') : await captureScreen())
    } catch {
      setMessage(camera ? '카메라와 마이크를 쓸 수 없습니다. 브라우저 권한을 확인하세요.' : '화면 공유를 시작하지 못했습니다.')
    }
  }

  /** 캠을 켜거나 다른 캠으로 바꾼다. 못 열면 화면만으로 송출하게 둔다. */
  async function switchCam(deviceId: string | null) {
    setMessage(null)
    const request = ++camRequest.current
    try {
      const next = await openCam(deviceId)
      if (request !== camRequest.current || !mounted.current) {
        next.getTracks().forEach((t) => t.stop())
        return
      }
      // 송출 전에 캠이 끊기면(뽑힘, 권한 회수) 끈 것으로 본다.
      next.getVideoTracks()[0].addEventListener('ended', () => {
        setCam((c) => (c === next ? null : c))
        setMessage('캠 연결이 끊겼습니다. 화면만으로 송출할 수 있습니다.')
      })
      setCam(next)
      setCams((await navigator.mediaDevices.enumerateDevices()).filter((d) => d.kind === 'videoinput'))
    } catch {
      if (request !== camRequest.current) return
      setCam(null)
      setMessage('캠을 쓸 수 없습니다(권한 거부 또는 캠 없음). 화면만으로 송출할 수 있습니다.')
    }
  }

  function turnOffCam() {
    camRequest.current++
    setCam(null)
  }

  async function flip() {
    if (!capture) return
    setMessage(null)
    try {
      const next = await flipCamera(capture)
      adopt(next)
      if (next.facing === capture.facing) setMessage(FLIP_FALLBACK_MESSAGE)
    } catch {
      setCapture(null)
      setMessage('카메라를 바꾸지 못했습니다. 다시 켜 보세요.')
    }
  }

  async function start() {
    if (!outgoing) return
    setMessage(null)
    setStarting(true)
    try {
      const { token, whipUrl } = await issueToken.mutateAsync(record)
      // 송출 세션이 생기는 순간 이 화면이 내려가므로, 정리 책임을 먼저 넘겨 두고 실패하면 되찾는다.
      handedOff.current = true
      await startPublish(broadcast.id, outgoing, whipUrl, token)
    } catch (e) {
      handedOff.current = false
      // 연결하는 사이에 화면을 떠났으면 정리할 곳이 여기뿐이다.
      if (!mounted.current) {
        releaseCapture(outgoing)
        return
      }
      // 연결하는 사이에 "공유 중지"로 화면이 버려졌으면 남은 마이크·캠·합성도 여기서 정리한다.
      if (outgoing.sourceVideo.readyState === 'ended') {
        releaseCapture(outgoing)
        turnOffCam()
      }
      setMessage(e instanceof Error ? e.message : '송출을 시작하지 못했습니다.')
    } finally {
      setStarting(false)
    }
  }

  const sourceButton =
    camera && capture?.facing ? (
      <FlipCameraButton facing={capture.facing} onClick={flip} disabled={starting} />
    ) : (
      <button type="button" onClick={selectSource} className={SECONDARY_BUTTON}>
        {camera ? '카메라 켜기' : capture ? '화면 다시 고르기' : '공유할 화면 고르기'}
      </button>
    )

  return (
    <Studio
      heading="방송하기"
      broadcast={broadcast}
      stage={
        <VideoStage stream={outgoing?.stream ?? null}>
          {!outgoing && step === 'capture' && (
            <StageMessage
              title={camera ? '카메라를 켜면 여기에 미리보기가 나옵니다.' : '공유할 화면을 고르면 여기에 미리보기가 나옵니다.'}
              detail={camera ? '휴대폰 카메라와 마이크로 송출합니다.' : '탭, 창, 전체 화면 중 하나를 고를 수 있습니다.'}
              action={
                <button type="button" onClick={selectSource} className={STAGE_BUTTON}>
                  {camera ? '카메라 켜기' : '공유할 화면 고르기'}
                </button>
              }
            />
          )}
        </VideoStage>
      }
    >
      {step === 'record' && <RecordDialog broadcast={broadcast} record={record} onChange={setRecord} onNext={() => setStep('capture')} />}

      <TitleRow broadcast={broadcast} />
      <Row label="녹화">
        <p className="pt-2.5 font-semibold">{record ? '녹화함' : '녹화 안 함'}</p>
        <Hints lines={[record ? RECORD_OPTIONS[0].detail : RECORD_OPTIONS[1].detail]} />
      </Row>
      <Row label={camera ? '카메라' : '송출 화면'}>
        <div>{sourceButton}</div>
        <Hints
          lines={[
            camera
              ? '송출 중 다른 앱으로 전환하거나 화면을 끄면 방송이 종료됩니다.'
              : '마이크 소리가 함께 나갑니다. 공유 창에서 "탭 오디오 공유"(macOS) 또는 "시스템 오디오 공유"(Windows)를 켜면 화면 소리도 섞입니다.',
          ]}
        />
      </Row>
      {pipAvailable && (
        <Row label="캠">
          <div className="flex flex-wrap items-center gap-3 pt-1.5">
            <Switch
              checked={cam !== null}
              disabled={!capture || starting}
              onChange={(on) => (on ? void switchCam(null) : turnOffCam())}
            />
            {cam && cams.length > 1 && (
              <select
                value={cam.getVideoTracks()[0]?.getSettings().deviceId ?? ''}
                disabled={starting}
                onChange={(e) => void switchCam(e.target.value)}
                className="rounded-md border border-neutral-700 bg-neutral-900 px-2 py-1.5 text-sm"
              >
                {cams.map((d, i) => (
                  <option key={d.deviceId} value={d.deviceId}>
                    {d.label || `캠 ${i + 1}`}
                  </option>
                ))}
              </select>
            )}
          </div>
          <Hints lines={capture ? ['화면 우하단에 캠을 겹쳐 보냅니다.'] : ['화면 우하단에 캠을 겹쳐 보냅니다.', '화면을 먼저 고르세요.']} />
        </Row>
      )}
      {capture && (
        <Row label="소리">
          <p className="pt-2.5 font-semibold break-keep">{capture.audioLabel}</p>
        </Row>
      )}
      <ActionBar error={message}>
        <button
          type="button"
          onClick={start}
          disabled={!capture || starting}
          className="rounded-md bg-emerald-500 px-5 py-2 font-semibold text-neutral-950 disabled:opacity-50"
        >
          송출 시작
        </button>
      </ActionBar>
    </Studio>
  )
}

/** 송출 중. LIVE로 바뀌는지 SSE로 따라간다. 카메라 송출이면 전면/후면을 바꿀 수 있다. */
function PublishingView({ broadcast, session }: { broadcast: Broadcast; session: PublishSession }) {
  const { data: latest } = useBroadcast(broadcast.id)
  useBroadcastEvents(broadcast.id, true)
  const current = latest ?? broadcast
  const [flipping, setFlipping] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function flip() {
    setMessage(null)
    setFlipping(true)
    try {
      if (!(await flipPublishingCamera())) setMessage(FLIP_FALLBACK_MESSAGE)
    } catch (e) {
      setMessage(e instanceof Error ? e.message : '카메라를 바꾸지 못했습니다.')
    } finally {
      setFlipping(false)
    }
  }

  return (
    <Studio heading="방송하기" broadcast={current} stage={<VideoStage stream={session.stream} />}>
      <TitleRow broadcast={current} />
      <Row label="송출">
        <p className="pt-2.5 font-semibold break-keep">
          {current.status === 'LIVE' ? '방송 중입니다.' : '송출을 연결했습니다. 미디어 서버가 영상을 받으면 방송이 시작됩니다.'}
        </p>
        <Hints lines={['다른 페이지로 옮겨도 송출은 이어집니다.']} />
      </Row>
      {session.facing && (
        <Row label="카메라">
          <div>
            <FlipCameraButton facing={session.facing} onClick={flip} disabled={flipping} />
          </div>
        </Row>
      )}
      <Row label="소리">
        <p className="pt-2.5 font-semibold break-keep">{session.audioLabel}</p>
      </Row>
      <ActionBar error={message}>
        <EndBroadcastButton broadcastId={broadcast.id} />
      </ActionBar>
    </Studio>
  )
}

const FLIP_FALLBACK_MESSAGE = '반대쪽 카메라를 열지 못해 지금 카메라를 그대로 씁니다.'

/** 전면/후면 카메라 전환 버튼. 라벨은 바꿀 방향이다. */
function FlipCameraButton({ facing, onClick, disabled }: { facing: Facing; onClick: () => void; disabled: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={SECONDARY_BUTTON}
    >
      {facing === 'user' ? '후면 카메라로' : '전면 카메라로'}
    </button>
  )
}

const SECONDARY_BUTTON =
  'rounded-md border border-neutral-700 px-4 py-2 text-sm font-semibold hover:bg-neutral-800 disabled:opacity-50'

const RECORD_OPTIONS = [
  { value: true, label: '녹화하기', detail: '방송이 끝나면 다시보기로 남고, 시청자가 댓글을 달 수 있습니다.' },
  { value: false, label: '녹화하지 않기', detail: '라이브로만 방송하고 다시보기는 남지 않습니다.' },
]

/** 방송 시작 모달. 녹화 여부는 송출 토큰을 받을 때 함께 보내므로 화면을 고르기 전에 정한다. */
function RecordDialog({
  broadcast,
  record,
  onChange,
  onNext,
}: {
  broadcast: Broadcast
  record: boolean
  onChange: (record: boolean) => void
  onNext: () => void
}) {
  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/70 p-4">
      <div role="dialog" aria-modal="true" aria-labelledby="record-dialog-title" className="w-full max-w-md overflow-hidden rounded-lg border border-neutral-800 bg-neutral-950">
        <div className="border-b border-neutral-800 px-5 py-4">
          <h2 id="record-dialog-title" className="text-lg font-bold">
            방송 시작
          </h2>
          <p className="mt-1 truncate text-sm text-neutral-400">{broadcast.title}</p>
        </div>
        <div role="radiogroup" aria-label="녹화" className="space-y-2 px-5 py-5">
          <p className="mb-3 text-sm font-semibold text-neutral-300">녹화</p>
          {RECORD_OPTIONS.map((option) => (
            <label
              key={option.label}
              className="flex cursor-pointer gap-3 rounded-md border border-neutral-700 p-3 has-checked:border-emerald-500 has-checked:bg-emerald-500/10"
            >
              <input
                type="radio"
                name="record"
                checked={record === option.value}
                onChange={() => onChange(option.value)}
                className="mt-1 accent-emerald-500"
              />
              <span className="space-y-0.5 break-keep">
                <span className="block font-semibold">{option.label}</span>
                <span className="block text-xs text-neutral-400">{option.detail}</span>
              </span>
            </label>
          ))}
        </div>
        <div className="flex justify-end gap-2 border-t border-neutral-800 px-5 py-4">
          <Link to={`/broadcasts/${broadcast.id}`} className={SECONDARY_BUTTON}>
            취소
          </Link>
          <button
            type="button"
            onClick={onNext}
            className="rounded-md bg-emerald-500 px-4 py-2 text-sm font-semibold text-neutral-950"
          >
            다음
          </button>
        </div>
      </div>
    </div>
  )
}

function TitleRow({ broadcast }: { broadcast: Broadcast }) {
  return (
    <Row label="방송 제목">
      <div className="flex items-start justify-between gap-3 pt-2.5">
        <p className="min-w-0 font-semibold break-words">{broadcast.title}</p>
        <Link to={`/broadcasts/${broadcast.id}/edit`} className="shrink-0 text-sm text-neutral-400 underline hover:text-white">
          수정
        </Link>
      </div>
    </Row>
  )
}

/** 미리보기 영상 자리. 영상이 없을 때 `children`을 가운데에 겹친다. */
function VideoStage({ stream, children }: { stream: MediaStream | null; children?: ReactNode }) {
  const ref = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    if (ref.current) ref.current.srcObject = stream
  }, [stream])
  return (
    <div className="relative aspect-video bg-black">
      <video ref={ref} autoPlay muted playsInline className="size-full object-contain" />
      {children && <div className="absolute inset-0 flex items-center justify-center">{children}</div>}
    </div>
  )
}

function Switch({ checked, disabled, onChange }: { checked: boolean; disabled: boolean; onChange: (checked: boolean) => void }) {
  return (
    <label className="inline-flex cursor-pointer items-center gap-2 text-sm has-disabled:cursor-not-allowed has-disabled:opacity-50">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className="peer sr-only"
      />
      <span className="relative h-6 w-11 rounded-full bg-neutral-700 transition peer-checked:bg-emerald-500 peer-focus-visible:ring-2 peer-focus-visible:ring-emerald-300 after:absolute after:top-0.5 after:left-0.5 after:size-5 after:rounded-full after:bg-white after:transition peer-checked:after:translate-x-5" />
      {checked ? '켜짐' : '꺼짐'}
    </label>
  )
}
