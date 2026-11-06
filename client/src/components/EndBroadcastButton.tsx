import { useEndBroadcast } from '../broadcast/broadcast'

/** 종료 요청 버튼. 이 탭에서 송출 중이면 송출 자원도 정리된다. */
export function EndBroadcastButton({ broadcastId }: { broadcastId: number }) {
  const end = useEndBroadcast(broadcastId)

  function onClick() {
    if (!window.confirm('방송을 종료할까요? 종료한 방송은 다시 송출할 수 없습니다.')) return
    end.mutate()
  }

  return (
    <>
      <button
        type="button"
        onClick={onClick}
        disabled={end.isPending}
        className="rounded-md border border-red-800 px-3 py-1.5 text-sm text-red-400 hover:bg-red-950 disabled:opacity-50"
      >
        방송 종료
      </button>
      {end.error && <p className="text-sm text-red-400">{end.error.message}</p>}
    </>
  )
}
