import { act, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeBackend, OFFICER, renderApp, reply, stubEventSource } from '../test/render'

const BASE = { id: 'a1', siteId: 's1', status: 'COMPLETED', risk: 'high', createdAt: '2026-11-04T21:35:11Z' }
const ALERT = {
  stage: 'NOW',
  severity: 'warning',
  shadow: true,
  createdAt: '2026-11-04T21:35:12Z',
  en: 'Elephant warning: 8 elephants near Galgamuwa.',
  si: 'අලි අනතුරු ඇඟවීම',
  ta: 'யானை எச்சரிக்கை',
  deliveries: { SKIPPED_SHADOW: 2, FAILED: 1 },
}
const detail = (over: object = {}) => ({
  ...BASE,
  failureReason: null,
  result: { notes: ['verifier: approve', 'high risk: lowered the alert'] },
  alerts: [ALERT],
  officerReport: 'Eight elephants including a juvenile.',
  ...over,
})
const signedIn = { 'GET /api/me': () => reply(200, OFFICER) }

afterEach(() => vi.unstubAllGlobals())

describe('the analysis page', () => {
  it('shows what was sent in three languages, whether it was a test, and each SMS state as text', async () => {
    stubEventSource()
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(200, detail()) })
    renderApp('/analyses/a1')
    expect(await screen.findByText('Completed')).toBeInTheDocument()
    expect(screen.getByText('High risk')).toBeInTheDocument()
    expect(screen.getByText('Test mode: stored, not sent to phones')).toBeInTheDocument()
    expect(screen.getByText(/Elephant warning: 8 elephants/).closest('p')).toHaveAttribute('lang', 'en')
    expect(screen.getByText(/අලි අනතුරු ඇඟවීම/).closest('p')).toHaveAttribute('lang', 'si')
    expect(screen.getByText(/யானை எச்சரிக்கை/).closest('p')).toHaveAttribute('lang', 'ta')
    expect(screen.getByText(/Not sent \(test mode\)/)).toBeInTheDocument()
    expect(screen.getByText(/^Failed:/)).toBeInTheDocument() // a failed SMS is never shown as sent
    expect(screen.getByText('Eight elephants including a juvenile.')).toBeInTheDocument()
    expect(screen.getByText('high risk: lowered the alert')).toBeInTheDocument()
  })

  it('shows alert and failure text written by the AI service as plain text, never as HTML', async () => {
    stubEventSource()
    const hostile = '<img src=x onerror="alert(1)"><b>bold</b>'
    fakeBackend({
      ...signedIn,
      'GET /api/analyses/a1': () =>
        reply(200, detail({ status: 'FAILED', failureReason: hostile, alerts: [{ ...ALERT, en: hostile }] })),
    })
    const { container } = renderApp('/analyses/a1')
    expect(await screen.findByText('Failed')).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
    expect(screen.getAllByText(/<img src=x/).length).toBe(2)
  })

  it('follows live changes: an event re-reads the analysis', async () => {
    const stream = stubEventSource()
    let status = 'RUNNING'
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(200, detail({ status, alerts: [] })) })
    renderApp('/analyses/a1')
    expect(await screen.findByText('Analysing')).toBeInTheDocument()
    expect(stream.instances[0].url).toBe('/api/analyses/a1/events')
    status = 'COMPLETED'
    act(() => stream.instances[0].emit())
    expect(await screen.findByText('Completed')).toBeInTheDocument()
  })

  it('says when the live connection drops, and when it is back', async () => {
    const stream = stubEventSource()
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(200, detail()) })
    renderApp('/analyses/a1')
    expect(await screen.findByText('Connecting…')).toBeInTheDocument()
    act(() => stream.instances[0].onopen?.())
    expect(await screen.findByText('Live updates on')).toBeInTheDocument()
    act(() => stream.instances[0].onerror?.())
    expect(await screen.findByText('Reconnecting. What you see may be out of date.')).toBeInTheDocument()
  })

  it('says live updates stopped when the stream was refused, instead of "reconnecting" forever', async () => {
    const stream = stubEventSource()
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(200, detail()) })
    renderApp('/analyses/a1')
    await screen.findByText('Completed')
    act(() => {
      stream.instances[0].readyState = stream.CLOSED // the browser gave up: it will not retry
      stream.instances[0].onerror?.()
    })
    expect(await screen.findByText('Live updates stopped. Reload the page to continue.')).toBeInTheDocument()
  })

  it('closes the live stream when the page is left', async () => {
    const stream = stubEventSource()
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(200, detail()) })
    const { unmount } = renderApp('/analyses/a1')
    await screen.findByText('Completed')
    unmount()
    expect(stream.instances.every((s) => s.closed)).toBe(true)
  })

  it('points an officer to the approvals queue when an escalation is waiting', async () => {
    stubEventSource()
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(200, detail({ status: 'AWAITING_APPROVAL' })) })
    renderApp('/analyses/a1')
    expect(await screen.findByText('An escalation is waiting for an officer.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open the approvals queue' })).toHaveAttribute('href', '/approvals')
  })

  it('says an unknown analysis does not exist, and keeps a real failure retryable', async () => {
    stubEventSource()
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(404) })
    renderApp('/analyses/a1')
    expect(await screen.findByRole('alert')).toHaveTextContent('This analysis does not exist.')
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
    vi.unstubAllGlobals()
    stubEventSource()
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(500) })
    renderApp('/analyses/a1')
    expect((await screen.findAllByRole('button', { name: 'Try again' })).length).toBeGreaterThan(0)
  })

  it('refuses a detail that is not the promised shape', async () => {
    stubEventSource()
    fakeBackend({ ...signedIn, 'GET /api/analyses/a1': () => reply(200, { ...detail(), alerts: [{ severity: 'loud' }] }) })
    renderApp('/analyses/a1')
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded')
  })
})
