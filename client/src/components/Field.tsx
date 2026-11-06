import type { InputHTMLAttributes } from 'react'

export function Field({
  label,
  hint,
  error,
  ...input
}: { label: string; hint?: string; error?: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block space-y-1">
      <span className="text-sm text-neutral-300">{label}</span>
      <input
        {...input}
        className="w-full rounded-md border border-neutral-700 bg-neutral-900 px-3 py-2 text-neutral-100 outline-none focus:border-emerald-500"
      />
      {error ? (
        <span className="block text-xs text-red-400">{error}</span>
      ) : (
        hint && <span className="block text-xs text-neutral-500">{hint}</span>
      )}
    </label>
  )
}
