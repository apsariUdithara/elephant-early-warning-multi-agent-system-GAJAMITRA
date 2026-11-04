import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, setSessionExpiredHandler } from '../api/client'
import { parseMe } from '../api/parse'
import { AuthContext, type Auth, type Session } from './useAuth'

// Asks the server who is signed in. The first call also makes Spring set the XSRF-TOKEN cookie that every later
// write sends back. A 401 means "nobody"; anything else means the server could not give an answer.
async function loadSession(): Promise<Session> {
  try {
    return { status: 'signedIn', me: parseMe(await api<unknown>('/api/me', { anonymousOk: true })) }
  } catch (error) {
    return error instanceof ApiError && error.status === 401 ? { status: 'anonymous' } : { status: 'unreachable' }
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>({ status: 'loading' })

  useEffect(() => {
    let current = true
    void loadSession().then((next) => current && setSession(next))
    setSessionExpiredHandler(() => setSession({ status: 'anonymous' }))
    return () => {
      current = false
      setSessionExpiredHandler(() => {}) // never leave a handler pointing at an unmounted provider
    }
  }, [])

  const value = useMemo<Auth>(
    () => ({
      session,
      login: async (username, password) => {
        await api<void>('/api/login', { method: 'POST', form: new URLSearchParams({ username, password }), anonymousOk: true })
        const next = await loadSession()
        setSession(next)
        return next
      },
      logout: async () => {
        try {
          await api<void>('/api/logout', { method: 'POST', anonymousOk: true })
        } catch {
          // fall through: ask the server what is true instead of assuming the session ended
        }
        setSession(await loadSession()) // a failed logout keeps the page signed in, as the server still is
      },
      retry: async () => setSession(await loadSession()),
    }),
    [session],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
