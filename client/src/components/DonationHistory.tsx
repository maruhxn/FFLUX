import { formatDateTime } from '../broadcast/broadcast'
import { formatWon, useDonationHistory } from '../donation/donations'

/** 방송의 확정된 후원 내역과 합계, 최신순. 비로그인도 본다. */
export function DonationHistory({ broadcastId }: { broadcastId: number }) {
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useDonationHistory(broadcastId)
  const items = data?.pages.flatMap((page) => page.items) ?? []

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">
        후원 {data && <span className="text-amber-300">{formatWon(data.pages[0].totalAmount)}</span>}
      </h2>
      {error && <p className="text-sm text-red-400">{error.message}</p>}
      {!isPending && !error && items.length === 0 && <p className="text-sm text-neutral-500">아직 후원이 없습니다.</p>}
      <ul className="space-y-2">
        {items.map((item) => (
          <li key={item.id} className="rounded-md border border-neutral-800 px-3 py-2">
            <div className="flex flex-wrap items-baseline gap-x-2">
              <span className="font-semibold">{item.donorNickname}</span>
              <span className="text-amber-300 tabular-nums">{formatWon(item.amount)}</span>
              <span className="text-xs text-neutral-500">{formatDateTime(item.confirmedAt)}</span>
            </div>
            {item.message && <p className="text-sm break-words whitespace-pre-wrap text-neutral-300">{item.message}</p>}
          </li>
        ))}
      </ul>
      {hasNextPage && (
        <button
          type="button"
          onClick={() => void fetchNextPage()}
          disabled={isFetchingNextPage}
          className="rounded-md border border-neutral-700 px-3 py-1.5 text-sm hover:bg-neutral-800 disabled:opacity-50"
        >
          더 보기
        </button>
      )}
    </section>
  )
}
