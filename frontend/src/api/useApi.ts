import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from './client'

// Server state for a page: loading, failed (with the status; 0 = unreachable, -1 = unexpected answer) or ready.
// Kept apart from UI state. `parse` checks the answer at runtime, so a page never renders an unchecked value.
// While a reload is running, the last answer for the same path stays on screen (no flicker, nothing typed is lost).
// If that reload fails, the old answer stays but is marked `stale`, so the page can say it may be out of date.
export type Loaded<T> =
  | { status: 'loading' }
  | { status: 'error'; code: number }
  | { status: 'ready'; data: T; at: number; stale: boolean }

export function useApi<T>(path: string, parse: (value: unknown) => T): { state: Loaded<T>; reload: () => void } {
  const [attempt, setAttempt] = useState(0)
  // The answer is stored with the request it belongs to; an answer to an older request is not shown as the new one.
  const [answer, setAnswer] = useState<{ path: string; key: string; result: Loaded<T> } | null>(null)
  const key = `${path}#${attempt}`

  useEffect(() => {
    let current = true // ignore an answer that arrives after the page moved on
    api<unknown>(path)
      .then((value) => {
        if (current) setAnswer({ path, key, result: { status: 'ready', data: parse(value), at: Date.now(), stale: false } })
      })
      .catch((error: unknown) => {
        if (!current) return
        const code = error instanceof ApiError ? error.status : -1
        setAnswer((previous) =>
          previous && previous.path === path && previous.result.status === 'ready'
            ? { path, key, result: { ...previous.result, stale: true } }
            : { path, key, result: { status: 'error', code } },
        )
      })
    return () => {
      current = false
    }
  }, [path, parse, key])

  const reload = useCallback(() => setAttempt((n) => n + 1), [])
  let state: Loaded<T> = { status: 'loading' }
  if (answer?.key === key) state = answer.result
  else if (answer?.path === path && answer.result.status === 'ready') state = answer.result // refreshing
  return { state, reload }
}
