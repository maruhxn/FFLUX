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
