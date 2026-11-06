import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
}

type BroadcastPage = { items: Broadcast[]; nextCursor: number | null }

export type BroadcastForm = { title: string; description: string }

export function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
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

/** 상세. 시청 주소는 LIVE 방송에만 있다. */
export type BroadcastDetail = Broadcast & { whepUrl: string | null; hlsUrl: string | null }

export const broadcastDetailKey = (id: number) => [...BROADCASTS_KEY, 'detail', id] as const

/** 상세. 상태·제목 변화를 따라가려면 `useBroadcastEvents`를 함께 쓴다. */
export function useBroadcast(id: number) {
  return useQuery({
    queryKey: broadcastDetailKey(id),
    queryFn: () => api<BroadcastDetail>(`/api/broadcasts/${id}`),
  })
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
