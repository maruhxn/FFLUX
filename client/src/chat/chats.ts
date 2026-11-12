import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../api/client'

/**
 * LIVE 방송의 채팅. 입장 시 목록의 항목과 SSE `chat` 이벤트가 같은 모양이다.
 * 스트리머가 가린 채팅([hidden])은 닉네임과 본문이 null이다.
 */
export type ChatMessage = {
  id: number
  authorId: number
  authorNickname: string | null
  content: string | null
  hidden: boolean
}

/** 채팅 금지 상태. [until]이 null이면 스트리머가 풀 때까지다(서울 시각, 시간대 없음). */
export type ChatRestriction = { until: string | null }

/** 입장 시 읽는 채팅 방. [restriction]은 로그인한 내 채팅 금지 상태이고, 금지 중이 아니면 null이다. */
export type ChatRoom = { chats: ChatMessage[]; restriction: ChatRestriction | null }

/** 스트리머의 채팅 금지 목록 항목. [memberNickname]은 금지 당시 닉네임이다. */
export type RestrictedMember = { memberId: number; memberNickname: string; until: string | null }

/** 채팅 금지 기간. 서버 `ChatRestrictionDuration`과 같다. */
export type RestrictionDuration = 'THIRTY_SECONDS' | 'ONE_HOUR' | 'UNTIL_LIFTED'

/** 본문 최대 길이. 서버 값 객체(`ChatContent`)와 같다. */
export const CHAT_MAX_LENGTH = 200

/** 화면에 들고 있는 채팅 수. 오래 켜 둔 방송에서 목록이 끝없이 늘지 않게 오래된 것부터 버린다. */
const CHAT_KEEP_COUNT = 200

const chatsKey = (broadcastId: number) => ['chats', broadcastId] as const
const RESTRICTIONS_KEY = ['chat-restrictions'] as const

/** 서버는 서울 시각을 시간대 없이 보낸다. 밀리초까지 자르고 시간대를 붙여 기기 시간대와 상관없이 같은 순간으로 읽는다. */
export const seoulTimeToMs = (time: string) => new Date(`${time.slice(0, 23)}+09:00`).getTime()

/**
 * 입장할 때 읽는 최근 50개(오래된 것부터)와 내 채팅 금지 상태. 그 뒤 채팅은 SSE로 {@link appendChat}이 붙인다.
 * 다시 읽는 동안 SSE로 먼저 붙은 채팅이 덮여 사라지지 않게, 읽은 목록보다 새 채팅은 남긴다.
 */
export function useChats(broadcastId: number) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: chatsKey(broadcastId),
    queryFn: async () => {
      const fetched = await api<ChatRoom>(`/api/broadcasts/${broadcastId}/chats`)
      const lastId = fetched.chats.at(-1)?.id ?? 0
      const newer = (queryClient.getQueryData<ChatRoom>(chatsKey(broadcastId))?.chats ?? []).filter((it) => it.id > lastId)
      return { ...fetched, chats: [...fetched.chats, ...newer].slice(-CHAT_KEEP_COUNT) }
    },
    // SSE가 목록을 이어 붙이므로 창을 다시 볼 때마다 새로 읽지 않는다. 재연결 때는 events.ts가 다시 읽는다.
    staleTime: Infinity,
  })
}

const updateRoom = (queryClient: QueryClient, broadcastId: number, update: (room: ChatRoom) => ChatRoom) =>
  queryClient.setQueryData<ChatRoom>(chatsKey(broadcastId), (old) => old && update(old))

/** SSE로 받은 채팅을 목록 끝에 붙인다. 목록과 SSE가 겹칠 수 있어 같은 ID는 건너뛴다. */
export function appendChat(queryClient: QueryClient, broadcastId: number, chat: ChatMessage) {
  updateRoom(queryClient, broadcastId, (room) =>
    room.chats.some((it) => it.id === chat.id) ? room : { ...room, chats: [...room.chats, chat].slice(-CHAT_KEEP_COUNT) },
  )
}

/** SSE `chat-hidden`: 이미 받은 채팅을 가린 자리로 바꾼다. */
export function hideChat(queryClient: QueryClient, broadcastId: number, chatId: number) {
  updateRoom(queryClient, broadcastId, (room) => ({
    ...room,
    chats: room.chats.map((it) => (it.id === chatId ? { ...it, authorNickname: null, content: null, hidden: true } : it)),
  }))
}

/** 내 채팅 금지 상태를 바꾼다(SSE `chat-restriction`이 나에 대한 것일 때, 금지 기간이 끝났을 때). */
export function setMyRestriction(queryClient: QueryClient, broadcastId: number, restriction: ChatRestriction | null) {
  updateRoom(queryClient, broadcastId, (room) => ({ ...room, restriction }))
}

/** 끊긴 사이 놓친 채팅을 메우려고 목록을 다시 읽는다. */
export const refetchChats = (queryClient: QueryClient, broadcastId: number) =>
  void queryClient.invalidateQueries({ queryKey: chatsKey(broadcastId) })

/** 채팅을 보낸다. 보낸 채팅도 SSE로 돌아와 목록에 붙는다. 금지돼 거절되면(403) 내 금지 상태를 다시 읽는다. */
export function usePostChat(broadcastId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (content: string) =>
      api<{ id: number }>(`/api/broadcasts/${broadcastId}/chats`, { method: 'POST', body: { content } }),
    onError: (e) => {
      if (e instanceof ApiError && e.status === 403) refetchChats(queryClient, broadcastId)
    },
  })
}

/** 스트리머의 채팅 가리기. 화면은 SSE `chat-hidden`으로 바뀐다. */
export function useHideChat() {
  return useMutation({
    mutationFn: (chatId: number) => api<void>(`/api/chats/${chatId}/hide`, { method: 'POST' }),
  })
}

/** 스트리머의 채팅 금지 목록. 지금 금지 중인 회원만 온다. */
export function useChatRestrictions(enabled = true) {
  return useQuery({
    queryKey: RESTRICTIONS_KEY,
    queryFn: () => api<RestrictedMember[]>('/api/chat-restrictions'),
    enabled,
  })
}

/** 이 채팅을 쓴 회원을 채팅 금지한다. 다시 금지하면 마지막 선택으로 바뀐다. */
export function useRestrictChat() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ chatId, duration }: { chatId: number; duration: RestrictionDuration }) =>
      api<void>(`/api/chats/${chatId}/restrict`, { method: 'POST', body: { duration } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: RESTRICTIONS_KEY }),
  })
}

/** 채팅 금지를 푼다. */
export function useLiftRestriction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (memberId: number) => api<void>(`/api/chat-restrictions/${memberId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: RESTRICTIONS_KEY }),
  })
}
