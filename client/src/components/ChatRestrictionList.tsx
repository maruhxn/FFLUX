import { seoulTimeToMs, useChatRestrictions, useLiftRestriction } from '../chat/chats'
import { formatRemaining, useNow } from './remaining'

/** 스트리머의 채팅 금지 목록. 방송이 없을 때도, 채팅에 메시지가 보이지 않는 회원도 여기서 푼다. */
export function ChatRestrictionList() {
  const { data: restrictions, error } = useChatRestrictions()
  const lift = useLiftRestriction()
  const now = useNow()
  // 기간이 지난 금지는 다시 읽기 전에도 바로 뺀다.
  const active = restrictions?.filter((it) => !it.until || seoulTimeToMs(it.until) > now)

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">채팅 금지 목록</h2>
      {error && <p className="text-sm text-red-400">{error.message}</p>}
      {active?.length === 0 && <p className="text-sm text-neutral-500">채팅 금지한 회원이 없습니다.</p>}
      <ul className="divide-y divide-neutral-800 rounded-md border border-neutral-800">
        {active?.map((it) => (
          <li key={it.memberId} className="flex items-center justify-between gap-3 px-4 py-2 text-sm">
            <span className="font-semibold text-neutral-100">{it.memberNickname}</span>
            <span className="flex-1 text-neutral-400">
              {it.until ? `남은 시간 ${formatRemaining(seoulTimeToMs(it.until) - now)}` : '풀 때까지'}
            </span>
            <button
              type="button"
              disabled={lift.isPending}
              onClick={() => lift.mutate(it.memberId)}
              className="rounded border border-neutral-600 px-2 py-1 text-xs text-neutral-200 hover:bg-neutral-800 disabled:opacity-50"
            >
              해제
            </button>
          </li>
        ))}
      </ul>
      {lift.error && <p className="text-sm text-red-400">{lift.error.message}</p>}
    </section>
  )
}
