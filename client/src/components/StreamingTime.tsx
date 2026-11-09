import { useEffect, useState } from 'react'

/** LIVE가 된 뒤 흐른 시간을 `HH:MM:SS 스트리밍 중`으로 1초마다 다시 그린다. 시각은 이 기기의 시계로 잰다. */
export function StreamingTime({ startedAt }: { startedAt: string }) {
  const [now, setNow] = useState(Date.now)

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  // 서버는 서울 시각을 시간대 없이, 소수점 아래 6자리까지 보낸다. Date가 보장해 읽는 밀리초(3자리)까지 자르고
  // 시간대를 붙여야 기기 시간대와 상관없이 같은 순간이 된다.
  const start = new Date(`${startedAt.slice(0, 23)}+09:00`).getTime()
  return <span>{formatElapsed(Math.max(0, now - start))} 스트리밍 중</span>
}

/** 24시간을 넘어도 시간을 계속 늘린다(25:00:00). */
function formatElapsed(ms: number) {
  const seconds = Math.floor(ms / 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(Math.floor(seconds / 3600))}:${pad(Math.floor((seconds % 3600) / 60))}:${pad(seconds % 60)}`
}
