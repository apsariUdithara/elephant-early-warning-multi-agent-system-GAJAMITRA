import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from './client'

// Server state for a page: loading, failed (with the status; 0 = unreachable, -1 = unexpected answer) or ready.
// Kept apart from UI state. `parse` checks the answer at runtime, so a page never renders an unchecked value.
export type Loaded<T> = { status: 'loading' } | { status: 'error'; code: number } | { status: 'ready'; data: T }

export function useApi<T>(path: string, parse: (value: unknown) => T): { state: Loaded<T>; reload: () => void } {
  const [attempt, setAttempt] = useState(0)
  // The answer is stored with the request it belongs to; an answer to an older request counts as still loading.
  const [answer, setAnswer] = useState<{ key: string; result: Loaded<T> } | null>(null)
  const key = `${path}#${attempt}`

  useEffect(() => {
    let current = true // ignore an answer that arrives after the page moved on
    api<unknown>(path)
      .then((value) => current && setAnswer({ key, result: { status: 'ready', data: parse(value) } }))
      .catch(
        (error: unknown) =>
          current && setAnswer({ key, result: { status: 'error', code: error instanceof ApiError ? error.status : -1 } }),
      )
    return () => {
      current = false
    }
  }, [path, parse, key])

  const reload = useCallback(() => setAttempt((n) => n + 1), [])
  return { state: answer?.key === key ? answer.result : { status: 'loading' }, reload }
}
