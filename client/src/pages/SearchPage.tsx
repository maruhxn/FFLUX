import { useSearchParams } from 'react-router'
import { BroadcastList } from '../components/BroadcastList'
import { SearchBox } from '../components/SearchBox'
import { StreamerResults } from '../components/StreamerResults'

/** 검색 결과. 닉네임이 맞는 스트리머, 제목이나 닉네임이 맞는 방송 중·지난 방송을 차례로 보여 준다. 검색어는 주소(`?q=`)에 둔다. */
export function SearchPage() {
  const [params] = useSearchParams()
  const q = (params.get('q') ?? '').trim()

  return (
    <div className="space-y-8">
      {/* 좁은 화면은 상단바에 검색창이 없어 여기에 둔다. */}
      <SearchBox className="sm:hidden" />
      {q === '' ? (
        <p className="text-neutral-400">방송 제목이나 스트리머 닉네임으로 검색하세요.</p>
      ) : (
        <>
          <h1 className="text-2xl font-bold break-all">‘{q}’ 검색 결과</h1>
          <section className="space-y-4">
            <h2 className="text-lg font-bold">스트리머</h2>
            <StreamerResults q={q} />
          </section>
          <section className="space-y-4">
            <h2 className="text-lg font-bold">방송 중</h2>
            <BroadcastList kind="LIVE" filter={{ q }} empty="찾은 방송 중인 방송이 없습니다." />
          </section>
          <section className="space-y-4">
            <h2 className="text-lg font-bold">지난 방송</h2>
            <BroadcastList kind="ENDED" filter={{ q }} empty="찾은 지난 방송이 없습니다." />
          </section>
        </>
      )}
    </div>
  )
}
