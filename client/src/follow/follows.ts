import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'

/** 스트리머의 팔로워 수와 내가 팔로우 중인지. 비로그인이면 [following]은 false다. */
export type FollowStatus = { followerCount: number; following: boolean }

/** 회원별 SSE `broadcast-started`: 팔로우한 스트리머의 방송이 LIVE가 됐다. */
export type BroadcastStartedAlert = { broadcastId: number; streamerNickname: string; title: string }

/** 내가 팔로우한 스트리머. [nickname]은 지금 닉네임이고, [liveBroadcastId]는 방송 중이 아니면 null이다. */
export type Following = { streamerId: number; nickname: string; liveBroadcastId: number | null }

const followStatusKey = (streamerId: number) => ['follow', streamerId] as const
export const FOLLOWINGS_KEY = ['followings'] as const

/** 방송이 끝난 것은 알림이 없어서 이 주기로 다시 읽어 따라간다. */
const FOLLOWINGS_REFRESH_MS = 30_000

/** 내가 팔로우한 스트리머. 방송 중인 스트리머가 먼저, 그 안에서는 최근에 팔로우한 순서다. */
export function useFollowings() {
  return useQuery({
    queryKey: FOLLOWINGS_KEY,
    queryFn: () => api<Following[]>('/api/me/followings'),
    refetchInterval: FOLLOWINGS_REFRESH_MS,
  })
}

export function useFollowStatus(streamerId: number) {
  return useQuery({
    queryKey: followStatusKey(streamerId),
    queryFn: () => api<FollowStatus>(`/api/streamers/${streamerId}/follow`),
  })
}

/** [follow]가 true면 팔로우, false면 언팔로우한다. 둘 다 이미 그 상태여도 성공한다. */
export function useSetFollow(streamerId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (follow: boolean) => api<void>(`/api/streamers/${streamerId}/follow`, { method: follow ? 'PUT' : 'DELETE' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: followStatusKey(streamerId) })
      void queryClient.invalidateQueries({ queryKey: FOLLOWINGS_KEY })
    },
  })
}

/**
 * 사이트를 다른 탭에 둔 채로도 방송 시작을 알 수 있게 브라우저 알림 권한을 묻는다. 팔로우 버튼을 누를 때 부른다(사용자 동작 안에서만 물을 수 있다).
 * 알림을 지원하지 않는 브라우저(홈 화면에 추가하지 않은 iOS Safari 등)는 사이트 안 알림만 받는다.
 */
export function askNotificationPermission() {
  if ('Notification' in window && Notification.permission === 'default') void Notification.requestPermission()
}

/** 탭이 가려져 있고 권한이 있으면 브라우저 알림을 띄운다. 누르면 [onClick]을 부른다. 띄웠으면 true. */
export function showSystemNotification(alert: BroadcastStartedAlert, onClick: () => void): boolean {
  if (document.visibilityState !== 'hidden' || !('Notification' in window) || Notification.permission !== 'granted') return false
  try {
    const notification = new Notification(`${alert.streamerNickname}님이 방송을 시작했습니다`, {
      body: alert.title,
      tag: `broadcast-${alert.broadcastId}`,
    })
    notification.onclick = () => {
      window.focus()
      onClick()
      notification.close()
    }
    return true
  } catch {
    // Android Chrome은 페이지에서 Notification을 만들 수 없다(서비스 워커로만 된다). 사이트 안 알림으로 대신한다.
    return false
  }
}
