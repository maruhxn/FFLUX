import { BroadcastList } from '../components/BroadcastList'

export function EndedBroadcastsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">지난 방송</h1>
      <BroadcastList kind="ENDED" empty="지난 방송이 없습니다." />
    </div>
  )
}
