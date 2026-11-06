import { BroadcastList } from '../components/BroadcastList'

/** 메인. 지금 LIVE인 방송 목록. */
export function HomePage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">지금 방송 중</h1>
      <BroadcastList kind="LIVE" empty="지금 방송 중인 방송이 없습니다." />
    </div>
  )
}
