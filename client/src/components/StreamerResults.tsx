import { Link } from 'react-router'
import { useStreamerSearch } from '../broadcast/broadcast'
import { StatusBadge } from './StatusBadge'

/** 닉네임에 검색어가 든 스트리머. 방송 중인 스트리머가 먼저 오고 LIVE 표시가 붙는다. 누르면 스트리머 화면으로 간다. */
export function StreamerResults({ q }: { q: string }) {
  const { data, error, isPending } = useStreamerSearch(q)

  if (isPending) return <p className="text-neutral-400">불러오는 중…</p>
  if (error) return <p className="text-red-400">{error.message}</p>
  if (data.length === 0) return <p className="text-neutral-400">찾은 스트리머가 없습니다.</p>

  return (
    <ul className="flex flex-wrap gap-3">
      {data.map((s) => (
        <li key={s.streamerId}>
          <Link
            to={`/streamers/${s.streamerId}`}
            className="flex items-center gap-3 rounded-lg border border-neutral-800 py-2 pr-4 pl-2 hover:border-brand-400"
          >
            <span className="flex size-9 items-center justify-center rounded-full bg-neutral-800 text-sm font-bold text-brand-300">
              {s.nickname.slice(0, 1)}
            </span>
            <span className="font-semibold">{s.nickname}</span>
            {s.liveBroadcastId !== null && <StatusBadge status="LIVE" />}
          </Link>
        </li>
      ))}
    </ul>
  )
}
