import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { openEvents } from '../api/client'
import { refetchMe } from '../auth/auth'
import { BROADCASTS_KEY } from '../broadcast/broadcast'
import { FOLLOWINGS_KEY, showSystemNotification, type BroadcastStartedAlert } from '../follow/follows'

/** 알림 하나가 떠 있는 시간. */
const SHOW_MS = 10_000

/**
 * 로그인한 회원의 SSE를 구독해, 팔로우한 스트리머가 방송을 시작하면 화면 아래 구석에 알림을 띄운다.
 * 탭이 가려져 있으면 브라우저 알림(권한이 있을 때)으로 띄운다. 사이트를 열어 두었을 때만 받는다.
 */
export function BroadcastStartAlerts() {
  const [alerts, setAlerts] = useState<BroadcastStartedAlert[]>([])
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  useEffect(
    () =>
      openEvents('/api/me/events', (source) => {
        // 서버가 연결을 거절했으면(세션이 사라져 401 등) 로그인 상태를 다시 읽는다. 로그아웃이면 이 구독이 닫혀 다시 열지 않는다.
        source.addEventListener('error', () => {
          if (source.readyState === EventSource.CLOSED) refetchMe(queryClient)
        })
        source.addEventListener('broadcast-started', (e) => {
          const alert = JSON.parse(e.data) as BroadcastStartedAlert
          // 방송 목록과 팔로잉 목록에 새로 LIVE가 된 방송이 보이게 한다.
          void queryClient.invalidateQueries({ queryKey: BROADCASTS_KEY })
          void queryClient.invalidateQueries({ queryKey: FOLLOWINGS_KEY })
          if (showSystemNotification(alert, () => navigate(`/broadcasts/${alert.broadcastId}`))) return
          setAlerts((old) => [...old.filter((it) => it.broadcastId !== alert.broadcastId), alert])
          setTimeout(() => setAlerts((old) => old.filter((it) => it !== alert)), SHOW_MS)
        })
      }),
    [navigate, queryClient],
  )

  const dismiss = (alert: BroadcastStartedAlert) => setAlerts((old) => old.filter((it) => it !== alert))

  if (alerts.length === 0) return null
  return (
    <div className="fixed inset-x-3 bottom-3 z-40 flex flex-col items-end gap-2 sm:inset-x-auto sm:right-4 sm:bottom-4 sm:w-96">
      {alerts.map((alert) => (
        <div
          key={alert.broadcastId}
          role="status"
          className="flex w-full items-start gap-3 rounded-lg border border-neutral-700 bg-neutral-900 p-3 shadow-lg"
        >
          <Link to={`/broadcasts/${alert.broadcastId}`} onClick={() => dismiss(alert)} className="min-w-0 flex-1">
            <p className="text-sm font-semibold">
              <span className="mr-1.5 rounded bg-live px-1 text-[10px] leading-4 font-bold text-white">LIVE</span>
              {alert.streamerNickname}님이 방송을 시작했습니다
            </p>
            <p className="mt-1 truncate text-sm text-neutral-400">{alert.title}</p>
          </Link>
          <button
            type="button"
            onClick={() => dismiss(alert)}
            aria-label="알림 닫기"
            className="rounded-md p-1 text-neutral-500 hover:bg-neutral-800 hover:text-neutral-200"
          >
            <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
      ))}
    </div>
  )
}
