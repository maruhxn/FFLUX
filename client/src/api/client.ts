/**
 * API·미디어 서버 주소. 운영은 프론트(Vercel)와 출처가 달라 `VITE_API_ORIGIN`(예: https://api.fflux.kro.kr)으로 받는다.
 * 비어 있으면 같은 출처다(로컬 개발은 Vite가 /api·/media를 넘긴다).
 */
const API_ORIGIN: string = import.meta.env.VITE_API_ORIGIN ?? ''

/** 서버가 주는 절대 경로(`/api/...`, `/media/...`)를 API 서버 주소로 바꾼다. */
export const apiUrl = (path: string) => API_ORIGIN + path

/** ProblemDetail 응답을 담은 API 오류. */
export class ApiError extends Error {
  readonly status: number
  /** 요청 본문 검증 실패(400) 때 필드별 메시지. */
  readonly fieldErrors: Record<string, string>

  constructor(status: number, detail: string, fieldErrors: Record<string, string> = {}) {
    super(detail)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/** API 서버의 [path](`/api/...`)를 부르고 세션 쿠키에 기댄다. 출처가 달라도 쿠키를 싣는다. */
export async function api<T>(path: string, options: { method?: Method; body?: unknown } = {}): Promise<T> {
  const response = await fetch(apiUrl(path), {
    method: options.method ?? 'GET',
    headers: options.body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    credentials: 'include',
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new ApiError(response.status, problem?.detail ?? '요청을 처리하지 못했습니다.', problem?.errors ?? {})
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

/** 서버가 연결을 오류 응답으로 거절했을 때 다시 열기까지 기다리는 시간. */
const RECONNECT_MS = 5_000

/**
 * API 서버의 SSE [path]를 세션 쿠키를 실어 열고, 닫는 함수를 돌려준다.
 * EventSource는 네트워크가 끊기면 알아서 다시 연결하지만, 오류 응답(배포 중 앱이 내려가 Caddy가 주는 502 등)을 받으면
 * 닫힌 채 다시 연결하지 않는다. 그래서 닫히면 잠시 뒤 새로 연다. [listen]은 새로 열 때마다 불러 리스너를 붙인다.
 */
export function openEvents(path: string, listen: (source: EventSource) => void): () => void {
  let source: EventSource
  let retry: ReturnType<typeof setTimeout> | undefined
  const connect = () => {
    source = new EventSource(apiUrl(path), { withCredentials: true })
    listen(source)
    source.addEventListener('error', () => {
      if (source.readyState === EventSource.CLOSED) retry = setTimeout(connect, RECONNECT_MS)
    })
  }
  connect()
  return () => {
    clearTimeout(retry)
    source.close()
  }
}
