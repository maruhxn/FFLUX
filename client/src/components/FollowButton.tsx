import { Link } from 'react-router'
import { useMe } from '../auth/auth'
import { askNotificationPermission, useFollowStatus, useSetFollow } from '../follow/follows'

/**
 * 스트리머 이름 옆의 팔로워 수와 팔로우 버튼. 팔로우하면 그 스트리머가 방송을 시작할 때 알림을 받는다.
 * 비로그인이면 로그인으로 보내고, 자기 자신에게는 팔로워 수만 보여 준다.
 */
export function FollowButton({ streamerId }: { streamerId: number }) {
  const { data: me } = useMe()
  const { data: status } = useFollowStatus(streamerId)
  const setFollow = useSetFollow(streamerId)
  const following = status?.following ?? false

  const button = 'rounded-full px-4 py-1.5 text-sm font-semibold'
  return (
    <div className="flex items-center gap-3">
      {status && <span className="text-sm text-neutral-400">팔로워 {status.followerCount.toLocaleString()}명</span>}
      {!me ? (
        <Link to="/login" className={`${button} bg-brand-500 text-white hover:bg-brand-600`}>
          팔로우
        </Link>
      ) : me.memberId === streamerId ? null : (
        <button
          type="button"
          disabled={!status || setFollow.isPending}
          onClick={() => {
            if (!following) askNotificationPermission()
            setFollow.mutate(!following)
          }}
          className={`${button} disabled:opacity-50 ${
            following ? 'border border-neutral-700 text-neutral-200 hover:bg-neutral-800' : 'bg-brand-500 text-white hover:bg-brand-600'
          }`}
        >
          {following ? '팔로잉' : '팔로우'}
        </button>
      )}
      {setFollow.error && <span className="text-sm text-red-400">{setFollow.error.message}</span>}
    </div>
  )
}
