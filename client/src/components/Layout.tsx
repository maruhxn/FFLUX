import { Link, Outlet } from 'react-router'
import { useLogout, useMe } from '../auth/auth'

export function Layout() {
  const { data: me } = useMe()
  const logout = useLogout()

  return (
    <div className="min-h-dvh bg-neutral-950 text-neutral-100">
      <header className="border-b border-neutral-800">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <nav className="flex items-center gap-4 text-sm">
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
            <div className="flex items-center gap-3 text-sm">
              <Link to="/broadcasts/new" className="rounded-md bg-emerald-500 px-3 py-1.5 font-semibold text-neutral-950">
                방송 등록
              </Link>
              <span className="text-neutral-300">{me.nickname}</span>
              <button
                type="button"
                onClick={() => logout.mutate()}
                className="rounded-md border border-neutral-700 px-3 py-1.5 hover:bg-neutral-800"
              >
                로그아웃
              </button>
            </div>
          ) : (
            <Link to="/login" className="rounded-md bg-emerald-500 px-3 py-1.5 text-sm font-semibold text-neutral-950">
              로그인
            </Link>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
