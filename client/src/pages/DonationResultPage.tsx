import { useEffect, useRef } from 'react'
import { Link, useSearchParams } from 'react-router'
import { formatWon, useCancelDonation, useConfirmDonation, type DonationOrder } from '../donation/donations'

/** 결제창 승인 뒤 돌아오는 successUrl. 서버가 결제를 승인해 후원을 확정한다. */
export function DonationSuccessPage() {
  const [params] = useSearchParams()
  const confirm = useConfirmDonation()
  const started = useRef(false)

  useEffect(() => {
    // StrictMode에서 두 번 부르지 않게 한다(서버는 다시 불러도 한 번만 확정한다).
    if (started.current) return
    started.current = true
    confirm.mutate({
      orderId: params.get('orderId') ?? '',
      paymentKey: params.get('paymentKey') ?? '',
      amount: Number(params.get('amount')),
    })
  }, [confirm, params])

  if (confirm.isSuccess) {
    return <Result title="후원했습니다" detail={`${formatWon(confirm.data.amount)}을 후원했습니다. 고맙습니다!`} order={confirm.data} />
  }
  if (confirm.isError) return <Result title="후원을 확정하지 못했습니다" detail={confirm.error.message} />
  return <p className="text-neutral-400">결제를 확인하는 중…</p>
}

/** 결제창에서 실패·취소해 돌아오는 failUrl. 주문이 있으면 후원을 취소한다. */
export function DonationFailPage() {
  const [params] = useSearchParams()
  const cancel = useCancelDonation()
  const started = useRef(false)
  // 결제창을 닫아 취소하면(PAY_PROCESS_CANCELED) 토스가 orderId를 주지 않는다.
  const orderId = params.get('orderId')

  useEffect(() => {
    if (started.current || !orderId) return
    started.current = true
    cancel.mutate(orderId)
  }, [cancel, orderId])

  return <Result title="결제하지 못했습니다" detail={params.get('message') ?? '결제가 취소되었습니다.'} order={cancel.data} />
}

function Result({ title, detail, order }: { title: string; detail: string; order?: DonationOrder }) {
  return (
    <div className="mx-auto max-w-md space-y-4 py-10 text-center">
      <h1 className="text-xl font-bold">{title}</h1>
      <p className="text-neutral-300">{detail}</p>
      <Link to={order ? `/broadcasts/${order.broadcastId}` : '/'} className="inline-block text-emerald-400 hover:underline">
        {order ? '방송으로 돌아가기' : '홈으로'}
      </Link>
    </div>
  )
}
