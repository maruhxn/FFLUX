import type { FormEvent } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'

/** 서버가 받는 검색어 최대 길이(방송 제목 최대 길이와 같다). */
const MAX_LENGTH = 50

/** 방송·스트리머 검색창. 검색 화면(`/search?q=`)으로 간다. 검색 화면에서는 지금 검색어를 채워 둔다. */
export function SearchBox({ className = '' }: { className?: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const current = location.pathname === '/search' ? (params.get('q') ?? '') : ''

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const q = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    if (q) navigate(`/search?${new URLSearchParams({ q })}`)
  }

  return (
    <form role="search" onSubmit={submit} className={`flex min-w-0 ${className}`}>
      <input
        key={current}
        name="q"
        type="search"
        defaultValue={current}
        maxLength={MAX_LENGTH}
        placeholder="방송 제목, 스트리머 검색"
        aria-label="검색어"
        className="min-w-0 flex-1 rounded-l-md border border-neutral-700 bg-neutral-900 px-3 py-1.5 text-sm placeholder:text-neutral-500 focus:border-brand-500 focus:outline-none"
      />
      <button
        type="submit"
        aria-label="검색"
        className="rounded-r-md border border-l-0 border-neutral-700 bg-neutral-800 px-3 text-neutral-300 hover:bg-neutral-700"
      >
        <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        </svg>
      </button>
    </form>
  )
}
