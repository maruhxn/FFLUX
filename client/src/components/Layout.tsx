import { useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import { useLogout, useMe } from '../auth/auth'
import { usePublishSession } from '../broadcast/publishSession'
import { BroadcastStartAlerts } from './BroadcastStartAlerts'
import { FollowingList } from './FollowingList'

export function Layout() {
  const { data: me } = useMe()
  const logout = useLogout()
  const publishing = usePublishSession()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  // 방송 상세는 플레이어와 채팅으로 화면 폭을 다 쓰고, 넓은 화면에서는 창 높이에 맞춘다(채팅 칸이 창 높이만큼 선다).
  // 그래서 왼쪽 메뉴를 늘 펼쳐 두지 않고 메뉴 버튼으로만 연다.
  const wide = /^\/broadcasts\/\d+$/.test(location.pathname)

  const menuItems = [
    { label: '지금 방송 중', to: '/', active: location.pathname === '/' && !location.search.includes('tab=replays') },
    { label: '다시보기', to: '/?tab=replays', active: location.pathname === '/' && location.search.includes('tab=replays') },
    { label: '지난 방송', to: '/broadcasts/ended', active: location.pathname === '/broadcasts/ended' },
    ...(me ? [{ label: '내 방송', to: '/me/broadcasts', active: location.pathname === '/me/broadcasts' }] : []),
  ]

  const menu = (
    <>
      <nav className="flex flex-col gap-1 p-3 text-sm">
        {menuItems.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            onClick={() => setMenuOpen(false)}
            aria-current={item.active ? 'page' : undefined}
            className={`rounded-md px-3 py-2 font-medium ${
              item.active ? 'bg-neutral-800 text-white' : 'text-neutral-400 hover:bg-neutral-900 hover:text-neutral-100'
            }`}
          >
            {item.label}
          </Link>
        ))}
      </nav>
      {me && <FollowingList onNavigate={() => setMenuOpen(false)} />}
    </>
  )

  return (
    <div className={`flex min-h-dvh flex-col bg-neutral-950 text-neutral-100 ${wide ? 'lg:h-dvh' : ''}`}>
      <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center justify-between gap-3 border-b border-neutral-800 bg-neutral-950 px-3 sm:px-4">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setMenuOpen(!menuOpen)}
            aria-label="메뉴"
            aria-expanded={menuOpen}
            className={`rounded-md p-2 text-neutral-300 hover:bg-neutral-800 ${wide ? '' : 'lg:hidden'}`}
          >
            <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <Link to="/" onClick={() => setMenuOpen(false)} className="text-lg font-extrabold tracking-tight text-brand-400">
            FFLUX
          </Link>
        </div>
        {me ? (
          <div className="flex items-center gap-2 text-sm whitespace-nowrap sm:gap-3">
            {publishing && (
              <Link
                to={`/broadcasts/${publishing.broadcastId}/publish`}
                className="rounded-md border border-red-700 px-2.5 py-1.5 font-semibold text-red-400 hover:bg-red-950 sm:px-3"
              >
                ● 송출 중
              </Link>
            )}
            <Link to="/broadcasts/new" className="rounded-md bg-brand-500 px-2.5 py-1.5 font-semibold text-white hover:bg-brand-600 sm:px-3">
              방송 등록
            </Link>
            <span className="hidden text-neutral-300 sm:inline">{me.nickname}</span>
            <button
              type="button"
              onClick={() => logout.mutate()}
              className="rounded-md border border-neutral-700 px-2.5 py-1.5 hover:bg-neutral-800 sm:px-3"
            >
              로그아웃
            </button>
          </div>
        ) : (
          <Link to="/login" className="rounded-md bg-brand-500 px-3 py-1.5 text-sm font-semibold text-white hover:bg-brand-600">
            로그인
          </Link>
        )}
      </header>
      <div className={`flex flex-1 ${wide ? 'lg:min-h-0' : ''}`}>
        {!wide && <aside className="sticky top-14 hidden h-[calc(100dvh-3.5rem)] w-56 shrink-0 overflow-y-auto border-r border-neutral-800 lg:block">{menu}</aside>}
        {menuOpen && (
          <div className={`fixed inset-0 top-14 z-20 ${wide ? '' : 'lg:hidden'}`}>
            <button type="button" aria-label="메뉴 닫기" onClick={() => setMenuOpen(false)} className="absolute inset-0 bg-black/60" />
            <aside className="relative h-full w-56 overflow-y-auto border-r border-neutral-800 bg-neutral-950">{menu}</aside>
          </div>
        )}
        <main className={wide ? 'min-w-0 flex-1 lg:min-h-0' : 'mx-auto w-full max-w-7xl min-w-0 flex-1 px-4 py-6 sm:px-6'}>
          <Outlet />
        </main>
      </div>
      {me && <BroadcastStartAlerts key={me.memberId} />}
    </div>
  )
}
