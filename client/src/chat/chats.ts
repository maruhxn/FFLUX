import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api } from '../api/client'

/** LIVE 방송의 채팅. 입장 시 목록의 항목과 SSE `chat` 이벤트가 같은 모양이다. */
export type ChatMessage = { id: number; authorNickname: string; content: string }

/** 본문 최대 길이. 서버 값 객체(`ChatContent`)와 같다. */
export const CHAT_MAX_LENGTH = 200

/** 화면에 들고 있는 채팅 수. 오래 켜 둔 방송에서 목록이 끝없이 늘지 않게 오래된 것부터 버린다. */
const CHAT_KEEP_COUNT = 200

const chatsKey = (broadcastId: number) => ['chats', broadcastId] as const

/**
 * 입장할 때 읽는 최근 50개(오래된 것부터). 그 뒤 채팅은 SSE로 {@link appendChat}이 붙인다.
 * 다시 읽는 동안 SSE로 먼저 붙은 채팅이 덮여 사라지지 않게, 읽은 목록보다 새 채팅은 남긴다.
 */
export function useChats(broadcastId: number) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: chatsKey(broadcastId),
    queryFn: async () => {
      const fetched = await api<ChatMessage[]>(`/api/broadcasts/${broadcastId}/chats`)
      const lastId = fetched.at(-1)?.id ?? 0
      const newer = (queryClient.getQueryData<ChatMessage[]>(chatsKey(broadcastId)) ?? []).filter((it) => it.id > lastId)
      return [...fetched, ...newer].slice(-CHAT_KEEP_COUNT)
    },
    // SSE가 목록을 이어 붙이므로 창을 다시 볼 때마다 새로 읽지 않는다. 재연결 때는 events.ts가 다시 읽는다.
    staleTime: Infinity,
  })
}

/** SSE로 받은 채팅을 목록 끝에 붙인다. 목록과 SSE가 겹칠 수 있어 같은 ID는 건너뛴다. */
export function appendChat(queryClient: QueryClient, broadcastId: number, chat: ChatMessage) {
  queryClient.setQueryData<ChatMessage[]>(chatsKey(broadcastId), (old = []) =>
    old.some((it) => it.id === chat.id) ? old : [...old, chat].slice(-CHAT_KEEP_COUNT),
  )
}

/** 끊긴 사이 놓친 채팅을 메우려고 목록을 다시 읽는다. */
export const refetchChats = (queryClient: QueryClient, broadcastId: number) =>
  void queryClient.invalidateQueries({ queryKey: chatsKey(broadcastId) })

/** 채팅을 보낸다. 보낸 채팅도 SSE로 돌아와 목록에 붙는다. */
export function usePostChat(broadcastId: number) {
  return useMutation({
    mutationFn: (content: string) =>
      api<{ id: number }>(`/api/broadcasts/${broadcastId}/chats`, { method: 'POST', body: { content } }),
  })
}
