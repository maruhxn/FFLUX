import { formatDateTime, formatDuration, useReplayList, versionedThumbnailUrl } from '../broadcast/broadcast'
import { BroadcastCard, ThumbnailTag } from './BroadcastCard'

/** 최신순 다시보기 목록. 다음 페이지는 "더 보기"로 읽는다. */
export function ReplayList() {
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage, dataUpdatedAt } = useReplayList()

  if (isPending) return <p className="text-neutral-400">불러오는 중…</p>
  if (error) return <p className="text-red-400">{error.message}</p>

  const items = data.pages.flatMap((page) => page.items)
  if (items.length === 0) return <p className="text-neutral-400">다시보기가 없습니다.</p>

  return (
    <div className="space-y-4">
      <ul className="grid gap-x-4 gap-y-8 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
        {items.map((r) => (
          <li key={r.broadcastId}>
            <BroadcastCard
              to={`/broadcasts/${r.broadcastId}`}
              title={r.title}
              streamerId={r.streamerId}
              streamerNickname={r.streamerNickname}
              meta={r.startedAt ? formatDateTime(r.startedAt) : undefined}
              thumbnailUrl={versionedThumbnailUrl(r.thumbnailUrl, dataUpdatedAt)}
              corner={r.durationSeconds !== null && <ThumbnailTag>{formatDuration(r.durationSeconds)}</ThumbnailTag>}
            />
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
