import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { api } from '../api/client'
import { endBroadcast } from './publishSession'

export type BroadcastStatus = 'READY' | 'LIVE' | 'ENDED'

export type Broadcast = {
  id: number
  streamerId: number
  streamerNickname: string
  title: string
  description: string | null
  status: BroadcastStatus
  createdAt: string
  /** LIVE가 된 시각(방송 경과 시간의 기준). LIVE가 된 적 없으면 null. */
  startedAt: string | null
  /** LIVE 방송의 지금 시청자 수(로그인한 시청자만 센다). LIVE가 아니면 null. */
  viewerCount: number | null
}

type BroadcastPage = { items: Broadcast[]; nextCursor: number | null }

export type BroadcastForm = { title: string; description: string }

export function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}

export function formatViewerCount(count: number) {
  return `${count.toLocaleString('ko-KR')}명`
}

/** 녹화 길이. 한 시간 미만은 `M:SS`, 이상은 `H:MM:SS`. */
export function formatDuration(totalSeconds: number) {
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${seconds}` : `${minutes}:${seconds}`
}

const BROADCASTS_KEY = ['broadcasts'] as const

/** 목록 종류: 메인(LIVE), 지난 방송(ENDED), 내 방송(mine). */
export type BroadcastListKind = 'LIVE' | 'ENDED' | 'mine'

export function useBroadcastList(kind: BroadcastListKind) {
  return useInfiniteQuery({
    queryKey: [...BROADCASTS_KEY, 'list', kind],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams()
      if (kind !== 'mine') params.set('status', kind)
      if (pageParam !== null) params.set('cursor', String(pageParam))
      const path = kind === 'mine' ? '/api/broadcasts/mine' : '/api/broadcasts'
      return api<BroadcastPage>(`${path}?${params}`)
    },
    getNextPageParam: (last) => last.nextCursor,
  })
}

export type RecordingStatus = 'PROCESSING' | 'READY' | 'FAILED'

/** 녹화. [url]은 READY 녹화의 재생 주소(`/vod/...`)이고, 그 밖에는 null이다. */
export type Recording = { status: RecordingStatus; url: string | null; durationSeconds: number | null }

/**
 * 상세. 시청 주소는 LIVE 방송에만 있다.
 * [recording]은 READY면 모두에게, 처리 중·실패면 스트리머에게만 온다. 없거나 보이지 않으면 null이다.
 */
export type BroadcastDetail = Broadcast & { whepUrl: string | null; hlsUrl: string | null; recording: Recording | null }

export const broadcastDetailKey = (id: number) => [...BROADCASTS_KEY, 'detail', id] as const

/** 다시보기 목록의 항목. [startedAt]은 방송이 LIVE가 된 시각(방송 날짜)이다. */
export type Replay = {
  broadcastId: number
  title: string
  streamerNickname: string
  durationSeconds: number | null
  startedAt: string | null
}

type ReplayPage = { items: Replay[]; nextCursor: number | null }

/** 다시보기 목록: 재생할 수 있는 녹화가 있는 끝난 방송, 최신순. */
export function useReplayList() {
  return useInfiniteQuery({
    queryKey: [...BROADCASTS_KEY, 'replays'],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams()
      if (pageParam !== null) params.set('cursor', String(pageParam))
      return api<ReplayPage>(`/api/replays?${params}`)
    },
    getNextPageParam: (last) => last.nextCursor,
  })
}

/** 상세. 상태·제목 변화를 따라가려면 `useBroadcastEvents`를 함께 쓴다. */
export function useBroadcast(id: number) {
  return useQuery({
    queryKey: broadcastDetailKey(id),
    queryFn: () => api<BroadcastDetail>(`/api/broadcasts/${id}`),
  })
}

/**
 * 끝난 방송의 녹화를 기다리며 상세를 다시 읽을지. 처리 중인 녹화는 스트리머에게만 오므로 스트리머 화면만 기다린다.
 * 시청자는 치지직처럼 종료 화면에 머물고, 처리가 끝난 다시보기는 다시보기 목록에서 찾는다.
 * 녹화는 방송이 끝난 뒤 리먹싱 훅의 통지로 생겨 종료 직후에는 아직 없을 수 있으므로, LIVE를 지켜본 스트리머([streamerSawLive])는 그때도 기다린다.
 */
export function waitsForRecording(detail: BroadcastDetail, streamerSawLive: boolean) {
  if (detail.status !== 'ENDED') return false
  return detail.recording === null ? streamerSawLive : detail.recording.status === 'PROCESSING'
}

const RECORDING_POLL_MS = 5_000
/** 이만큼 기다려도 녹화가 나오지 않으면 그만 읽는다(녹화하지 않은 방송이거나 훅이 통지하지 못했다). */
const RECORDING_WAIT_MS = 5 * 60_000

/** [waiting]인 동안 상세를 주기적으로 다시 읽는다. 끝난 방송은 SSE를 구독하지 않으므로 녹화 상태 변화는 이렇게 따라간다. */
export function useRecordingWait(id: number, waiting: boolean) {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!waiting) return
    const poll = setInterval(
      () => void queryClient.invalidateQueries({ queryKey: broadcastDetailKey(id) }),
      RECORDING_POLL_MS,
    )
    const giveUp = setTimeout(() => clearInterval(poll), RECORDING_WAIT_MS)
    return () => {
      clearInterval(poll)
      clearTimeout(giveUp)
    }
  }, [id, waiting, queryClient])
}

/** 빈 설명은 서버가 없음으로 저장한다. */
const toBody = (form: BroadcastForm) => ({ title: form.title, description: form.description || null })

export function useRegisterBroadcast() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (form: BroadcastForm) =>
      api<{ id: number }>('/api/broadcasts', { method: 'POST', body: toBody(form) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: BROADCASTS_KEY }),
  })
}

export function useEditBroadcast(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (form: BroadcastForm) => api<void>(`/api/broadcasts/${id}`, { method: 'PATCH', body: toBody(form) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: BROADCASTS_KEY }),
  })
}

export function useDeleteBroadcast(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>(`/api/broadcasts/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: BROADCASTS_KEY }),
  })
}

export type PublishToken = { token: string; whipUrl: string }

/** 송출 토큰 발급. 토큰은 약 1분 안에 한 번만 쓸 수 있으므로 송출 직전에 받는다. */
export function useIssuePublishToken(id: number) {
  return useMutation({
    mutationFn: (record: boolean) =>
      api<PublishToken>(`/api/broadcasts/${id}/publish-token`, { method: 'POST', body: { record } }),
  })
}

/** 종료 요청. 이 탭에서 송출 중이면 송출 자원도 정리한다. */
export function useEndBroadcast(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => endBroadcast(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: BROADCASTS_KEY }),
  })
}
