import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { useMe } from '../auth/auth'
import { CHAT_MAX_LENGTH, useChats, usePostChat } from '../chat/chats'

/** 맨 아래에서 이만큼(px) 안이면 아래를 보고 있는 것으로 친다. */
const STICK_TO_BOTTOM_PX = 40

/** LIVE 방송 채팅. 누구나 읽고, 로그인하면 보낸다. 아래를 보고 있으면 새 채팅이 올 때 맨 아래로 따라 내린다. */
export function Chat({ broadcastId }: { broadcastId: number }) {
  const { data: me } = useMe()
  const { data: chats, error } = useChats(broadcastId)
  const listRef = useRef<HTMLUListElement>(null)
  // 위로 올려 지난 채팅을 읽는 중에는 끌어내리지 않는다.
  const atBottomRef = useRef(true)

  useEffect(() => {
    const list = listRef.current
    if (list && atBottomRef.current) list.scrollTop = list.scrollHeight
  }, [chats])

  return (
    <section className="flex h-96 flex-col lg:h-full">
      <h2 className="shrink-0 border-b border-neutral-800 py-3 text-center text-sm font-semibold">채팅</h2>
      <ul
        ref={listRef}
        onScroll={(e) => {
          const list = e.currentTarget
          atBottomRef.current = list.scrollHeight - list.scrollTop - list.clientHeight < STICK_TO_BOTTOM_PX
        }}
        className="min-h-0 flex-1 space-y-1.5 overflow-y-auto px-4 py-3 text-sm"
      >
        {error && <li className="text-red-400">{error.message}</li>}
        {chats?.length === 0 && <li className="text-neutral-500">아직 채팅이 없습니다.</li>}
        {chats?.map((chat) => (
          <li key={chat.id} className="break-words">
            <span className="mr-2 font-semibold text-emerald-300">{chat.authorNickname}</span>
            <span className="text-neutral-200">{chat.content}</span>
          </li>
        ))}
      </ul>
      <div className="shrink-0 border-t border-neutral-800 p-3">
        {me ? (
          <ChatForm broadcastId={broadcastId} />
        ) : (
          <p className="text-sm text-neutral-400">
            <Link to="/login" className="text-emerald-400 hover:underline">
              로그인
            </Link>
            하면 채팅할 수 있습니다.
          </p>
        )}
      </div>
    </section>
  )
}

function ChatForm({ broadcastId }: { broadcastId: number }) {
  const [content, setContent] = useState('')
  const post = usePostChat(broadcastId)
  const error = post.error && ((post.error instanceof ApiError && post.error.fieldErrors.content) || post.error.message)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!content.trim() || post.isPending) return
    post.mutate(content, { onSuccess: () => setContent('') })
  }

  return (
    <form onSubmit={submit} className="space-y-1">
      <div className="flex gap-2">
        <input
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={CHAT_MAX_LENGTH}
          placeholder="채팅을 입력하세요."
          className="min-w-0 flex-1 rounded-full border border-neutral-700 bg-neutral-900 px-4 py-2 text-neutral-100 outline-none placeholder:text-neutral-600 focus:border-emerald-500"
        />
        <button
          type="submit"
          disabled={!content.trim() || post.isPending}
          className="rounded-full bg-emerald-500 px-4 py-2 text-sm font-semibold text-neutral-950 disabled:opacity-50"
        >
          보내기
        </button>
      </div>
      {error && <p className="text-xs text-red-400">{error}</p>}
    </form>
  )
}
