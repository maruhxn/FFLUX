import { Link, Outlet, useLocation } from 'react-router'
import { useLogout, useMe } from '../auth/auth'
import { usePublishSession } from '../broadcast/publishSession'

export function Layout() {
  const { data: me } = useMe()
  const logout = useLogout()
  const publishing = usePublishSession()
  // 방송 상세는 플레이어와 채팅으로 화면 폭을 다 쓰고, 넓은 화면에서는 창 높이에 맞춘다(채팅 칸이 창 높이만큼 선다).
  const wide = /^\/broadcasts\/\d+$/.test(useLocation().pathname)

  return (
    <div className={`flex min-h-dvh flex-col bg-neutral-950 text-neutral-100 ${wide ? 'lg:h-dvh' : ''}`}>
      <header className="shrink-0 border-b border-neutral-800">
        <div className={`mx-auto flex ${wide ? '' : 'max-w-5xl'} flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3`}>
          <nav className="flex items-center gap-3 text-sm whitespace-nowrap sm:gap-4">
            <Link to="/" className="text-lg font-bold tracking-tight text-emerald-400">
              FFLUX
            </Link>
            <Link to="/broadcasts/ended" className="text-neutral-300 hover:text-white">
              지난 방송
            </Link>
            {me && (
              <Link to="/me/broadcasts" className="text-neutral-300 hover:text-white">
                내 방송
              </Link>
            )}
          </nav>
          {me ? (
            <div className="flex items-center gap-2 text-sm whitespace-nowrap sm:gap-3">
              {publishing && (
                <Link
                  to={`/broadcasts/${publishing.broadcastId}/publish`}
                  className="rounded-md border border-red-700 px-2.5 py-1.5 sm:px-3 font-semibold text-red-400 hover:bg-red-950"
                >
                  ● 송출 중
                </Link>
              )}
              <Link to="/broadcasts/new" className="rounded-md bg-emerald-500 px-2.5 py-1.5 sm:px-3 font-semibold text-neutral-950">
                방송 등록
              </Link>
              <span className="hidden text-neutral-300 sm:inline">{me.nickname}</span>
              <button
                type="button"
                onClick={() => logout.mutate()}
                className="rounded-md border border-neutral-700 px-2.5 py-1.5 sm:px-3 hover:bg-neutral-800"
              >
                로그아웃
              </button>
            </div>
          ) : (
            <Link to="/login" className="rounded-md bg-emerald-500 px-2.5 py-1.5 sm:px-3 text-sm font-semibold text-neutral-950">
              로그인
            </Link>
          )}
        </div>
      </header>
      <main className={wide ? 'flex-1 lg:min-h-0' : 'mx-auto w-full max-w-5xl px-4 py-8'}>
        <Outlet />
      </main>
    </div>
  )
}
