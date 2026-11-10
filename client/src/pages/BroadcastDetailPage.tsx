import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { apiUrl } from '../api/client'
import { useMe } from '../auth/auth'
import {
  broadcastDetailKey,
  formatDateTime,
  formatViewerCount,
  useBroadcast,
  useDeleteBroadcast,
  useRecordingWait,
  waitsForRecording,
  type BroadcastDetail,
  type BroadcastStatus,
} from '../broadcast/broadcast'
import { useBroadcastEvents } from '../broadcast/events'
import { EndBroadcastButton } from '../components/EndBroadcastButton'
import { LivePlayer } from '../components/LivePlayer'
import { StatusBadge } from '../components/StatusBadge'
import { StreamingTime } from '../components/StreamingTime'

const PLAYER_PLACEHOLDER: Record<BroadcastStatus, string> = {
  READY: '방송 대기 중',
  LIVE: '방송 중',
  ENDED: '방송이 종료되었습니다',
}

/** 플레이어를 그릴 수 없을 때 그 자리의 안내. 처리 중·실패 녹화는 스트리머에게만 온다. */
function placeholderOf(broadcast: BroadcastDetail) {
  if (broadcast.recording?.status === 'PROCESSING') return '녹화 처리 중'
  if (broadcast.recording?.status === 'FAILED') return '녹화 처리 실패'
  return PLAYER_PLACEHOLDER[broadcast.status]
}

/**
 * 방송 상세. LIVE면 플레이어로 바로 보고, 방송이 시작·종료되거나 정보가 바뀌면 SSE로 바로 따라간다.
 * 끝난 방송에 재생할 수 있는 녹화가 있으면 그 자리에서 다시보기를 재생한다.
 */
export function BroadcastDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const { data: me } = useMe()
  const { data: broadcast, error } = useBroadcast(id)
  // ENDED는 더 바뀌지 않으므로 구독하지 않는다.
  useBroadcastEvents(id, !!broadcast && broadcast.status !== 'ENDED')
  // 끝난 뒤 녹화가 생기는 것은 다시 읽어 따라간다. 이 화면에서 LIVE를 봤는지는 방송 ID와 함께 기억한다.
  const [sawLiveId, setSawLiveId] = useState<number | null>(null)
  if (broadcast?.status === 'LIVE' && sawLiveId !== id) setSawLiveId(id)
  useRecordingWait(id, !!broadcast && waitsForRecording(broadcast, sawLiveId === id))
  const remove = useDeleteBroadcast(id)
  const queryClient = useQueryClient()

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
        <LivePlayer
          whepUrl={broadcast.whepUrl}
          hlsUrl={broadcast.hlsUrl}
          // SSE가 막힌 네트워크에서도 방송이 끝나 끊긴 것이면 종료 화면으로 바뀌게 상세를 다시 읽는다.
          onDisconnect={() => void queryClient.invalidateQueries({ queryKey: broadcastDetailKey(id) })}
        />
      ) : broadcast.status === 'ENDED' && broadcast.recording?.url ? (
        <video
          src={apiUrl(broadcast.recording.url)}
          controls
          playsInline
          className="aspect-video w-full rounded-lg bg-black"
        />
      ) : (
        <div className="flex aspect-video items-center justify-center rounded-lg bg-neutral-900 text-neutral-500">
          {placeholderOf(broadcast)}
        </div>
      )}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <StatusBadge status={broadcast.status} />
          <span className="text-sm text-neutral-400">{broadcast.streamerNickname}</span>
          {broadcast.status === 'LIVE' && broadcast.viewerCount !== null && (
            <span className="text-sm text-neutral-300">{formatViewerCount(broadcast.viewerCount)} 시청 중</span>
          )}
          {broadcast.status === 'LIVE' && broadcast.startedAt && (
            <span className="text-sm text-neutral-300 tabular-nums">
              <StreamingTime startedAt={broadcast.startedAt} />
            </span>
          )}
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
              방송 시작
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
