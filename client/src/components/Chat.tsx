import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { useMe } from '../auth/auth'
import {
  CHAT_MAX_LENGTH,
  seoulTimeToMs,
  setMyRestriction,
  useChatRestrictions,
  useChats,
  useHideChat,
  useLiftRestriction,
  usePostChat,
  useRestrictChat,
  type ChatMessage,
  type ChatRestriction,
  type RestrictionDuration,
} from '../chat/chats'
import { useNow, formatRemaining } from './remaining'

/** 맨 아래에서 이만큼(px) 안이면 아래를 보고 있는 것으로 친다. */
const STICK_TO_BOTTOM_PX = 40

/** 휴대폰에서 이만큼(ms) 누르고 있으면 가리기 버튼을 연다. */
const LONG_PRESS_MS = 500

const DURATIONS: { value: RestrictionDuration; label: string }[] = [
  { value: 'THIRTY_SECONDS', label: '30초' },
  { value: 'ONE_HOUR', label: '1시간' },
  { value: 'UNTIL_LIFTED', label: '풀 때까지' },
]

/**
 * LIVE 방송 채팅. 누구나 읽고, 로그인하면 보낸다. 아래를 보고 있으면 새 채팅이 올 때 맨 아래로 따라 내린다.
 * 방송의 스트리머에게만 채팅 관리(메시지 옆 가리기, 닉네임을 누르면 여는 프로필 레이어의 채팅 금지)가 보인다.
 */
export function Chat({ broadcastId, streamerId }: { broadcastId: number; streamerId: number }) {
  const { data: me } = useMe()
  const { data: room, error } = useChats(broadcastId)
  const isStreamer = me?.memberId === streamerId
  const listRef = useRef<HTMLUListElement>(null)
  // 위로 올려 지난 채팅을 읽는 중에는 끌어내리지 않는다.
  const atBottomRef = useRef(true)
  // 스트리머가 휴대폰에서 길게 눌러 가리기 버튼을 연 채팅, 닉네임을 눌러 프로필 레이어를 연 채팅.
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [profileId, setProfileId] = useState<number | null>(null)

  useEffect(() => {
    const list = listRef.current
    if (list && atBottomRef.current) list.scrollTop = list.scrollHeight
  }, [room?.chats])

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
        {room?.chats.length === 0 && <li className="text-neutral-500">아직 채팅이 없습니다.</li>}
        {room?.chats.map((chat) =>
          chat.hidden ? (
            <li key={chat.id} className="text-neutral-500 italic">
              스트리머가 가린 메시지입니다.
            </li>
          ) : isStreamer ? (
            <ModeratedChatLine
              key={chat.id}
              chat={chat}
              isOwn={chat.authorId === me?.memberId}
              selected={selectedId === chat.id}
              onToggleSelect={() => setSelectedId(selectedId === chat.id ? null : chat.id)}
              profileOpen={profileId === chat.id}
              onToggleProfile={() => setProfileId(profileId === chat.id ? null : chat.id)}
            />
          ) : (
            <li key={chat.id} className="break-words">
              <span className="mr-2 font-semibold text-emerald-300">{chat.authorNickname}</span>
              <span className="text-neutral-200">{chat.content}</span>
            </li>
          ),
        )}
      </ul>
      <div className="shrink-0 border-t border-neutral-800 p-3">
        {!me ? (
          <p className="text-sm text-neutral-400">
            <Link to="/login" className="text-emerald-400 hover:underline">
              로그인
            </Link>
            하면 채팅할 수 있습니다.
          </p>
        ) : room?.restriction ? (
          <RestrictedNotice broadcastId={broadcastId} restriction={room.restriction} />
        ) : (
          <ChatForm broadcastId={broadcastId} />
        )}
      </div>
    </section>
  )
}

/**
 * 스트리머에게 보이는 채팅 한 줄. PC는 마우스를 올리면, 휴대폰은 길게 누르면 가리기 버튼이 보이고 다시 길게 누르면 닫힌다.
 * 휴대폰에서는 길게 누를 때 뜨는 텍스트 선택·기본 메뉴를 막고, 길게 누른 뒤 손을 뗄 때의 클릭은 닉네임 클릭으로 치지 않는다.
 */
