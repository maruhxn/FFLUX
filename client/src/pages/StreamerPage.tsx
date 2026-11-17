import { Link, useParams } from 'react-router'
import { useStreamer } from '../broadcast/broadcast'
import { BroadcastList } from '../components/BroadcastList'
import { StatusBadge } from '../components/StatusBadge'

/** 스트리머 화면. 닉네임과 방송 중 여부, 그 스트리머의 방송 중·지난 방송을 보여 준다. */
export function StreamerPage() {
  const id = Number(useParams().id)
  const { data: streamer, error, isPending } = useStreamer(id)

  if (isPending) return <p className="text-neutral-400">불러오는 중…</p>
  if (error) return <p className="text-red-400">{error.message}</p>

  const live = streamer.liveBroadcastId !== null
  return (
    <div className="space-y-8">
      <div className="flex items-center gap-4">
        <div className="flex size-16 shrink-0 items-center justify-center rounded-full bg-neutral-800 text-2xl font-bold text-brand-300">
          {streamer.nickname.slice(0, 1)}
        </div>
        <div className="min-w-0 space-y-1">
          <h1 className="truncate text-2xl font-bold">{streamer.nickname}</h1>
          {live && (
            <Link to={`/broadcasts/${streamer.liveBroadcastId}`} className="inline-flex items-center gap-2 text-sm text-brand-400">
              <StatusBadge status="LIVE" />
              방송 보러 가기
            </Link>
          )}
        </div>
      </div>
      {live && (
        <section className="space-y-4">
          <h2 className="text-lg font-bold">방송 중</h2>
          <BroadcastList kind="LIVE" filter={{ streamerId: id }} empty="지금 방송 중이 아닙니다." />
        </section>
      )}
      <section className="space-y-4">
        <h2 className="text-lg font-bold">지난 방송</h2>
        <BroadcastList kind="ENDED" filter={{ streamerId: id }} empty="지난 방송이 없습니다." />
      </section>
    </div>
  )
}
