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
import { useDonationAlerts } from '../donation/donations'
import { Chat } from '../components/Chat'
import { Comments } from '../components/Comments'
import { Donation } from '../components/Donation'
import { DonationHistory } from '../components/DonationHistory'
import { DonationToast } from '../components/DonationToast'
import { EndBroadcastButton } from '../components/EndBroadcastButton'
import { LivePlayer } from '../components/LivePlayer'
import { StatusBadge } from '../components/StatusBadge'
import { StreamingTime } from '../components/StreamingTime'

/**
 * 넓은 화면에서 플레이어 높이는 창 높이에서 헤더와 방송 정보 줄 몫(15rem)을 뺀 값을 넘지 않는다.
 * 폭이 아주 넓어도 제목과 스트리머 줄(방송 시작·종료 버튼)이 첫 화면에 보이고, 남는 좌우는 가운데 맞춘 검은 여백이 된다.
 */
const PLAYER_MAX_HEIGHT = 'lg:mx-auto lg:max-h-[calc(100dvh-15rem)]'

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
  const donationAlerts = useDonationAlerts()
  // ENDED는 더 바뀌지 않으므로 구독하지 않는다.
  useBroadcastEvents(id, !!broadcast && broadcast.status !== 'ENDED', donationAlerts.push)
  // 스트리머 화면은 끝난 뒤 녹화 처리를 다시 읽어 따라간다. 이 화면에서 LIVE를 봤는지는 방송 ID와 함께 기억한다.
  const [sawLiveId, setSawLiveId] = useState<number | null>(null)
  if (broadcast?.status === 'LIVE' && sawLiveId !== id) setSawLiveId(id)
  const streamerSawLive = sawLiveId === id && !!broadcast && me?.memberId === broadcast.streamerId
  useRecordingWait(id, !!broadcast && waitsForRecording(broadcast, streamerSawLive))
  const remove = useDeleteBroadcast(id)
  const queryClient = useQueryClient()

  if (error) return <p className="text-red-400">{error.message}</p>
  if (!broadcast) return null

  const isStreamer = me?.memberId === broadcast.streamerId

  function onDelete() {
    if (!window.confirm('방송을 삭제할까요? 삭제한 방송은 되돌릴 수 없습니다.')) return
    remove.mutate(undefined, { onSuccess: () => navigate('/me/broadcasts') })
  }

  const live = broadcast.status === 'LIVE'

  return (
    <div className="lg:flex lg:h-full">
      <div className="min-w-0 flex-1 lg:overflow-y-auto">
        {/* LIVE 후원 알림은 플레이어 위에 토스트로 띄운다. */}
        <div className="relative bg-black">
          {broadcast.whepUrl && broadcast.hlsUrl ? (
            <LivePlayer
              whepUrl={broadcast.whepUrl}
              hlsUrl={broadcast.hlsUrl}
              // SSE가 막힌 네트워크에서도 방송이 끝나 끊긴 것이면 종료 화면으로 바뀌게 상세를 다시 읽는다.
              onDisconnect={() => void queryClient.invalidateQueries({ queryKey: broadcastDetailKey(id) })}
              className={PLAYER_MAX_HEIGHT}
            />
          ) : broadcast.status === 'ENDED' && broadcast.recording?.url ? (
            <video
              src={apiUrl(broadcast.recording.url)}
              controls
              playsInline
              className={`aspect-video w-full bg-black object-contain ${PLAYER_MAX_HEIGHT}`}
            />
          ) : (
            <div className={`flex aspect-video items-center justify-center bg-neutral-900 text-neutral-500 ${PLAYER_MAX_HEIGHT}`}>
              {placeholderOf(broadcast)}
            </div>
          )}
          <DonationToast alert={donationAlerts.current} />
        </div>
        <div className="space-y-6 px-4 py-5 lg:px-8">
          <div className="space-y-2">
            <h1 className="text-xl font-bold break-words sm:text-2xl">{broadcast.title}</h1>
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-neutral-400">
              <StatusBadge status={broadcast.status} />
              {live && broadcast.viewerCount !== null && (
                <span className="text-neutral-200">{formatViewerCount(broadcast.viewerCount)} 시청 중</span>
              )}
              {live && broadcast.startedAt && (
                <span className="text-neutral-200 tabular-nums">
                  · <StreamingTime startedAt={broadcast.startedAt} />
                </span>
              )}
              <span className="text-xs text-neutral-500">{formatDateTime(broadcast.createdAt)}</span>
            </div>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <StreamerAvatar nickname={broadcast.streamerNickname} live={live} />
              <span className="text-lg font-semibold">{broadcast.streamerNickname}</span>
            </div>
            {isStreamer && (
              <div className="flex flex-wrap gap-2">
                {broadcast.status === 'READY' && (
                  <Link
                    to={`/broadcasts/${broadcast.id}/publish`}
                    className="rounded-full bg-emerald-500 px-4 py-1.5 text-sm font-semibold text-neutral-950"
                  >
                    방송 시작
                  </Link>
                )}
                {live && <EndBroadcastButton broadcastId={broadcast.id} />}
                {broadcast.status !== 'ENDED' && (
                  <Link
                    to={`/broadcasts/${broadcast.id}/edit`}
                    className="rounded-full border border-neutral-700 px-4 py-1.5 text-sm hover:bg-neutral-800"
                  >
                    수정
                  </Link>
                )}
                {!live && (
                  <button
                    type="button"
                    onClick={onDelete}
                    disabled={remove.isPending}
                    className="rounded-full border border-red-800 px-4 py-1.5 text-sm text-red-400 hover:bg-red-950 disabled:opacity-50"
                  >
                    삭제
                  </button>
                )}
              </div>
            )}
          </div>
          {remove.error && <p className="text-sm text-red-400">{remove.error.message}</p>}
          {broadcast.description && (
            <p className="rounded-lg bg-neutral-900 px-4 py-3 whitespace-pre-wrap text-neutral-300">{broadcast.description}</p>
          )}
          {/* 후원은 LIVE·ENDED 방송의 스트리머에게 한다. */}
          {broadcast.status !== 'READY' && (
            <>
              <div>
                <Donation broadcastId={broadcast.id} streamerId={broadcast.streamerId} streamerNickname={broadcast.streamerNickname} />
              </div>
              <DonationHistory broadcastId={broadcast.id} />
            </>
          )}
          {/* 댓글은 다시보기(끝난 방송의 재생할 수 있는 녹화)에만 단다. */}
          {broadcast.status === 'ENDED' && broadcast.recording?.status === 'READY' && <Comments broadcastId={broadcast.id} />}
        </div>
      </div>
      {/* 채팅은 LIVE 방송에만 있다(다시보기에는 보여 주지 않는다). 넓은 화면은 오른쪽 칸, 좁은 화면은 플레이어 정보 아래. */}
      {live && (
        <aside className="border-t border-neutral-800 lg:w-[22rem] lg:shrink-0 lg:border-t-0 lg:border-l">
          <Chat broadcastId={broadcast.id} streamerId={broadcast.streamerId} />
        </aside>
      )}
    </div>
  )
}

/** 스트리머 프로필 사진 자리. 사진 기능이 없어 닉네임 첫 글자를 그리고, 방송 중이면 빨간 테두리와 LIVE 표시를 단다. */
function StreamerAvatar({ nickname, live }: { nickname: string; live: boolean }) {
  return (
    <div className="relative">
      <div
        className={`flex h-12 w-12 items-center justify-center rounded-full bg-neutral-800 text-lg font-bold text-emerald-300 ${
          live ? 'ring-2 ring-red-600 ring-offset-2 ring-offset-neutral-950' : ''
        }`}
      >
        {nickname.slice(0, 1)}
      </div>
      {live && (
        <span className="absolute -bottom-1.5 left-1/2 -translate-x-1/2 rounded bg-red-600 px-1 text-[10px] leading-4 font-bold text-white">
          LIVE
        </span>
      )}
    </div>
  )
}
