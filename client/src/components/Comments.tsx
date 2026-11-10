import type { UseMutationResult } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { useMe } from '../auth/auth'
import { formatDateTime } from '../broadcast/broadcast'
import {
  COMMENT_MAX_LENGTH,
  useComments,
  useDeleteComment,
  useEditComment,
  useReplies,
  useWriteComment,
  useWriteReply,
  type Comment,
} from '../comment/comments'

const TEXTAREA =
  'w-full rounded-md border border-neutral-700 bg-neutral-900 px-3 py-2 text-neutral-100 outline-none placeholder:text-neutral-600 focus:border-emerald-500'

/** 댓글 칸 아래 오류. 본문 규칙 위반은 칸 오류로, 그 밖(409 등)은 메시지로 보여 준다. */
function errorOf(error: Error | null) {
  if (!error) return null
  return (error instanceof ApiError && error.fieldErrors.content) || error.message
}

/** 다시보기 댓글. 로그인하면 쓰고 답글을 달며, 내 댓글·답글만 고치고 지운다. */
export function Comments({ broadcastId }: { broadcastId: number }) {
  const { data: me } = useMe()
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useComments(broadcastId)

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">댓글 {data ? data.pages[0].count.toLocaleString('ko-KR') : ''}</h2>
      {me ? (
        <NewCommentForm broadcastId={broadcastId} />
      ) : (
        <p className="rounded-md border border-neutral-800 px-3 py-3 text-sm text-neutral-400">
          <Link to="/login" className="text-emerald-400 hover:underline">
            로그인
          </Link>
          하면 댓글을 쓸 수 있습니다.
        </p>
      )}
      {isPending ? (
        <p className="text-neutral-400">불러오는 중…</p>
      ) : error ? (
        <p className="text-red-400">{error.message}</p>
      ) : (
        <>
          <ul className="divide-y divide-neutral-800">
            {data.pages
              .flatMap((page) => page.items)
              .map((comment) => (
                <CommentThread key={comment.id} broadcastId={broadcastId} comment={comment} memberId={me?.memberId ?? null} />
              ))}
          </ul>
          {hasNextPage && (
            <button
              type="button"
              onClick={() => fetchNextPage()}
              disabled={isFetchingNextPage}
              className="w-full rounded-md border border-neutral-700 py-2 text-sm hover:bg-neutral-800 disabled:opacity-50"
            >
              더 보기
            </button>
          )}
        </>
      )}
    </section>
  )
}

function NewCommentForm({ broadcastId }: { broadcastId: number }) {
  return <CommentForm write={useWriteComment(broadcastId)} placeholder="댓글을 입력하세요." />
}

/** 원댓글 하나와 그 답글. 답글은 접어 두고 "답글 N개"로 펼친다. 원댓글·답글의 "답글"은 이 원댓글 아래 작성 칸을 연다. */
function CommentThread({ broadcastId, comment, memberId }: { broadcastId: number; comment: Comment; memberId: number | null }) {
  const [open, setOpen] = useState(false)
  // 작성 칸에 채울 글. 다른 글의 "답글"을 누르면 칸을 새로 연다(key).
  const [draft, setDraft] = useState<{ text: string; key: number } | null>(null)
  // 삭제된 원댓글에는 답글을 달 수 없으므로(서버도 409) 그 아래 답글에서도 "답글"을 숨긴다.
  const replyTo =
    memberId === null || comment.deleted
      ? undefined
      : (target: Comment) => setDraft({ text: `@${target.authorNickname} `, key: Date.now() })

  return (
    <li className="py-3">
      <CommentItem
        broadcastId={broadcastId}
        comment={comment}
        mine={comment.authorId === memberId}
        onReply={replyTo && (() => replyTo(comment))}
      />
      {comment.replyCount > 0 && (
        <button type="button" onClick={() => setOpen(!open)} className="mt-1 text-xs font-semibold text-emerald-400">
          {open ? '답글 접기' : `답글 ${comment.replyCount.toLocaleString('ko-KR')}개`}
        </button>
      )}
      <div className="ml-6 space-y-3">
        {open && <Replies broadcastId={broadcastId} parentId={comment.id} memberId={memberId} onReply={replyTo} />}
        {draft && (
          <ReplyForm
            key={draft.key}
            broadcastId={broadcastId}
            parentId={comment.id}
            initial={draft.text}
            onDone={() => {
              setDraft(null)
              setOpen(true)
            }}
            onCancel={() => setDraft(null)}
          />
        )}
      </div>
    </li>
  )
}

function Replies({
  broadcastId,
  parentId,
  memberId,
  onReply,
}: {
  broadcastId: number
  parentId: number
  memberId: number | null
  onReply?: (target: Comment) => void
}) {
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useReplies(broadcastId, parentId)

  if (isPending) return <p className="mt-2 text-sm text-neutral-400">불러오는 중…</p>
  if (error) return <p className="mt-2 text-sm text-red-400">{error.message}</p>

  return (
    <div>
      <ul>
        {data.pages
          .flatMap((page) => page.items)
          .map((reply) => (
            <li key={reply.id} className="pt-3">
              <CommentItem
                broadcastId={broadcastId}
                comment={reply}
                mine={reply.authorId === memberId}
                onReply={onReply && (() => onReply(reply))}
              />
            </li>
          ))}
      </ul>
      {hasNextPage && (
        <button
          type="button"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="mt-2 text-xs font-semibold text-emerald-400 disabled:opacity-50"
        >
          답글 더 보기
        </button>
      )}
    </div>
  )
}

