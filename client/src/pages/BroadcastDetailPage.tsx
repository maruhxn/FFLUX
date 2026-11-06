import { Link, useNavigate, useParams } from 'react-router'
import { useMe } from '../auth/auth'
import { formatDateTime, useBroadcast, useDeleteBroadcast, type BroadcastStatus } from '../broadcast/broadcast'
import { EndBroadcastButton } from '../components/EndBroadcastButton'
import { LivePlayer } from '../components/LivePlayer'
import { StatusBadge } from '../components/StatusBadge'

// 방송이 시작되거나 끝나는지 이 간격으로 다시 읽는다(SSE는 #9). ENDED는 더 바뀌지 않는다.
const STATUS_CHECK_MS = 5000

const PLAYER_PLACEHOLDER: Record<BroadcastStatus, string> = {
  READY: '방송 대기 중',
  LIVE: '방송 중',
  ENDED: '종료된 방송',
}

/** 방송 상세. LIVE면 플레이어로 바로 본다. */
export function BroadcastDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const { data: me } = useMe()
  const { data: broadcast, error } = useBroadcast(id, (b) => (b?.status === 'ENDED' ? false : STATUS_CHECK_MS))
  const remove = useDeleteBroadcast(id)

  if (error) return <p className="text-red-400">{error.message}</p>
  if (!broadcast) return null

  const isStreamer = me?.memberId === broadcast.streamerId

  function onDelete() {
    if (!window.confirm('방송을 삭제할까요? 삭제한 방송은 되돌릴 수 없습니다.')) return
    remove.mutate(undefined, { onSuccess: () => navigate('/me/broadcasts') })
  }

  return (
    <div className="space-y-6">
      {broadcast.whepUrl && broadcast.hlsUrl ? (
        <LivePlayer whepUrl={broadcast.whepUrl} hlsUrl={broadcast.hlsUrl} />
      ) : (
        <div className="flex aspect-video items-center justify-center rounded-lg bg-neutral-900 text-neutral-500">
          {PLAYER_PLACEHOLDER[broadcast.status]}
        </div>
      )}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <StatusBadge status={broadcast.status} />
          <span className="text-sm text-neutral-400">{broadcast.streamerNickname}</span>
          <span className="text-xs text-neutral-500">{formatDateTime(broadcast.createdAt)}</span>
        </div>
        <h1 className="text-2xl font-bold break-words">{broadcast.title}</h1>
        {broadcast.description && <p className="whitespace-pre-wrap text-neutral-300">{broadcast.description}</p>}
      </div>
      {isStreamer && (
        <div className="flex gap-2">
          {broadcast.status === 'READY' && (
            <Link
              to={`/broadcasts/${broadcast.id}/publish`}
              className="rounded-md bg-emerald-500 px-3 py-1.5 text-sm font-semibold text-neutral-950"
            >
              방송 시작 (PC)
            </Link>
          )}
          {broadcast.status === 'LIVE' && <EndBroadcastButton broadcastId={broadcast.id} />}
          {broadcast.status !== 'ENDED' && (
            <Link
              to={`/broadcasts/${broadcast.id}/edit`}
              className="rounded-md border border-neutral-700 px-3 py-1.5 text-sm hover:bg-neutral-800"
            >
              수정
            </Link>
          )}
          {broadcast.status !== 'LIVE' && (
            <button
              type="button"
              onClick={onDelete}
              disabled={remove.isPending}
              className="rounded-md border border-red-800 px-3 py-1.5 text-sm text-red-400 hover:bg-red-950 disabled:opacity-50"
            >
              삭제
            </button>
          )}
        </div>
      )}
      {remove.error && <p className="text-sm text-red-400">{remove.error.message}</p>}
    </div>
  )
}
