import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useLogin, useTestLogin } from '../auth/auth'
import { Field } from '../components/Field'

export function LoginPage() {
  const navigate = useNavigate()
  const login = useLogin()
  const testLogin = useTestLogin()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const error = login.error ?? testLogin.error

  function loginAsTestAccount(account: 'streamer' | 'viewer') {
    login.reset()
    testLogin.mutate(account, { onSuccess: () => navigate('/') })
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    testLogin.reset()
    login.mutate({ username, password }, { onSuccess: () => navigate('/') })
  }

  return (
    <div className="mx-auto max-w-sm space-y-6">
      <h1 className="text-2xl font-bold">로그인</h1>

      <section className="space-y-2 rounded-lg border border-emerald-800 bg-emerald-950/40 p-4">
        <p className="text-sm text-emerald-200">평가용 테스트 계정으로 바로 로그인할 수 있습니다.</p>
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            disabled={testLogin.isPending}
            onClick={() => loginAsTestAccount('streamer')}
            className="rounded-md bg-emerald-500 px-3 py-2 text-sm font-semibold text-neutral-950 disabled:opacity-50"
          >
            스트리머로 로그인
          </button>
          <button
            type="button"
            disabled={testLogin.isPending}
            onClick={() => loginAsTestAccount('viewer')}
            className="rounded-md border border-emerald-500 px-3 py-2 text-sm font-semibold text-emerald-300 disabled:opacity-50"
          >
            시청자로 로그인
          </button>
        </div>
      </section>

      <form onSubmit={onSubmit} className="space-y-4">
        <Field label="아이디" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
        <Field
          label="비밀번호"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />
        {error && <p className="text-sm text-red-400">{error.message}</p>}
        <button
          type="submit"
          disabled={login.isPending}
          className="w-full rounded-md bg-neutral-100 px-3 py-2 font-semibold text-neutral-950 disabled:opacity-50"
        >
          로그인
        </button>
      </form>

      <p className="text-center text-sm text-neutral-400">
        계정이 없나요?{' '}
        <Link to="/signup" className="text-emerald-400 underline">
          회원가입
        </Link>
      </p>
    </div>
  )
}
