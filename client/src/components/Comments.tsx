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
  useWriteComment,
  type Comment,
} from '../comment/comments'

const TEXTAREA =
  'w-full rounded-md border border-neutral-700 bg-neutral-900 px-3 py-2 text-neutral-100 outline-none placeholder:text-neutral-600 focus:border-emerald-500'

/** 댓글 칸 아래 오류. 본문 규칙 위반은 칸 오류로, 그 밖(409 등)은 메시지로 보여 준다. */
function errorOf(error: Error | null) {
  if (!error) return null
  return (error instanceof ApiError && error.fieldErrors.content) || error.message
}

/** 다시보기 댓글. 로그인하면 쓰고, 내 댓글만 고치고 지운다. */
export function Comments({ broadcastId }: { broadcastId: number }) {
  const { data: me } = useMe()
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useComments(broadcastId)

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">댓글 {data ? data.pages[0].count.toLocaleString('ko-KR') : ''}</h2>
      {me ? (
        <CommentForm broadcastId={broadcastId} />
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
                <CommentItem key={comment.id} broadcastId={broadcastId} comment={comment} mine={comment.authorId === me?.memberId} />
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

function CommentForm({ broadcastId }: { broadcastId: number }) {
  const [content, setContent] = useState('')
  const write = useWriteComment(broadcastId)

  function submit(e: FormEvent) {
    e.preventDefault()
    write.mutate(content, { onSuccess: () => setContent('') })
  }

  return (
    <form onSubmit={submit} className="space-y-2">
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        maxLength={COMMENT_MAX_LENGTH}
        rows={3}
        required
        placeholder="댓글을 입력하세요."
        className={TEXTAREA}
      />
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs text-red-400">{errorOf(write.error)}</span>
        <div className="flex items-center gap-3">
          <span className="text-xs text-neutral-500 tabular-nums">
            {content.length}/{COMMENT_MAX_LENGTH}
          </span>
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

function CommentItem({ broadcastId, comment, mine }: { broadcastId: number; comment: Comment; mine: boolean }) {
  const [editing, setEditing] = useState<string | null>(null)
  const edit = useEditComment(broadcastId)
  const remove = useDeleteComment(broadcastId)

  if (comment.deleted) return <li className="py-3 text-sm text-neutral-500">삭제된 댓글입니다</li>

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
    <li className="space-y-1 py-3">
      <div className="flex items-center gap-2 text-sm">
        <span className="font-semibold">{comment.authorNickname}</span>
        <span className="text-xs text-neutral-500">{formatDateTime(comment.createdAt)}</span>
        {comment.edited && <span className="text-xs text-neutral-500">(수정됨)</span>}
        {mine && editing === null && (
          <span className="ml-auto flex gap-2 text-xs">
            <button type="button" onClick={() => setEditing(comment.content ?? '')} className="text-neutral-400 hover:text-white">
              수정
            </button>
            <button type="button" onClick={onDelete} disabled={remove.isPending} className="text-red-400 hover:text-red-300">
              삭제
            </button>
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
    </li>
  )
}
