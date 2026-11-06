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

/** same-origin 상대 경로(`/api/...`)로 호출하고 세션 쿠키에 기댄다. */
export async function api<T>(path: string, options: { method?: Method; body?: unknown } = {}): Promise<T> {
  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers: options.body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    credentials: 'same-origin',
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new ApiError(response.status, problem?.detail ?? '요청을 처리하지 못했습니다.', problem?.errors ?? {})
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