function ReplyForm({
  broadcastId,
  parentId,
  ...form
}: {
  broadcastId: number
  parentId: number
  initial: string
  onDone: () => void
  onCancel: () => void
}) {
  return <CommentForm write={useWriteReply(broadcastId, parentId)} placeholder="답글을 입력하세요." {...form} />
}

/** 댓글·답글 작성 칸. 등록하면 비우거나 [onDone]을 부른다. [onCancel]이 있으면 "취소"를 보인다. */
function CommentForm({
  write,
  placeholder,
  initial = '',
  onDone,
  onCancel,
}: {
  write: UseMutationResult<unknown, Error, string>
  placeholder: string
  initial?: string
  onDone?: () => void
  onCancel?: () => void
}) {
  const [content, setContent] = useState(initial)

  function submit(e: FormEvent) {
    e.preventDefault()
    write.mutate(content, { onSuccess: () => (onDone ? onDone() : setContent('')) })
  }

  return (
    <form onSubmit={submit} className="space-y-2">
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        maxLength={COMMENT_MAX_LENGTH}
        rows={3}
        required
        autoFocus={!!onCancel}
        placeholder={placeholder}
        className={TEXTAREA}
      />
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs text-red-400">{errorOf(write.error)}</span>
        <div className="flex items-center gap-3">
          <span className="text-xs text-neutral-500 tabular-nums">
            {content.length}/{COMMENT_MAX_LENGTH}
          </span>
          {onCancel && (
            <button type="button" onClick={onCancel} className="rounded-md border border-neutral-700 px-3 py-1.5 text-sm hover:bg-neutral-800">
              취소
            </button>
          )}
          <button
            type="submit"
            disabled={write.isPending}
            className="rounded-md bg-emerald-500 px-4 py-1.5 text-sm font-semibold text-neutral-950 disabled:opacity-50"
          >
            등록
          </button>
        </div>
      </div>
    </form>
  )
}

/** 댓글·답글 한 개. [onReply]가 있으면(로그인했으면) "답글"을 보인다. */
function CommentItem({
  broadcastId,
  comment,
  mine,
  onReply,
}: {
  broadcastId: number
  comment: Comment
  mine: boolean
  onReply?: () => void
}) {
  const [editing, setEditing] = useState<string | null>(null)
  const edit = useEditComment(broadcastId)
  const remove = useDeleteComment(broadcastId)

  if (comment.deleted) return <p className="text-sm text-neutral-500">삭제된 댓글입니다</p>

  function submit(e: FormEvent) {
    e.preventDefault()
    if (editing === null) return
    edit.mutate({ id: comment.id, content: editing }, { onSuccess: () => setEditing(null) })
  }

  function onDelete() {
    if (!window.confirm('댓글을 삭제할까요?')) return
    remove.mutate(comment.id)
  }

  return (
    <div className="space-y-1">
      <div className="flex items-center gap-2 text-sm">
        <span className="font-semibold">{comment.authorNickname}</span>
        <span className="text-xs text-neutral-500">{formatDateTime(comment.createdAt)}</span>
        {comment.edited && <span className="text-xs text-neutral-500">(수정됨)</span>}
        {editing === null && (onReply || mine) && (
          <span className="ml-auto flex gap-2 text-xs">
            {onReply && (
              <button type="button" onClick={onReply} className="text-neutral-400 hover:text-white">
                답글
              </button>
            )}
            {mine && (
              <>
                <button type="button" onClick={() => setEditing(comment.content ?? '')} className="text-neutral-400 hover:text-white">
                  수정
                </button>
                <button type="button" onClick={onDelete} disabled={remove.isPending} className="text-red-400 hover:text-red-300">
                  삭제
                </button>
              </>
            )}
          </span>
        )}
      </div>
      {editing === null ? (
        <p className="whitespace-pre-wrap break-words text-neutral-200">{comment.content}</p>
      ) : (
        <form onSubmit={submit} className="space-y-2">
          <textarea
            value={editing}
            onChange={(e) => setEditing(e.target.value)}
            maxLength={COMMENT_MAX_LENGTH}
            rows={3}
            required
            className={TEXTAREA}
          />
          <div className="flex items-center justify-end gap-2">
            <span className="mr-auto text-xs text-red-400">{errorOf(edit.error)}</span>
            <button type="button" onClick={() => setEditing(null)} className="rounded-md border border-neutral-700 px-3 py-1 text-sm hover:bg-neutral-800">
              취소
            </button>
            <button type="submit" disabled={edit.isPending} className="rounded-md bg-emerald-500 px-3 py-1 text-sm font-semibold text-neutral-950 disabled:opacity-50">
              저장
            </button>
          </div>
        </form>
      )}
      {remove.error && <p className="text-xs text-red-400">{remove.error.message}</p>}
    </div>
  )
}
