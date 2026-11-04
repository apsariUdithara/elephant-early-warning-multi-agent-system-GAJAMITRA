import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeBackend, OFFICER, renderApp, reply } from '../test/render'

const ANALYSES = [
  { id: 'a1', siteId: 's1', status: 'AWAITING_APPROVAL', risk: 'high', createdAt: '2026-11-04T21:35:11Z' },
  { id: 'a2', siteId: 's1', status: 'RUNNING', risk: null, createdAt: '2026-11-04T21:31:45Z' },
]

afterEach(() => vi.unstubAllGlobals())

describe('signing in', () => {
  it('sends a visitor without a session to the login page', async () => {
    fakeBackend({ 'GET /api/me': () => reply(401) })
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('signs in with the form, sends the CSRF token and shows the analyses', async () => {
    document.cookie = 'XSRF-TOKEN=tok'
    let signedIn = false
    const calls = fakeBackend({
      'GET /api/me': () => (signedIn ? reply(200, OFFICER) : reply(401)),
      'POST /api/login': () => {
        signedIn = true
        return reply(204)
      },
      'GET /api/analyses': () => reply(200, ANALYSES),
    })
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Username'), 'officer')
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('heading', { name: 'Analyses' })).toBeInTheDocument()
    const login = calls.find((c) => c.path === '/api/login')
    expect(login?.headers['X-XSRF-TOKEN']).toBe('tok')
    expect(String(login?.body)).toContain('username=officer')
  })

  it.each([
    [401, 'Wrong username or password.'],
    [429, 'Too many attempts. Try again in 15 minutes.'],
  ])('explains a %i answer and keeps the form', async (status, message) => {
    fakeBackend({ 'GET /api/me': () => reply(401), 'POST /api/login': () => reply(status) })
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Username'), 'officer')
    await userEvent.type(screen.getByLabelText('Password'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('says the server cannot be reached instead of "wrong password"', async () => {
    fakeBackend({ 'GET /api/me': () => reply(401) })
    renderApp('/login')
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('down'))))
    await userEvent.type(await screen.findByLabelText('Username'), 'officer')
    await userEvent.type(screen.getByLabelText('Password'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('cannot be reached')
  })

  it('offers a retry when the server cannot be reached at start-up', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('down'))))
    renderApp('/')
    expect(await screen.findByRole('alert')).toHaveTextContent('cannot be reached')
    fakeBackend({ 'GET /api/me': () => reply(200, OFFICER), 'GET /api/analyses': () => reply(200, []) })
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('There are no analyses yet.')).toBeInTheDocument()
  })
})

describe('leaving and coming back', () => {
  it('signs out with the CSRF token and returns to the login page', async () => {
    document.cookie = 'XSRF-TOKEN=tok'
    let signedIn = true
    const calls = fakeBackend({
      'GET /api/me': () => (signedIn ? reply(200, OFFICER) : reply(401)),
      'GET /api/analyses': () => reply(200, []),
      'POST /api/logout': () => {
        signedIn = false
        return reply(204)
      },
    })
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(calls.find((c) => c.path === '/api/logout')?.headers['X-XSRF-TOKEN']).toBe('tok')
  })

  it('stays signed in when the sign-out did not work, because the server still has the session', async () => {
    fakeBackend({
      'GET /api/me': () => reply(200, OFFICER),
      'GET /api/analyses': () => reply(200, []),
      'POST /api/logout': () => reply(403),
    })
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))
    expect(await screen.findByText('There are no analyses yet.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument()
  })

  it('signs in again after the session expired, and the list comes back', async () => {
    let valid = false
    let meCalls = 0
    fakeBackend({
      // At start the page still believes it is signed in; the first real call finds the session gone.
      'GET /api/me': () => (valid || meCalls++ === 0 ? reply(200, OFFICER) : reply(401)),
      'GET /api/analyses': () => (valid ? reply(200, ANALYSES) : reply(401)),
      'POST /api/login': () => {
        valid = true
        return reply(204)
      },
    })
    renderApp('/')
    await userEvent.type(await screen.findByLabelText('Username'), 'officer')
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Analysing')).toBeInTheDocument()
  })

  it('does not call a refused CSRF token "server unreachable"', async () => {
    fakeBackend({ 'GET /api/me': () => reply(401), 'POST /api/login': () => reply(403) })
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Username'), 'officer')
    await userEvent.type(screen.getByLabelText('Password'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Reload the page and try again.')
  })
})

describe('the analyses page', () => {
  const signedIn = { 'GET /api/me': () => reply(200, OFFICER) }

  it('shows status and risk as text, and says when a risk is not recorded yet', async () => {
    fakeBackend({ ...signedIn, 'GET /api/analyses': () => reply(200, ANALYSES) })
    renderApp('/')
    expect(await screen.findByText('Waiting for the officer')).toBeInTheDocument()
    expect(screen.getByText('High risk')).toBeInTheDocument() // text as well as colour
    expect(screen.getByText('Analysing')).toBeInTheDocument()
    expect(screen.getByText('Risk not assessed yet')).toBeInTheDocument()
  })

  it('does not call an empty list a failure, and a failure never reads as an empty list', async () => {
    fakeBackend({ ...signedIn, 'GET /api/analyses': () => reply(200, []) })
    renderApp('/')
    expect(await screen.findByText('There are no analyses yet.')).toBeInTheDocument()
    vi.unstubAllGlobals()
    fakeBackend({ ...signedIn, 'GET /api/analyses': () => reply(500) })
    renderApp('/')
    expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent('could not be loaded')
  })

  it('retries a failed load', async () => {
    let healthy = false
    fakeBackend({ ...signedIn, 'GET /api/analyses': () => (healthy ? reply(200, ANALYSES) : reply(500)) })
    renderApp('/')
    const retry = await screen.findByRole('button', { name: 'Try again' })
    healthy = true
    await userEvent.click(retry)
    expect(await screen.findByText('Analysing')).toBeInTheDocument()
  })

  it('refuses an answer that is not the shape the backend promised', async () => {
    fakeBackend({ ...signedIn, 'GET /api/analyses': () => reply(200, [{ id: 'a1', status: 'EXPLODED' }]) })
    renderApp('/')
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded')
  })

  it('returns to the login page when the session expires', async () => {
    fakeBackend({ ...signedIn, 'GET /api/analyses': () => reply(401) })
    renderApp('/')
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument())
  })
})

describe('languages', () => {
  it('switches the whole page to Sinhala or Tamil, keeps the choice and sets the document language', async () => {
    fakeBackend({ 'GET /api/me': () => reply(200, OFFICER), 'GET /api/analyses': () => reply(200, ANALYSES) })
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: 'සිංහල' }))
    expect(await screen.findByText('ඉහළ අවදානම')).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('si')
    expect(localStorage.getItem('gajamitra.lang')).toBe('si')
    await userEvent.click(screen.getByRole('button', { name: 'தமிழ்' }))
    expect(await screen.findByText('அதிக ஆபத்து')).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('ta')
  })

  it('starts in the saved language', async () => {
    localStorage.setItem('gajamitra.lang', 'ta')
    fakeBackend({ 'GET /api/me': () => reply(401) })
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'உள்நுழை' })).toBeInTheDocument()
  })
})
