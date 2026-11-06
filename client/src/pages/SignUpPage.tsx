import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useLogin, useSignUp } from '../auth/auth'
import { Field } from '../components/Field'

export function SignUpPage() {
  const navigate = useNavigate()
  const signUp = useSignUp()
  const login = useLogin()
  const [form, setForm] = useState({ username: '', password: '', nickname: '' })
  const error = signUp.error ?? login.error
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    signUp.mutate(form, {
      // 가입 후 바로 로그인한다.
      onSuccess: () =>
        login.mutate({ username: form.username, password: form.password }, { onSuccess: () => navigate('/') }),
    })
  }

  return (
    <div className="mx-auto max-w-sm space-y-6">
      <h1 className="text-2xl font-bold">회원가입</h1>
      <form onSubmit={onSubmit} className="space-y-4">
        <Field
          label="아이디"
          hint="영문 소문자, 숫자, _ 4~20자"
          error={fieldErrors.username}
          value={form.username}
          onChange={(e) => setForm({ ...form, username: e.target.value })}
          autoComplete="username"
          required
        />
        <Field
          label="비밀번호"
          hint="8~64자"
          error={fieldErrors.password}
          type="password"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
          autoComplete="new-password"
          required
        />
        <Field
          label="닉네임"
          hint="2~12자"
          error={fieldErrors.nickname}
          value={form.nickname}
          onChange={(e) => setForm({ ...form, nickname: e.target.value })}
          required
        />
        {error && Object.keys(fieldErrors).length === 0 && <p className="text-sm text-red-400">{error.message}</p>}
        <button
          type="submit"
          disabled={signUp.isPending || login.isPending}
          className="w-full rounded-md bg-emerald-500 px-3 py-2 font-semibold text-neutral-950 disabled:opacity-50"
        >
          가입하기
        </button>
      </form>
    </div>
  )
}
