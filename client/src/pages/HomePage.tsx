import { Link, useSearchParams } from 'react-router'
import { BroadcastList } from '../components/BroadcastList'
import { ReplayList } from '../components/ReplayList'

const TABS = [
  { key: 'live', label: '지금 방송 중', to: '/' },
  { key: 'replays', label: '다시보기', to: '/?tab=replays' },
] as const

/** 메인. 지금 LIVE인 방송 목록과 다시보기 목록을 탭으로 나눈다. 탭은 주소(`?tab=replays`)에 둔다. */
export function HomePage() {
  const [params] = useSearchParams()
  const tab = params.get('tab') === 'replays' ? 'replays' : 'live'

  return (
    <div className="space-y-6">
      <nav className="flex gap-4 border-b border-neutral-800">
        {TABS.map((t) => (
          <Link
            key={t.key}
            to={t.to}
            aria-current={t.key === tab ? 'page' : undefined}
            className={`-mb-px border-b-2 pb-2 text-lg font-bold ${
              t.key === tab ? 'border-emerald-400 text-white' : 'border-transparent text-neutral-500 hover:text-neutral-300'
            }`}
          >
            {t.label}
          </Link>
        ))}
      </nav>
      {tab === 'live' ? <BroadcastList kind="LIVE" empty="지금 방송 중인 방송이 없습니다." /> : <ReplayList />}
    </div>
  )
}
