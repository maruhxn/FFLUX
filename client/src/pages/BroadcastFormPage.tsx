import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { useMe } from '../auth/auth'
import {
  useBroadcast,
  useEditBroadcast,
  useRegisterBroadcast,
  type Broadcast,
  type BroadcastForm,
} from '../broadcast/broadcast'
import { ActionBar, Row, STAGE_BUTTON, StageMessage, Studio } from '../components/Studio'

/** 방송 등록(`/broadcasts/new`). */
export function BroadcastNewPage() {
  const navigate = useNavigate()
  const register = useRegisterBroadcast()
  const { data: me, isPending } = useMe()
  if (isPending) return null
  if (!me) return <Navigate to="/login" replace />

  return (
    <BroadcastFormView
      heading="방송 등록"
      submitLabel="등록하기"
      initial={{ title: '', description: '' }}
      error={register.error}
      pending={register.isPending}
      onSubmit={(form) => register.mutate(form, { onSuccess: ({ id }) => navigate(`/broadcasts/${id}`) })}
    />
  )
}

/** 방송 수정(`/broadcasts/:id/edit`). */
export function BroadcastEditPage() {
  const id = Number(useParams().id)
  const { data: broadcast, error } = useBroadcast(id)
  if (error) return <p className="text-red-400">{error.message}</p>
  if (!broadcast) return null
  return <BroadcastEditForm broadcast={broadcast} />
}

function BroadcastEditForm({ broadcast }: { broadcast: Broadcast }) {
  const navigate = useNavigate()
  const edit = useEditBroadcast(broadcast.id)
  return (
    <BroadcastFormView
      heading="방송 수정"
      submitLabel="저장하기"
      broadcast={broadcast}
      initial={{ title: broadcast.title, description: broadcast.description ?? '' }}
      error={edit.error}
      pending={edit.isPending}
      onSubmit={(form) => edit.mutate(form, { onSuccess: () => navigate(`/broadcasts/${broadcast.id}`) })}
    />
  )
}

/** 스튜디오 틀에 화면 자리 안내와 방송 정보 입력을 둔다. 등록 전이면 `broadcast`가 없다. */
function BroadcastFormView({
  heading,
  submitLabel,
  broadcast,
  initial,
  error,
  pending,
  onSubmit,
}: {
  heading: string
  submitLabel: string
  broadcast?: Broadcast
  initial: BroadcastForm
  error: Error | null
  pending: boolean
  onSubmit: (form: BroadcastForm) => void
}) {
  const [form, setForm] = useState(initial)
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  function submit(e: FormEvent) {
    e.preventDefault()
    onSubmit(form)
  }

  return (
    <Studio heading={heading} broadcast={broadcast} stage={<Stage broadcast={broadcast} />}>
      <form onSubmit={submit}>
        <Row label="방송 제목" required>
          <input
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            maxLength={50}
            required
            placeholder="방송 제목을 입력하세요."
            className={INPUT}
          />
          <FieldHints error={fieldErrors.title} count={`${form.title.length}/50`} />
        </Row>
        <Row label="방송 설명">
          <textarea
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            maxLength={500}
            rows={4}
            placeholder="시청자에게 보여 줄 방송 소개를 입력하세요. (선택)"
            className={INPUT}
          />
          <FieldHints
            error={fieldErrors.description}
            count={`${form.description.length}/500`}
            lines={['방송 상세 화면에 제목과 함께 노출됩니다.']}
          />
        </Row>
        <ActionBar error={error && Object.keys(fieldErrors).length === 0 ? error.message : null}>
          <button
            type="submit"
            disabled={pending}
            className="rounded-md bg-emerald-500 px-5 py-2 font-semibold text-neutral-950 disabled:opacity-50"
          >
            {submitLabel}
          </button>
        </ActionBar>
      </form>
    </Studio>
  )
}

const INPUT =
  'w-full rounded-md border border-neutral-700 bg-neutral-900 px-3 py-2.5 text-neutral-100 outline-none placeholder:text-neutral-600 focus:border-emerald-500'

const PUBLISH_GUIDE = 'PC는 화면 공유, 휴대폰은 카메라로 송출하고, 녹화 여부는 방송 시작 때 고릅니다.'

/** 화면 자리. 영상은 송출 화면에서만 다루므로 여기서는 다음 할 일을 안내한다. */
function Stage({ broadcast }: { broadcast?: Broadcast }) {
  const [title, detail] = !broadcast
    ? ['방송 정보를 등록하면 송출을 시작할 수 있습니다.', PUBLISH_GUIDE]
    : broadcast.status === 'LIVE'
      ? ['방송 중입니다.', '바꾼 제목과 설명은 시청자 화면에 바로 반영됩니다.']
      : ['아직 송출 전입니다.', PUBLISH_GUIDE]

  return (
    <div className="flex h-56 items-center justify-center bg-black">
      <StageMessage
        title={title}
        detail={detail}
        action={
          broadcast &&
          broadcast.status !== 'ENDED' && (
            <Link to={`/broadcasts/${broadcast.id}/publish`} className={STAGE_BUTTON}>
              송출 화면으로
            </Link>
          )
        }
      />
    </div>
  )
}

function FieldHints({ error, count, lines = [] }: { error?: string; count: string; lines?: string[] }) {
  return (
    <div className="flex justify-between gap-3 text-xs">
      <ul className="space-y-0.5 text-neutral-500">
        {error && <li className="text-red-400">• {error}</li>}
        {lines.map((line) => (
          <li key={line}>• {line}</li>
        ))}
      </ul>
      <span className="shrink-0 text-neutral-500">{count}</span>
    </div>
  )
}
