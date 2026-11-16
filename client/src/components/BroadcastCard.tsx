import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { apiUrl } from '../api/client'

/**
 * 방송·다시보기 목록의 카드. 16:9 자리에 썸네일(송출 중 화면)을 그리고, 없거나 불러오지 못하면 스트리머 닉네임 첫 글자를 그린다.
 * `badges`는 썸네일 왼쪽 위, `corner`는 오른쪽 아래에 얹는다.
 */
export function BroadcastCard({
  to,
  title,
  streamerNickname,
  meta,
  thumbnailUrl,
  badges,
  corner,
}: {
  to: string
  title: string
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
  return (
    <Link to={to} className="group block space-y-3">
      <div className="relative flex aspect-video items-center justify-center overflow-hidden rounded-lg bg-linear-to-br from-brand-900 to-neutral-900 ring-brand-400 transition group-hover:ring-2">
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
      </div>
      <div className="flex gap-3">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-neutral-800 text-sm font-bold text-brand-300">
          {initial}
        </div>
        <div className="min-w-0 space-y-0.5">
          <p className="line-clamp-2 font-semibold leading-snug group-hover:text-brand-200">{title}</p>
          <p className="truncate text-sm text-neutral-400">{streamerNickname}</p>
          {meta && <p className="text-xs text-neutral-500">{meta}</p>}
        </div>
      </div>
    </Link>
  )
}

/** 썸네일 위에 얹는 작은 표시(시청자 수, 길이 등). */
export function ThumbnailTag({ children }: { children: ReactNode }) {
  return <span className="rounded bg-black/70 px-1.5 py-0.5 text-xs font-semibold text-white tabular-nums">{children}</span>
}
