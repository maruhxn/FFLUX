import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { useMe } from '../auth/auth'
import {
  useBroadcast,
  useEditBroadcast,
  useRegisterBroadcast,
  type Broadcast,
  type BroadcastForm,
} from '../broadcast/broadcast'
import { Field } from '../components/Field'

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
      initial={{ title: broadcast.title, description: broadcast.description ?? '' }}
      error={edit.error}
      pending={edit.isPending}
      onSubmit={(form) => edit.mutate(form, { onSuccess: () => navigate(`/broadcasts/${broadcast.id}`) })}
    />
  )
}

function BroadcastFormView({
  heading,
  submitLabel,
  initial,
  error,
  pending,
  onSubmit,
}: {
  heading: string
  submitLabel: string
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
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-2xl font-bold">{heading}</h1>
      <form onSubmit={submit} className="space-y-4">
        <Field
          label="제목"
          hint="50자 이하"
          error={fieldErrors.title}
          value={form.title}
          onChange={(e) => setForm({ ...form, title: e.target.value })}
          maxLength={50}
          required
        />
        <label className="block space-y-1">
          <span className="text-sm text-neutral-300">설명 (선택)</span>
          <textarea
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            maxLength={500}
            rows={4}
            className="w-full rounded-md border border-neutral-700 bg-neutral-900 px-3 py-2 text-neutral-100 outline-none focus:border-emerald-500"
          />
          {fieldErrors.description ? (
            <span className="block text-xs text-red-400">{fieldErrors.description}</span>
          ) : (
            <span className="block text-xs text-neutral-500">500자 이하</span>
          )}
        </label>
        {error && Object.keys(fieldErrors).length === 0 && <p className="text-sm text-red-400">{error.message}</p>}
        <button
          type="submit"
          disabled={pending}
          className="w-full rounded-md bg-emerald-500 px-3 py-2 font-semibold text-neutral-950 disabled:opacity-50"
        >
          {submitLabel}
        </button>
      </form>
    </div>
  )
}
