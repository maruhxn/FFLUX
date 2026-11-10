import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'

/** 다시보기 댓글. 삭제된 댓글([deleted])은 자리만 남고 작성자와 본문이 null이다. */
export type Comment = {
  id: number
  authorId: number | null
  authorNickname: string | null
  content: string | null
  createdAt: string
  edited: boolean
  deleted: boolean
}

/** [count]는 삭제된 댓글을 뺀 방송의 댓글 수다. */
type CommentPage = { items: Comment[]; nextCursor: number | null; count: number }

/** 본문 최대 길이. 서버 값 객체(`CommentContent`)와 같다. */
export const COMMENT_MAX_LENGTH = 500

const commentsKey = (broadcastId: number) => ['comments', broadcastId] as const

/** 방송의 댓글, 최신순. 다음 페이지는 "더 보기"로 읽는다. */
export function useComments(broadcastId: number) {
  return useInfiniteQuery({
    queryKey: commentsKey(broadcastId),
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams()
      if (pageParam !== null) params.set('cursor', String(pageParam))
      return api<CommentPage>(`/api/broadcasts/${broadcastId}/comments?${params}`)
    },
    getNextPageParam: (last) => last.nextCursor,
  })
}

/** 쓰고 고치고 지운 뒤에는 목록을 처음부터 다시 읽는다. */
function useCommentMutation<T>(broadcastId: number, mutationFn: (input: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: commentsKey(broadcastId) }),
  })
}

export function useWriteComment(broadcastId: number) {
  return useCommentMutation(broadcastId, (content: string) =>
    api<{ id: number }>(`/api/broadcasts/${broadcastId}/comments`, { method: 'POST', body: { content } }),
  )
}

export function useEditComment(broadcastId: number) {
  return useCommentMutation(broadcastId, ({ id, content }: { id: number; content: string }) =>
    api<void>(`/api/comments/${id}`, { method: 'PATCH', body: { content } }),
  )
}

export function useDeleteComment(broadcastId: number) {
  return useCommentMutation(broadcastId, (id: number) => api<void>(`/api/comments/${id}`, { method: 'DELETE' }))
}
