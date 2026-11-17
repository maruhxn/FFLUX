import { Link } from 'react-router'
import { useFollowings, type Following } from '../follow/follows'

/** 왼쪽 메뉴 아래의 팔로잉 목록. 방송 중인 스트리머는 누르면 그 방송으로 가고, 방송 중이 아니면 흐리게만 보인다. */
export function FollowingList({ onNavigate }: { onNavigate: () => void }) {
  const { data: followings } = useFollowings()
  if (!followings) return null

  return (
    <section className="border-t border-neutral-800 p-3 text-sm">
      <h2 className="px-3 pb-2 text-xs font-semibold text-neutral-500">팔로잉</h2>
      {followings.length === 0 ? (
        <p className="px-3 text-xs text-neutral-500">팔로우한 스트리머가 없습니다</p>
      ) : (
        <ul className="flex flex-col gap-0.5">
          {followings.map((following) => (
            <li key={following.streamerId}>
              {following.liveBroadcastId === null ? (
                <div className="flex items-center gap-2.5 rounded-md px-3 py-1.5 text-neutral-500">
                  <Avatar following={following} />
                  <span className="truncate">{following.nickname}</span>
                </div>
              ) : (
                <Link
                  to={`/broadcasts/${following.liveBroadcastId}`}
                  onClick={onNavigate}
                  className="flex items-center gap-2.5 rounded-md px-3 py-1.5 text-neutral-200 hover:bg-neutral-900"
                >
                  <Avatar following={following} />
                  <span className="min-w-0 flex-1 truncate">{following.nickname}</span>
                  <span className="flex items-center gap-1 text-xs font-semibold text-live">
                    <span className="size-1.5 rounded-full bg-live" />
                    LIVE
                  </span>
                </Link>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Avatar({ following }: { following: Following }) {
  const live = following.liveBroadcastId !== null
  return (
    <span
      className={`flex size-7 shrink-0 items-center justify-center rounded-full bg-neutral-800 text-xs font-bold ${
        live ? 'text-brand-300 ring-2 ring-live' : 'text-neutral-500'
      }`}
    >
      {following.nickname.slice(0, 1)}
    </span>
  )
}
