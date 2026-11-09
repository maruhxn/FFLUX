import type { ReactNode } from 'react'
import { formatViewerCount, type Broadcast } from '../broadcast/broadcast'
import { StatusBadge } from './StatusBadge'
import { StreamingTime } from './StreamingTime'

/**
 * 치지직 스튜디오의 방송하기 화면 틀. 위에 화면 자리(`stage`)와 상태 줄, 아래 왼쪽에 스트림 상태, 오른쪽에 설정 줄을 둔다.
 * 등록 전이면 `broadcast`가 없다.
 */
export function Studio({
  heading,
  broadcast,
  stage,
  children,
}: {
  heading: string
  broadcast?: Broadcast
  stage: ReactNode
  children: ReactNode
}) {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">{heading}</h1>
      <div className="overflow-hidden rounded-lg border border-neutral-800">
        {stage}
        <div className="flex items-center justify-center gap-2 bg-neutral-900 py-2 text-sm font-semibold">
          {broadcast ? <StatusBadge status={broadcast.status} /> : <Offline />}
        </div>
        <div className="grid md:grid-cols-[14rem_1fr]">
          <StreamStatus broadcast={broadcast} />
          <div className="min-w-0">{children}</div>
        </div>
      </div>
    </div>
  )
}

/** 화면 자리 가운데 안내. 감싸는 쪽이 크기와 배경을 정한다. */
export function StageMessage({ title, detail, action }: { title: string; detail?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 text-center break-keep">
      <p className="font-semibold">{title}</p>
      {detail && <p className="text-sm text-neutral-400">{detail}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  )
}

/** 화면 자리 안의 흰 알약 버튼 모양. */
export const STAGE_BUTTON = 'rounded-full bg-white px-4 py-1.5 text-sm font-semibold text-neutral-950 disabled:opacity-50'

export function Row({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) {
  return (
    <div className="grid gap-2 border-b border-neutral-800 px-5 py-5 md:grid-cols-[8rem_1fr] md:gap-6">
      <span className="pt-2.5 text-sm font-semibold text-neutral-300">
        {label}
        {required && <span className="text-red-500"> *</span>}
      </span>
      <div className="min-w-0 space-y-2">{children}</div>
    </div>
  )
}

/** 설정 줄 아래 안내 문구. */
export function Hints({ lines }: { lines: ReactNode[] }) {
  return (
    <ul className="space-y-0.5 text-xs text-neutral-500 break-keep">
      {lines.map((line, i) => (
        <li key={i}>• {line}</li>
      ))}
    </ul>
  )
}

/** 맨 아래 버튼 줄. 오류 문구를 버튼 왼쪽에 둔다. */
export function ActionBar({ error, children }: { error?: string | null; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-end gap-3 px-5 py-4">
      {error && <p className="mr-auto text-sm text-red-400 break-keep">{error}</p>}
      {children}
    </div>
  )
}

function Offline() {
  return (
    <>
      <span className="size-2.5 rounded-full border-2 border-neutral-400" />
      오프라인
    </>
  )
}

function StreamStatus({ broadcast }: { broadcast?: Broadcast }) {
  const live = broadcast?.status === 'LIVE' ? broadcast : undefined
  return (
    <aside className="border-b border-neutral-800 md:border-r md:border-b-0">
      <h2 className="border-b border-neutral-800 px-5 py-3 font-bold">스트림 상태</h2>
      <dl className="grid grid-cols-3 md:grid-cols-1">
        <Stat label="상태">{broadcast ? <StatusBadge status={broadcast.status} /> : '오프라인'}</Stat>
        <Stat label="방송 시간">{live?.startedAt ? <StreamingTime startedAt={live.startedAt} /> : '-'}</Stat>
        <Stat label="시청자">{live && live.viewerCount !== null ? formatViewerCount(live.viewerCount) : '-'}</Stat>
      </dl>
    </aside>
  )
}

function Stat({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1 px-4 py-3 text-sm md:border-b md:border-neutral-800 md:px-5 md:text-base md:last:border-b-0">
      <dt className="text-xs text-neutral-500">{label}</dt>
      <dd className="font-semibold break-keep">{children}</dd>
    </div>
  )
}
