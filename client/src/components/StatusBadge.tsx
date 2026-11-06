import type { BroadcastStatus } from '../broadcast/broadcast'

const STATUS_LABEL: Record<BroadcastStatus, string> = { READY: '대기', LIVE: 'LIVE', ENDED: '종료' }
const STATUS_STYLE: Record<BroadcastStatus, string> = {
  READY: 'bg-neutral-700 text-neutral-200',
  LIVE: 'bg-red-600 text-white',
  ENDED: 'bg-neutral-800 text-neutral-400',
}

export function StatusBadge({ status }: { status: BroadcastStatus }) {
  return <span className={`rounded px-1.5 py-0.5 text-xs font-semibold ${STATUS_STYLE[status]}`}>{STATUS_LABEL[status]}</span>
}
