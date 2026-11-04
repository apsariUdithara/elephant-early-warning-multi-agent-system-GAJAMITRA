// The one place that talks to the backend. Same origin (Vite proxies /api in development), session cookie, and the
// CSRF token Spring sets in the XSRF-TOKEN cookie sent back as X-XSRF-TOKEN on every write (plan Phase 3, Security).

/** A failed call. `status` 0 means the server could not be reached at all. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, detail: string) {
    super(detail)
    this.status = status
  }
}

function xsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

// Called when a session has expired (a 401 on a call that needed one), so the app can show the login page.
let onSessionExpired: () => void = () => {}
export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler
}

interface Options {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  json?: unknown
  form?: URLSearchParams | FormData
  /** The call is how the page finds out whether there is a session, so a 401 is an answer, not an expiry. */
  anonymousOk?: boolean
}

async function problemDetail(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (typeof body === 'object' && body !== null && 'detail' in body && typeof body.detail === 'string') {
      return body.detail
    }
  } catch {
    // not a ProblemDetail body
  }
  return response.statusText || `HTTP ${response.status}`
}

export async function api<T>(path: string, options: Options = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  let body: BodyInit | undefined
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.json)
  } else if (options.form) {
    body = options.form
  }
  if (method !== 'GET') {
    const token = xsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  let response: Response
  try {
    response = await fetch(path, { method, headers, body, credentials: 'same-origin' })
  } catch {
    throw new ApiError(0, 'unreachable')
  }
  if (response.status === 401 && !options.anonymousOk) onSessionExpired()
  if (!response.ok) throw new ApiError(response.status, await problemDetail(response))
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}
