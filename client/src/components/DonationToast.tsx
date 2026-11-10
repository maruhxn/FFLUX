import { formatWon, type DonationAlert } from '../donation/donations'

/** 플레이어 위 후원 알림. 부모는 `relative`(또는 `absolute`)여야 한다. 영상 위에 겹치기만 하고 클릭은 플레이어로 넘긴다. */
export function DonationToast({ alert }: { alert: DonationAlert | null }) {
  if (!alert) return null
  return (
    <div className="pointer-events-none absolute inset-x-0 top-3 flex justify-center px-3">
      <div role="status" className="max-w-md rounded-lg bg-amber-400/95 px-4 py-2 text-center text-neutral-950 shadow-lg">
        <p className="font-semibold break-words">
          {alert.donorNickname}님이 {formatWon(alert.amount)} 후원했습니다
        </p>
        {alert.message && <p className="text-sm break-words whitespace-pre-wrap">{alert.message}</p>}
      </div>
    </div>
  )
}
