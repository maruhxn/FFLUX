import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { apiUrl } from '../api/client'

/**
 * 방송·다시보기 목록의 카드. 16:9 자리에 썸네일(송출 중 화면)을 그리고, 없거나 불러오지 못하면 스트리머 닉네임 첫 글자를 그린다.
 * `badges`는 썸네일 왼쪽 위, `corner`는 오른쪽 아래에 얹는다.
 * 썸네일과 제목은 방송([to])으로, 아바타와 닉네임은 [streamerId]가 있으면 스트리머 화면으로 간다. 링크 안에 링크를 둘 수 없어 따로 건다.
 */
export function BroadcastCard({
  to,
  title,
  streamerId,
  streamerNickname,
  meta,
  thumbnailUrl,
  badges,
  corner,
}: {
  to: string
  title: string
  /** 공개 방송(LIVE·ENDED)일 때만 넘긴다. 대기 중 방송만 있는 스트리머의 화면은 404다. */
  streamerId?: number
  streamerNickname: string
  meta?: string
  thumbnailUrl?: string | null
  badges?: ReactNode
  corner?: ReactNode
}) {
  const initial = streamerNickname.slice(0, 1)
  // 썸네일은 아직 찍히지 않았을 수 있다. 불러오지 못한 주소는 기억해 첫 글자 자리로 돌아간다.
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const showThumbnail = thumbnailUrl && thumbnailUrl !== failedUrl
  const streamerPath = streamerId === undefined ? undefined : `/streamers/${streamerId}`
  return (
    <div className="space-y-3">
      <Link
        to={to}
        aria-label={title}
        className="relative flex aspect-video items-center justify-center overflow-hidden rounded-lg bg-linear-to-br from-brand-900 to-neutral-900 ring-brand-400 transition hover:ring-2"
      >
        {showThumbnail ? (
          <img
            src={apiUrl(thumbnailUrl)}
            alt=""
            loading="lazy"
            onError={() => setFailedUrl(thumbnailUrl)}
            className="absolute inset-0 size-full object-cover"
          />
        ) : (
          <span className="text-5xl font-extrabold text-brand-300/40">{initial}</span>
        )}
        {badges && <div className="absolute top-2 left-2 flex items-center gap-1.5">{badges}</div>}
        {corner && <div className="absolute right-2 bottom-2">{corner}</div>}
      </Link>
      <div className="flex gap-3">
        <MaybeLink
          to={streamerPath}
          label={streamerNickname}
          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-neutral-800 text-sm font-bold text-brand-300"
        >
          {initial}
        </MaybeLink>
        <div className="min-w-0 space-y-0.5">
          <Link to={to} className="line-clamp-2 font-semibold leading-snug hover:text-brand-200">
            {title}
          </Link>
          <MaybeLink to={streamerPath} className="block truncate text-sm text-neutral-400 hover:text-neutral-200">
            {streamerNickname}
          </MaybeLink>
          {meta && <p className="text-xs text-neutral-500">{meta}</p>}
        </div>
      </div>
    </div>
  )
}

/** [to]가 있으면 링크, 없으면 같은 모양의 글자다. */
function MaybeLink({ to, label, className, children }: { to?: string; label?: string; className: string; children: ReactNode }) {
  return to ? (
    <Link to={to} aria-label={label} className={className}>
      {children}
    </Link>
  ) : (
    <div className={className}>{children}</div>
  )
}

/** 썸네일 위에 얹는 작은 표시(시청자 수, 길이 등). */
export function ThumbnailTag({ children }: { children: ReactNode }) {
  return <span className="rounded bg-black/70 px-1.5 py-0.5 text-xs font-semibold text-white tabular-nums">{children}</span>
}
