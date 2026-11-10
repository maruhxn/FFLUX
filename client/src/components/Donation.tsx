import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { useMe } from '../auth/auth'
import {
  amountError,
  DONATION_MESSAGE_MAX_LENGTH,
  DONATION_PRESETS,
  formatWon,
  TOSS_CLIENT_KEY,
  useDonate,
} from '../donation/donations'

const BUTTON = 'rounded-md bg-amber-400 px-3 py-1.5 text-sm font-semibold text-neutral-950 disabled:opacity-50'

/**
 * 방송(LIVE·ENDED)의 스트리머에게 후원한다. 금액과 메시지를 골라 서버에 주문을 만들고, 토스 결제창(카드)으로 결제한다.
 * 자기 방송에는 그리지 않는다(서버도 403). 비로그인이면 로그인으로 보낸다.
 */
export function Donation({ broadcastId, streamerId, streamerNickname }: { broadcastId: number; streamerId: number; streamerNickname: string }) {
  const { data: me } = useMe()
  const [open, setOpen] = useState(false)

  if (me?.memberId === streamerId) return null
  if (!me) {
    return (
      <Link to="/login" className={BUTTON}>
        후원하기
      </Link>
    )
  }
  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)} className={BUTTON}>
        후원하기
      </button>
    )
  }

  return (
    <section className="space-y-4 rounded-lg border border-neutral-800 p-4">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold">{streamerNickname}님에게 후원</h2>
        <button type="button" onClick={() => setOpen(false)} className="text-sm text-neutral-400 hover:text-white">
          닫기
        </button>
      </div>
      {!TOSS_CLIENT_KEY ? (
        <p className="text-sm text-red-400">결제 설정이 없어 지금은 후원할 수 없습니다.</p>
      ) : (
        <DonationForm broadcastId={broadcastId} orderName={`${streamerNickname}님 후원`} />
      )}
    </section>
  )
}

/** 금액(프리셋 또는 직접 입력)과 메시지. 메시지는 이 단계에서만 받는다. */
function DonationForm({ broadcastId, orderName }: { broadcastId: number; orderName: string }) {
  const [amount, setAmount] = useState<number>(DONATION_PRESETS[0])
  const [custom, setCustom] = useState('')
  const [message, setMessage] = useState('')
  const donate = useDonate(broadcastId, orderName)
  const chosen = custom === '' ? amount : Number(custom)
  const invalid = amountError(chosen)
  const fieldErrors = donate.error instanceof ApiError ? donate.error.fieldErrors : {}

  function submit(e: FormEvent) {
    e.preventDefault()
    if (invalid) return
    donate.mutate({ amount: chosen, message })
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {DONATION_PRESETS.map((preset) => (
          <button
            key={preset}
            type="button"
            onClick={() => {
              setAmount(preset)
              setCustom('')
            }}
            className={`rounded-md border px-3 py-1.5 text-sm ${
              custom === '' && amount === preset ? 'border-amber-400 text-amber-300' : 'border-neutral-700 hover:bg-neutral-800'
            }`}
          >
            {formatWon(preset)}
          </button>
        ))}
        <input
          type="number"
          inputMode="numeric"
          value={custom}
          onChange={(e) => setCustom(e.target.value)}
          placeholder="직접 입력(원)"
          className="w-36 rounded-md border border-neutral-700 bg-neutral-900 px-3 py-1.5 text-sm outline-none focus:border-amber-400"
        />
      </div>
      {custom !== '' && invalid && <p className="text-xs text-red-400">{invalid}</p>}
      {fieldErrors.amount && <p className="text-xs text-red-400">{fieldErrors.amount}</p>}
      <textarea
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        maxLength={DONATION_MESSAGE_MAX_LENGTH}
        rows={2}
        placeholder="메시지(선택)"
        className="w-full rounded-md border border-neutral-700 bg-neutral-900 px-3 py-2 text-neutral-100 outline-none placeholder:text-neutral-600 focus:border-amber-400"
      />
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs text-red-400">{fieldErrors.message ?? (donate.error && !fieldErrors.amount ? donate.error.message : null)}</span>
        <div className="flex items-center gap-3">
          <span className="text-xs text-neutral-500 tabular-nums">
            {message.length}/{DONATION_MESSAGE_MAX_LENGTH}
          </span>
          <button type="submit" disabled={!!invalid || donate.isPending} className={BUTTON}>
            {invalid ? '후원하기' : `${formatWon(chosen)} 후원하기`}
          </button>
        </div>
      </div>
    </form>
  )
}
