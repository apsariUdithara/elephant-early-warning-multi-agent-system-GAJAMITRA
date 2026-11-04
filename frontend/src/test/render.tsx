import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import App from '../App'
import { AuthProvider } from '../auth/Auth'
import { I18nProvider } from '../i18n/I18n'

// A fake backend: `{'GET /api/me': () => reply(200, {...})}`. An unlisted call is a 404, and every call is recorded.
export type Handler = () => Response
export const reply = (status: number, body?: unknown): Response =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

export interface Call {
  method: string
  path: string
  headers: Record<string, string>
  body: BodyInit | null | undefined
}

export function fakeBackend(routes: Record<string, Handler>) {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((path: string, init: RequestInit = {}) => {
      const method = init.method ?? 'GET'
      calls.push({ method, path, headers: (init.headers ?? {}) as Record<string, string>, body: init.body })
      const handler = routes[`${method} ${path}`]
      return Promise.resolve(handler ? handler() : reply(404))
    }),
  )
  return calls
}

export const OFFICER = { username: 'officer', role: 'OFFICER' }

// A stand-in for the browser's EventSource, so a test can open, drop and feed a live stream.
export class FakeEventSource {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2
  static instances: FakeEventSource[] = []
  readyState = 0
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  closed = false
  readonly url: string
  private listeners = new Map<string, (() => void)[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: () => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  close() {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }

  emit(type = 'analysis') {
    this.listeners.get(type)?.forEach((listener) => listener())
  }
}

export function stubEventSource(): typeof FakeEventSource {
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
  return FakeEventSource
}

export function renderApp(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <I18nProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </I18nProvider>
    </MemoryRouter>,
  )
}
