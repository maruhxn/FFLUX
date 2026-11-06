import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../api/client'

export type Me = { memberId: number; nickname: string }

const ME_KEY = ['me'] as const

/** 현재 로그인한 회원. 로그인하지 않았으면 null. */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: async () => {
      try {
        return await api<Me>('/api/auth/me')
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { username: string; password: string }) =>
      api<Me>('/api/auth/login', { method: 'POST', body }),
    onSuccess: (me) => queryClient.setQueryData(ME_KEY, me),
  })
}

export function useTestLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (account: 'streamer' | 'viewer') =>
      api<Me>('/api/auth/test-login', { method: 'POST', body: { account } }),
    onSuccess: (me) => queryClient.setQueryData(ME_KEY, me),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/auth/logout', { method: 'POST' }),
    onSuccess: () => queryClient.setQueryData(ME_KEY, null),
  })
}

export function useSignUp() {
  return useMutation({
    mutationFn: (body: { username: string; password: string; nickname: string }) =>
      api<{ memberId: number }>('/api/members', { method: 'POST', body }),
  })
}
