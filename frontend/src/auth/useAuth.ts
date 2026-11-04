import { createContext, useContext } from 'react'
import type { Me } from '../api/types'

// Who is signed in. `unreachable` is not "signed out": the page says the server cannot be reached and offers a retry.
export type Session =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'unreachable' }
  | { status: 'signedIn'; me: Me }

export interface Auth {
  session: Session
  /** Throws ApiError: 401 wrong credentials, 429 locked, 0 server unreachable. */
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  retry: () => Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)

export function useAuth(): Auth {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth needs an AuthProvider')
  return value
}
