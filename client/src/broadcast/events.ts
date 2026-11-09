import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { apiUrl } from '../api/client'
import { broadcastDetailKey, type BroadcastDetail, type BroadcastStatus } from './broadcast'

/** 방송별 SSE를 연다. API 서버의 출처가 달라도 세션 쿠키를 실어 로그인한 시청자로 세게 한다. */
export const openBroadcastEvents = (id: number) =>
  new EventSource(apiUrl(`/api/broadcasts/${id}/events`), { withCredentials: true })

/**
 * SSE 이벤트 이름. `broadcast-status`는 {@link BroadcastStatusMessage}, `broadcast-updated`는 `{title, description}`,
 * `viewer-count`는 `{count}`를 싣는다. 시청자 수는 방송 상태와 관계없이 오므로 화면이 LIVE일 때만 보여 준다.
 */
export const BROADCAST_STATUS_EVENT = 'broadcast-status'
export const BROADCAST_UPDATED_EVENT = 'broadcast-updated'
const VIEWER_COUNT_EVENT = 'viewer-count'

export type BroadcastStatusMessage = { status: BroadcastStatus }

export function parseStatusMessage(e: MessageEvent<string>): BroadcastStatusMessage {
  return JSON.parse(e.data) as BroadcastStatusMessage
}

/**
 * [enabled]인 동안 방송별 SSE를 구독해, 방송이 바뀌면 상세를 다시 읽는다(LIVE가 되면 시청 주소도 상세에서 받는다).
 * 시청자 수는 다시 읽지 않고 상세에 바로 넣는다.
 * 연결이 열릴 때마다(재연결 포함) 다시 읽어 끊긴 사이 놓친 변화를 메운다. 끊기면 EventSource가 알아서 다시 연결한다.
 */
export function useBroadcastEvents(id: number, enabled: boolean) {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!enabled) return
    const source = openBroadcastEvents(id)
    const refetch = () => void queryClient.invalidateQueries({ queryKey: broadcastDetailKey(id) })
    source.addEventListener('open', refetch)
    source.addEventListener(BROADCAST_STATUS_EVENT, refetch)
    source.addEventListener(BROADCAST_UPDATED_EVENT, refetch)
    source.addEventListener(VIEWER_COUNT_EVENT, (e) => {
      const { count } = JSON.parse(e.data) as { count: number }
      queryClient.setQueryData<BroadcastDetail>(broadcastDetailKey(id), (old) => old && { ...old, viewerCount: count })
    })
    return () => source.close()
  }, [id, enabled, queryClient])
}
