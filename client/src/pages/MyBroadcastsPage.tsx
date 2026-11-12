import { Link, Navigate } from 'react-router'
import { useMe } from '../auth/auth'
import { BroadcastList } from '../components/BroadcastList'
import { ChatRestrictionList } from '../components/ChatRestrictionList'

export function MyBroadcastsPage() {
  const { data: me, isPending } = useMe()
  if (isPending) return null
  if (!me) return <Navigate to="/login" replace />

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">내 방송</h1>
        <Link to="/broadcasts/new" className="rounded-md bg-emerald-500 px-3 py-1.5 text-sm font-semibold text-neutral-950">
          방송 등록
        </Link>
      </div>
      <BroadcastList kind="mine" empty="등록한 방송이 없습니다." />
      <ChatRestrictionList />
    </div>
  )
}
