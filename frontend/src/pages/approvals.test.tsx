import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeBackend, OFFICER, renderApp, reply } from '../test/render'

const inSeconds = (s: number) => new Date(Date.now() + s * 1000).toISOString()
const approval = (over: object = {}) => ({
  id: 'p1',
  analysisId: 'a1',
  status: 'PENDING',
  expiresAt: inSeconds(90),
  serverNow: new Date().toISOString(), // the server's clock when the list was read
  proposedAlerts: [
    { severity: 'danger', en: 'DANGER: 8 elephants at the fence. Go indoors now.', si: 'අලි අනතුර', ta: 'யானை ஆபத்து' },
  ],
  ...over,
})
const signedIn = { 'GET /api/me': () => reply(200, OFFICER) }

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('the approvals queue', () => {
  it('says the warning has already gone out, shows what would be sent, and counts down to the automatic send', async () => {
    fakeBackend({ ...signedIn, 'GET /api/approvals': () => reply(200, [approval()]) })
    renderApp('/approvals')
    expect(await screen.findByText(/DANGER: 8 elephants at the fence/)).toBeInTheDocument()
    expect(screen.getByText(/warning for each analysis below has already been sent/)).toBeInTheDocument()
    expect(screen.getByText(/අලි අනතුර/).closest('p')).toHaveAttribute('lang', 'si')
    expect(screen.getByText(/Sent automatically in 1:\d\d if nobody decides\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View the analysis' })).toHaveAttribute('href', '/analyses/a1')
  })

  it('says so when the time has run out: the escalation is being sent anyway', async () => {
    fakeBackend({ ...signedIn, 'GET /api/approvals': () => reply(200, [approval({ expiresAt: inSeconds(-5) })]) })
    renderApp('/approvals')
    expect(
      await screen.findByText('Time limit reached. The escalation is sent automatically; refreshing the list…'),
    ).toBeInTheDocument()
  })

  it('does not call an empty queue a failure, and a failure never reads as an empty queue', async () => {
    fakeBackend({ ...signedIn, 'GET /api/approvals': () => reply(200, []) })
    renderApp('/approvals')
    expect(await screen.findByText('No escalation is waiting for approval.')).toBeInTheDocument()
    vi.unstubAllGlobals()
    fakeBackend({ ...signedIn, 'GET /api/approvals': () => reply(500) })
    renderApp('/approvals')
    expect((await screen.findAllByRole('button', { name: 'Try again' })).length).toBeGreaterThan(0)
  })

  it('asks for confirmation, then sends the decision once with the CSRF token and the note', async () => {
    document.cookie = 'XSRF-TOKEN=tok'
    let decided = false
    const calls = fakeBackend({
      ...signedIn,
      'GET /api/approvals': () => reply(200, decided ? [] : [approval()]),
      'POST /api/approvals/p1': () => {
        decided = true
        return reply(204)
      },
    })
    renderApp('/approvals')
    await userEvent.type(await screen.findByLabelText('Note (optional)'), 'herd at the fence')
    await userEvent.click(screen.getByRole('button', { name: 'Approve and send now' }))
    expect(screen.getByText('Send this escalation to the villagers now?')).toBeInTheDocument()
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(0) // nothing is sent until it is confirmed
    await userEvent.dblClick(screen.getByRole('button', { name: 'Yes, confirm' })) // a double click is one decision
    expect(await screen.findByText('No escalation is waiting for approval.')).toBeInTheDocument()
    expect(screen.getByText('Decision saved.')).toBeInTheDocument() // still on screen after the card is gone
    const posts = calls.filter((c) => c.method === 'POST')
    expect(posts).toHaveLength(1)
    expect(posts[0].headers['X-XSRF-TOKEN']).toBe('tok')
    expect(JSON.parse(String(posts[0].body))).toEqual({ decision: 'APPROVE', note: 'herd at the fence' })
  })

  it('lets an officer cancel at the confirmation: nothing is sent', async () => {
    const calls = fakeBackend({ ...signedIn, 'GET /api/approvals': () => reply(200, [approval()]) })
    renderApp('/approvals')
    await userEvent.click(await screen.findByRole('button', { name: 'Do not send' }))
    expect(screen.getByText(/The warning that was already sent stays in place/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByRole('button', { name: 'Approve and send now' })).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('sends a rejection as a rejection', async () => {
    const calls = fakeBackend({
      ...signedIn,
      'GET /api/approvals': () => reply(200, [approval()]),
      'POST /api/approvals/p1': () => reply(204),
    })
    renderApp('/approvals')
    await userEvent.click(await screen.findByRole('button', { name: 'Do not send' }))
    await userEvent.click(screen.getByRole('button', { name: 'Yes, confirm' }))
    await screen.findByText('Decision saved.')
    expect(JSON.parse(String(calls.find((c) => c.method === 'POST')?.body))).toEqual({ decision: 'REJECT', note: null })
  })

  it('explains a 409: it was already decided or the time ran out, and the list is refreshed', async () => {
    let gets = 0
    fakeBackend({
      ...signedIn,
      'GET /api/approvals': () => reply(200, gets++ === 0 ? [approval()] : []),
      'POST /api/approvals/p1': () => reply(409, { detail: 'already decided, timed out or unknown' }),
    })
    renderApp('/approvals')
    await userEvent.click(await screen.findByRole('button', { name: 'Approve and send now' }))
    await userEvent.click(screen.getByRole('button', { name: 'Yes, confirm' }))
    expect(await screen.findByText('No escalation is waiting for approval.')).toBeInTheDocument()
    expect(screen.getByText(/already decided or the time ran out/)).toBeInTheDocument()
  })

  it('keeps the decision form and says it failed when saving did not work', async () => {
    fakeBackend({
      ...signedIn,
      'GET /api/approvals': () => reply(200, [approval()]),
      'POST /api/approvals/p1': () => reply(500),
    })
    renderApp('/approvals')
    await userEvent.click(await screen.findByRole('button', { name: 'Approve and send now' }))
    await userEvent.click(screen.getByRole('button', { name: 'Yes, confirm' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The decision could not be saved. Try again.')
    expect(screen.getByRole('button', { name: 'Approve and send now' })).toBeEnabled()
  })

  it('counts down with the server clock, so a wrong browser clock cannot fake "time ran out"', async () => {
    // The browser's clock is an hour ahead of the server's: by its own clock the expiry is long past.
    const serverNow = Date.now() - 3_600_000
    fakeBackend({
      ...signedIn,
      'GET /api/approvals': () =>
        reply(200, [approval({ serverNow: new Date(serverNow).toISOString(), expiresAt: new Date(serverNow + 90_000).toISOString() })]),
    })
    renderApp('/approvals')
    expect(await screen.findByText(/Sent automatically in 1:\d\d if nobody decides\./)).toBeInTheDocument()
    expect(screen.queryByText(/Time limit reached/)).not.toBeInTheDocument()
  })

  it('does not offer to approve text the officer cannot see, but still lets them decide not to send', async () => {
    fakeBackend({ ...signedIn, 'GET /api/approvals': () => reply(200, [approval({ proposedAlerts: null })]) })
    renderApp('/approvals')
    expect(await screen.findByRole('button', { name: 'Approve and send now' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Do not send' })).toBeEnabled()
    expect(screen.getByText(/If nobody decides, it is sent automatically\./)).toBeInTheDocument()
  })

  it('says the proposed text could not be shown instead of showing half of it', async () => {
    fakeBackend({
      ...signedIn,
      'GET /api/approvals': () => reply(200, [approval({ proposedAlerts: [{ severity: 'loud' }] })]),
    })
    renderApp('/approvals')
    expect(await screen.findByText('The proposed text could not be shown. Open the analysis.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View the analysis' })).toBeInTheDocument()
  })

  it('keeps the queue on screen when a refresh fails, and says it may be out of date', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    let healthy = true
    fakeBackend({ ...signedIn, 'GET /api/approvals': () => (healthy ? reply(200, [approval()]) : reply(500)) })
    renderApp('/approvals')
    expect(await screen.findByText(/DANGER: 8 elephants/)).toBeInTheDocument()
    healthy = false
    await act(async () => {
      vi.advanceTimersByTime(10_000) // the queue refreshes itself every 10 seconds
    })
    expect(await screen.findByText('Reconnecting. What you see may be out of date.')).toBeInTheDocument()
    expect(screen.getByText(/DANGER: 8 elephants/)).toBeInTheDocument()
  })
})
