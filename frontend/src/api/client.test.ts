import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, setSessionExpiredHandler } from './client'
import { fakeBackend, reply } from '../test/render'

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    setSessionExpiredHandler(() => {})
  })

  it('sends the CSRF token on writes only', async () => {
    document.cookie = 'XSRF-TOKEN=abc%20123'
    const calls = fakeBackend({ 'GET /api/x': () => reply(200, {}), 'POST /api/x': () => reply(204) })
    await api('/api/x')
    await api('/api/x', { method: 'POST', json: { a: 1 } })
    expect(calls[0].headers['X-XSRF-TOKEN']).toBeUndefined()
    expect(calls[1].headers['X-XSRF-TOKEN']).toBe('abc 123')
    expect(calls[1].headers['Content-Type']).toBe('application/json')
  })

  it('reports an expired session, but not for the call that asks whether there is one', async () => {
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    fakeBackend({ 'GET /api/secret': () => reply(401), 'GET /api/me': () => reply(401) })
    await expect(api('/api/secret')).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledOnce()
    await expect(api('/api/me', { anonymousOk: true })).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledOnce()
  })

  it('says 0 when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('network down'))))
    await expect(api('/api/x')).rejects.toEqual(expect.objectContaining({ status: 0 }))
  })

  it('uses the ProblemDetail text of a failure', async () => {
    fakeBackend({ 'POST /api/x': () => reply(409, { detail: 'already decided' }) })
    const error = await api('/api/x', { method: 'POST' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, message: 'already decided' })
  })
})
