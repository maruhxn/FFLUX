import { ANONYMOUS, loadTossPayments, type TossPaymentsWidgets } from '@tosspayments/tosspayments-sdk'
import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { useMe } from '../auth/auth'
import {
  amountError,
  DONATION_MESSAGE_MAX_LENGTH,
  DONATION_PRESETS,
  formatWon,
  TOSS_CLIENT_KEY,
  useRequestDonation,
  type DonationOrder,
} from '../donation/donations'

const BUTTON = 'rounded-md bg-amber-400 px-3 py-1.5 text-sm font-semibold text-neutral-950 disabled:opacity-50'

/**
 * 방송(LIVE·ENDED)의 스트리머에게 후원한다. 금액과 메시지를 골라 서버에 주문을 만들고, 결제위젯으로 결제한다.
 * 자기 방송에는 그리지 않는다(서버도 403). 비로그인이면 로그인으로 보낸다.
 */
export function Donation({ broadcastId, streamerId, streamerNickname }: { broadcastId: number; streamerId: number; streamerNickname: string }) {
  const { data: me } = useMe()
  const [open, setOpen] = useState(false)
  const [order, setOrder] = useState<DonationOrder | null>(null)

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

  function close() {
    setOpen(false)
    setOrder(null)
  }

  return (
    <section className="space-y-4 rounded-lg border border-neutral-800 p-4">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold">{streamerNickname}님에게 후원</h2>
        <button type="button" onClick={close} className="text-sm text-neutral-400 hover:text-white">
          닫기
        </button>
      </div>
      {!TOSS_CLIENT_KEY ? (
        <p className="text-sm text-red-400">결제 설정이 없어 지금은 후원할 수 없습니다.</p>
      ) : order ? (
        <PaymentWidget order={order} orderName={`${streamerNickname}님 후원`} onBack={() => setOrder(null)} />
      ) : (
        <DonationForm broadcastId={broadcastId} onOrdered={setOrder} />
      )}
    </section>
  )
}

/** 금액(프리셋 또는 직접 입력)과 메시지. 메시지는 이 단계에서만 받는다. */
function DonationForm({ broadcastId, onOrdered }: { broadcastId: number; onOrdered: (order: DonationOrder) => void }) {
  const [amount, setAmount] = useState<number>(DONATION_PRESETS[0])
  const [custom, setCustom] = useState('')
  const [message, setMessage] = useState('')
  const request = useRequestDonation(broadcastId)
  const chosen = custom === '' ? amount : Number(custom)
  const invalid = amountError(chosen)
  const fieldErrors = request.error instanceof ApiError ? request.error.fieldErrors : {}

  function submit(e: FormEvent) {
    e.preventDefault()
    if (invalid) return
    request.mutate({ amount: chosen, message }, { onSuccess: onOrdered })
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
        <span className="text-xs text-red-400">{fieldErrors.message ?? (request.error && !fieldErrors.amount ? request.error.message : null)}</span>
        <div className="flex items-center gap-3">
          <span className="text-xs text-neutral-500 tabular-nums">
            {message.length}/{DONATION_MESSAGE_MAX_LENGTH}
          </span>
          <button type="submit" disabled={!!invalid || request.isPending} className={BUTTON}>
            {invalid ? '후원하기' : `${formatWon(chosen)} 후원하기`}
          </button>
        </div>
      </div>
    </form>
  )
}

/** 토스 결제위젯(결제수단, 약관). 결제하면 결제창을 거쳐 successUrl·failUrl 페이지로 돌아온다. */
function PaymentWidget({ order, orderName, onBack }: { order: DonationOrder; orderName: string; onBack: () => void }) {
  const [widgets, setWidgets] = useState<TossPaymentsWidgets | null>(null)
  const [error, setError] = useState<string | null>(null)
  // 결제창이 열려 있는 동안 다시 요청하면 SDK가 거절하므로 막아 둔다.
  const [paying, setPaying] = useState(false)

  useEffect(() => {
    let cancelled = false
    const rendered: { destroy: () => Promise<void> }[] = []
    ;(async () => {
      const tossPayments = await loadTossPayments(TOSS_CLIENT_KEY)
      // StrictMode가 효과를 두 번 돌리므로, 먼저 정리된 쪽은 그리기 전에 멈춘다.
      if (cancelled) return
      const instance = tossPayments.widgets({ customerKey: ANONYMOUS })
      await instance.setAmount({ currency: 'KRW', value: order.amount })
      rendered.push(
        ...(await Promise.all([
          instance.renderPaymentMethods({ selector: '#payment-method', variantKey: 'DEFAULT' }),
          instance.renderAgreement({ selector: '#agreement', variantKey: 'AGREEMENT' }),
        ])),
      )
      if (!cancelled) setWidgets(instance)
    })().catch((e: unknown) => {
      if (!cancelled) setError(e instanceof Error ? e.message : '결제위젯을 불러오지 못했습니다.')
    })
    return () => {
      cancelled = true
      rendered.forEach((widget) => void widget.destroy())
    }
  }, [order.amount])

  async function pay() {
    if (!widgets) return
    setError(null)
    setPaying(true)
    try {
      await widgets.requestPayment({
        orderId: order.orderId,
        orderName,
        successUrl: `${window.location.origin}/donations/success`,
        failUrl: `${window.location.origin}/donations/fail`,
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : '결제를 요청하지 못했습니다.')
    } finally {
      setPaying(false)
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-neutral-300">{formatWon(order.amount)}을 결제합니다.</p>
      {/* 결제위젯은 흰 바탕에 그려진다. */}
      <div className="overflow-hidden rounded-md bg-white">
        <div id="payment-method" />
        <div id="agreement" />
      </div>
      {error && <p className="text-sm text-red-400">{error}</p>}
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onBack} className="rounded-md border border-neutral-700 px-3 py-1.5 text-sm hover:bg-neutral-800">
          금액 다시 고르기
        </button>
        <button type="button" onClick={pay} disabled={!widgets || paying} className={BUTTON}>
          결제하기
        </button>
      </div>
    </div>
  )
}
