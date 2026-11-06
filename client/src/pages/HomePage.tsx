import { useMe } from '../auth/auth'

/** 메인. LIVE 방송 목록은 #5에서 붙인다. */
export function HomePage() {
  const { data: me } = useMe()
  return (
    <div className="space-y-2">
      <h1 className="text-2xl font-bold">지금 방송 중</h1>
      <p className="text-neutral-400">{me ? `${me.nickname}님, 환영합니다.` : '로그인하지 않아도 방송을 볼 수 있습니다.'}</p>
    </div>
  )
}
