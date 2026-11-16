import { formatDateTime, formatViewerCount, useBroadcastList, versionedThumbnailUrl, type BroadcastListKind } from '../broadcast/broadcast'
import { BroadcastCard, ThumbnailTag } from './BroadcastCard'
import { StatusBadge } from './StatusBadge'

/** 최신순 방송 목록. 다음 페이지는 "더 보기"로 읽는다. */
export function BroadcastList({ kind, empty }: { kind: BroadcastListKind; empty: string }) {
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage, dataUpdatedAt } = useBroadcastList(kind)

  if (isPending) return <p className="text-neutral-400">불러오는 중…</p>
  if (error) return <p className="text-red-400">{error.message}</p>

  const items = data.pages.flatMap((page) => page.items)
  if (items.length === 0) return <p className="text-neutral-400">{empty}</p>

  return (
    <div className="space-y-4">
      <ul className="grid gap-x-4 gap-y-8 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
        {items.map((b) => (
          <li key={b.id}>
            <BroadcastCard
              to={`/broadcasts/${b.id}`}
              title={b.title}
              streamerNickname={b.streamerNickname}
              meta={formatDateTime(b.createdAt)}
              thumbnailUrl={versionedThumbnailUrl(b.thumbnailUrl, dataUpdatedAt)}
              badges={
                <>
                  <StatusBadge status={b.status} />
                  {b.status === 'LIVE' && b.viewerCount !== null && <ThumbnailTag>{formatViewerCount(b.viewerCount)}</ThumbnailTag>}
                </>
              }
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
