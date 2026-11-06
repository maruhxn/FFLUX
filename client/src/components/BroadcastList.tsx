import { Link } from 'react-router'
import { formatDateTime, useBroadcastList, type BroadcastListKind } from '../broadcast/broadcast'
import { StatusBadge } from './StatusBadge'

/** 최신순 방송 목록. 다음 페이지는 "더 보기"로 읽는다. */
export function BroadcastList({ kind, empty }: { kind: BroadcastListKind; empty: string }) {
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useBroadcastList(kind)

  if (isPending) return <p className="text-neutral-400">불러오는 중…</p>
  if (error) return <p className="text-red-400">{error.message}</p>

  const items = data.pages.flatMap((page) => page.items)
  if (items.length === 0) return <p className="text-neutral-400">{empty}</p>

  return (
    <div className="space-y-4">
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((b) => (
          <li key={b.id}>
            <Link
              to={`/broadcasts/${b.id}`}
              className="block space-y-2 rounded-lg border border-neutral-800 bg-neutral-900 p-4 hover:border-neutral-600"
            >
              <div className="flex items-center gap-2">
                <StatusBadge status={b.status} />
                <span className="truncate text-sm text-neutral-400">{b.streamerNickname}</span>
              </div>
              <p className="truncate font-semibold">{b.title}</p>
              <p className="text-xs text-neutral-500">{formatDateTime(b.createdAt)}</p>
            </Link>
          </li>
        ))}
      </ul>
      {hasNextPage && (
        <button
          type="button"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="w-full rounded-md border border-neutral-700 py-2 text-sm hover:bg-neutral-800 disabled:opacity-50"
        >
          더 보기
        </button>
      )}
    </div>
  )
}
