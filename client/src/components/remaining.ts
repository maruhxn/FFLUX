import { useEffect, useState } from 'react'

/** 1초마다 바뀌는 지금 시각(ms). 남은 시간을 줄여 보여 줄 때 쓴다. */
export function useNow() {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return now
}

/** 남은 시간을 `m:ss`, 1시간부터는 `h:mm:ss`로. 남은 초는 올림해 0이 되기 전까지 0:00을 보이지 않는다. */
export function formatRemaining(ms: number) {
  const seconds = Math.max(0, Math.ceil(ms / 1000))
  const pad = (n: number) => String(n).padStart(2, '0')
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`
}