function ModeratedChatLine({
  chat,
  isOwn,
  selected,
  onToggleSelect,
  profileOpen,
  onToggleProfile,
}: {
  chat: ChatMessage
  isOwn: boolean
  selected: boolean
  onToggleSelect: () => void
  profileOpen: boolean
  onToggleProfile: () => void
}) {
  const hide = useHideChat()
  const pressTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const touchRef = useRef(false)
  const longPressedRef = useRef(false)
  const cancelPress = () => clearTimeout(pressTimer.current)

  return (
    <li
      className="group break-words pointer-coarse:select-none pointer-coarse:[-webkit-touch-callout:none]"
      onPointerDown={(e) => {
        touchRef.current = e.pointerType === 'touch'
        longPressedRef.current = false
        if (!touchRef.current) return
        pressTimer.current = setTimeout(() => {
          longPressedRef.current = true
          onToggleSelect()
        }, LONG_PRESS_MS)
      }}
      onPointerUp={cancelPress}
      onPointerLeave={cancelPress}
      onContextMenu={(e) => {
        if (touchRef.current) e.preventDefault()
      }}
    >
      {isOwn ? (
        <span className="mr-2 font-semibold text-emerald-300">{chat.authorNickname}</span>
      ) : (
        <button
          type="button"
          onClick={() => {
            if (longPressedRef.current) return
            onToggleProfile()
          }}
          className="mr-2 font-semibold text-emerald-300 hover:underline"
        >
          {chat.authorNickname}
        </button>
      )}
      <span className="text-neutral-200">{chat.content}</span>
      <button
        type="button"
        onClick={() => hide.mutate(chat.id)}
        disabled={hide.isPending}
        className={`ml-2 rounded border border-neutral-700 px-1.5 text-xs text-neutral-400 hover:text-neutral-100 ${
          selected ? 'inline' : 'hidden group-hover:inline'
        }`}
      >
        가리기
      </button>
      {hide.error && <p className="text-xs text-red-400">{hide.error.message}</p>}
      {profileOpen && <ProfileLayer chat={chat} />}
    </li>
  )
}

/** 닉네임을 누르면 여는 프로필 레이어. 지금 금지 상태를 보여 주고 금지하거나 푼다. */
function ProfileLayer({ chat }: { chat: ChatMessage }) {
  const { data: restrictions } = useChatRestrictions()
  const restrict = useRestrictChat()
  const lift = useLiftRestriction()
  const now = useNow()
  // 기간이 지난 금지는 목록을 다시 읽기 전에도 금지 아님으로 본다.
  const current = restrictions?.find(
    (it) => it.memberId === chat.authorId && (!it.until || seoulTimeToMs(it.until) > now),
  )
  const error = restrict.error ?? lift.error

  return (
    <div className="mt-1 space-y-2 rounded-md border border-neutral-700 bg-neutral-900 p-3 text-xs">
      <p className="font-semibold text-neutral-100">{chat.authorNickname}</p>
      <p className="text-neutral-400">
        {!current
          ? '채팅 금지 안 됨'
          : current.until
            ? `채팅 금지 중 (남은 시간 ${formatRemaining(seoulTimeToMs(current.until) - now)})`
            : '채팅 금지 중 (풀 때까지)'}
      </p>
      <div className="flex flex-wrap gap-1.5">
        {DURATIONS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            disabled={restrict.isPending}
            onClick={() => restrict.mutate({ chatId: chat.id, duration: value })}
            className="rounded border border-red-500/60 px-2 py-1 text-red-300 hover:bg-red-500/10 disabled:opacity-50"
          >
            {label} 금지
          </button>
        ))}
        {current && (
          <button
            type="button"
            disabled={lift.isPending}
            onClick={() => lift.mutate(chat.authorId)}
            className="rounded border border-neutral-600 px-2 py-1 text-neutral-200 hover:bg-neutral-800 disabled:opacity-50"
          >
            금지 해제
          </button>
        )}
      </div>
      {error && <p className="text-red-400">{error.message}</p>}
    </div>
  )
}

/** 내가 채팅 금지됐을 때 입력 칸 대신 보여 준다. 기간이 끝나면 저절로 입력 칸을 다시 연다. */
function RestrictedNotice({ broadcastId, restriction }: { broadcastId: number; restriction: ChatRestriction }) {
  const queryClient = useQueryClient()
  const now = useNow()
  const remaining = restriction.until ? seoulTimeToMs(restriction.until) - now : null

  useEffect(() => {
    if (remaining !== null && remaining <= 0) setMyRestriction(queryClient, broadcastId, null)
  }, [remaining, queryClient, broadcastId])

  return (
    <p className="rounded-full border border-neutral-800 bg-neutral-900 px-4 py-2 text-sm text-neutral-400">
      {remaining === null
        ? '스트리머가 풀 때까지 채팅이 금지되었습니다.'
        : `채팅이 금지되었습니다. (남은 시간 ${formatRemaining(remaining)})`}
    </p>
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
