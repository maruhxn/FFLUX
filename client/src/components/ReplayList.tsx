import { Link } from 'react-router'
import { formatDateTime, formatDuration, useReplayList } from '../broadcast/broadcast'

/** 최신순 다시보기 목록. 다음 페이지는 "더 보기"로 읽는다. */
export function ReplayList() {
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useReplayList()

  if (isPending) return <p className="text-neutral-400">불러오는 중…</p>
  if (error) return <p className="text-red-400">{error.message}</p>

  const items = data.pages.flatMap((page) => page.items)
  if (items.length === 0) return <p className="text-neutral-400">다시보기가 없습니다.</p>

  return (
    <div className="space-y-4">
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((r) => (
          <li key={r.broadcastId}>
            <Link
              to={`/broadcasts/${r.broadcastId}`}
              className="block space-y-2 rounded-lg border border-neutral-800 bg-neutral-900 p-4 hover:border-neutral-600"
            >
              <div className="flex items-center gap-2">
                {r.durationSeconds !== null && (
                  <span className="rounded bg-neutral-800 px-1.5 py-0.5 text-xs font-semibold text-neutral-200 tabular-nums">
                    {formatDuration(r.durationSeconds)}
                  </span>
                )}
                <span className="truncate text-sm text-neutral-400">{r.streamerNickname}</span>
              </div>
              <p className="truncate font-semibold">{r.title}</p>
              {r.startedAt && <p className="text-xs text-neutral-500">{formatDateTime(r.startedAt)}</p>}
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
