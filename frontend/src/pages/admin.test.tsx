import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeBackend, OFFICER, renderApp, reply } from '../test/render'

const ADMIN = { username: 'admin', role: 'ADMIN' }
const SITE = {
  id: 's1',
  name: 'Galgamuwa',
  mode: 'SHADOW',
  villages: 'Ehetuwewa',
  officerContact: 'DWC Galgamuwa',
  requiresApproval: true,
  approvalTimeoutS: 120,
}
const asAdmin = { 'GET /api/me': () => reply(200, ADMIN) }

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('admin: who can see it', () => {
  it('shows the Admin link and the sites to an admin', async () => {
    fakeBackend({ ...asAdmin, 'GET /api/analyses': () => reply(200, []), 'GET /api/site-options': () => reply(200, []), 'GET /api/sites': () => reply(200, [SITE]) })
    renderApp('/admin')
    expect(await screen.findByText('Galgamuwa')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Admin' })).toBeInTheDocument()
    expect(screen.getByText('test mode')).toBeInTheDocument() // the mode is text, not only a colour
    expect(screen.getByText('Escalations wait for an officer, up to the time limit')).toBeInTheDocument()
  })

  it('shows an officer neither the link nor the pages (the server refuses them as well)', async () => {
    const calls = fakeBackend({ 'GET /api/me': () => reply(200, OFFICER), 'GET /api/analyses': () => reply(200, []), 'GET /api/site-options': () => reply(200, []) })
    renderApp('/admin')
    expect(await screen.findByRole('alert')).toHaveTextContent('You are not allowed to see this.')
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
    expect(calls.some((c) => c.path === '/api/sites')).toBe(false) // nothing admin-only is even requested
  })
})

describe('admin: sites', () => {
  it('creates a site with the CSRF token, and says it was saved', async () => {
    document.cookie = 'XSRF-TOKEN=tok'
    let created = false
    const calls = fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, created ? [SITE] : []),
      'POST /api/sites': () => {
        created = true
        return reply(201, SITE)
      },
    })
    renderApp('/admin')
    expect(await screen.findByText('There are no sites yet. Create the first one below.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Name'), 'Galgamuwa')
    await userEvent.click(screen.getAllByRole('button', { name: 'Create a site' })[0])
    expect(await screen.findByText('Saved.')).toBeInTheDocument()
    expect(await screen.findByText('test mode')).toBeInTheDocument() // the list was refreshed
    const post = calls.find((c) => c.method === 'POST')!
    expect(post.headers['X-XSRF-TOKEN']).toBe('tok')
    expect(JSON.parse(String(post.body))).toMatchObject({ name: 'Galgamuwa', requiresApproval: true, approvalTimeoutS: 120 })
  })

  it('shows why the server did not accept a site (the server states the reason)', async () => {
    fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, []),
      'POST /api/sites': () => reply(400, { detail: 'invalid request' }),
    })
    renderApp('/admin')
    await userEvent.type(await screen.findByLabelText('Name'), 'X')
    await userEvent.click(screen.getAllByRole('button', { name: 'Create a site' })[0])
    expect(await screen.findByText('Not accepted: invalid request')).toBeInTheDocument()
  })

  it('does not call a failed list "no sites"', async () => {
    fakeBackend({ ...asAdmin, 'GET /api/sites': () => reply(500) })
    renderApp('/admin')
    expect((await screen.findAllByRole('button', { name: 'Try again' })).length).toBeGreaterThan(0)
    expect(screen.queryByText('There are no sites yet. Create the first one below.')).not.toBeInTheDocument()
  })
})

describe('admin: one site', () => {
  const villagers = [{ id: 'v1', phone: '+94771234567', language: 'SI' }]
  const site = (over: object = {}) => ({ ...SITE, ...over })

  it('switches to LIVE only after saying plainly that villagers will receive real SMS, and asking first', async () => {
    let mode = 'SHADOW'
    const calls = fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, [site({ mode })]),
      'GET /api/sites/s1/subscribers': () => reply(200, villagers),
      'PUT /api/sites/s1/mode': () => {
        mode = 'LIVE'
        return reply(200, site({ mode }))
      },
    })
    renderApp('/admin/sites/s1')
    await userEvent.click(await screen.findByRole('button', { name: 'Switch to live' }))
    expect(screen.getByText('Villagers will receive real SMS alerts from this site. Switch to live?')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false) // nothing changes until it is confirmed
    await userEvent.click(screen.getByRole('button', { name: 'Yes, confirm' }))
    expect(await screen.findByRole('button', { name: 'Switch to test mode' })).toBeInTheDocument()
    expect(screen.getByText(/This site is LIVE: villagers receive real SMS alerts/)).toBeInTheDocument()
    expect(JSON.parse(String(calls.find((c) => c.method === 'PUT')?.body))).toEqual({ mode: 'LIVE' })
  })

  it('lets the admin cancel the switch: nothing is sent', async () => {
    const calls = fakeBackend({ ...asAdmin, 'GET /api/sites': () => reply(200, [SITE]), 'GET /api/sites/s1/subscribers': () => reply(200, []) })
    renderApp('/admin/sites/s1')
    await userEvent.click(await screen.findByRole('button', { name: 'Switch to live' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByRole('button', { name: 'Switch to live' })).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('says which mode is now active after a switch, and moves focus to that message', async () => {
    let mode = 'SHADOW'
    fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, [site({ mode })]),
      'GET /api/sites/s1/subscribers': () => reply(200, []),
      'PUT /api/sites/s1/mode': () => {
        mode = 'LIVE'
        return reply(200, site({ mode }))
      },
    })
    renderApp('/admin/sites/s1')
    await userEvent.click(await screen.findByRole('button', { name: 'Switch to live' }))
    await userEvent.click(screen.getByRole('button', { name: 'Yes, confirm' }))
    const status = await screen.findByText('The mode is now: live.')
    expect(status.closest('[role="status"]')).toHaveFocus()
  })

  it('returns focus to the button that opened the question when it is cancelled', async () => {
    fakeBackend({ ...asAdmin, 'GET /api/sites': () => reply(200, [SITE]), 'GET /api/sites/s1/subscribers': () => reply(200, []) })
    renderApp('/admin/sites/s1')
    await userEvent.click(await screen.findByRole('button', { name: 'Switch to live' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    await vi.waitFor(() => expect(screen.getByRole('button', { name: 'Switch to live' })).toHaveFocus())
  })

  it('warns that the site view may be out of date when a refresh fails (the mode decides whether real SMS go out)', async () => {
    let healthy = true
    fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => (healthy ? reply(200, [SITE]) : reply(500)),
      'GET /api/sites/s1/subscribers': () => reply(200, []),
      'PUT /api/sites/s1': () => reply(200, SITE),
    })
    renderApp('/admin/sites/s1')
    await screen.findByText('test mode')
    healthy = false
    await userEvent.click(screen.getByRole('button', { name: 'Save' })) // the reload after saving fails
    expect(await screen.findByText('Reconnecting. What you see may be out of date.')).toBeInTheDocument()
    healthy = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await vi.waitFor(() => expect(screen.queryByText('Reconnecting. What you see may be out of date.')).not.toBeInTheDocument())
  })

  it('tells a clash (409) from a rejection (400) and from a failure', async () => {
    let status = 409
    fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, [SITE]),
      'GET /api/sites/s1/subscribers': () => reply(200, []),
      'POST /api/sites/s1/subscribers': () => reply(status, { detail: 'x' }),
    })
    renderApp('/admin/sites/s1')
    await userEvent.type(await screen.findByLabelText(/Phone number/), '+94779999999')
    await userEvent.click(screen.getByRole('button', { name: 'Add the villager' }))
    expect(await screen.findByText(/clashes with existing data/)).toBeInTheDocument()
    status = 404
    await userEvent.click(screen.getByRole('button', { name: 'Add the villager' }))
    expect(await screen.findByText(/no longer exists/)).toBeInTheDocument()
    status = 500
    await userEvent.click(screen.getByRole('button', { name: 'Add the villager' }))
    expect(await screen.findByText('This could not be saved. Try again.')).toBeInTheDocument()
  })

  it('saves the site settings', async () => {
    const calls = fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, [SITE]),
      'GET /api/sites/s1/subscribers': () => reply(200, []),
      'PUT /api/sites/s1': () => reply(200, SITE),
    })
    renderApp('/admin/sites/s1')
    const timeout = await screen.findByLabelText(/Seconds before an unapproved escalation is sent/)
    await userEvent.clear(timeout)
    await userEvent.type(timeout, '90')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Saved.')).toBeInTheDocument()
    expect(JSON.parse(String(calls.find((c) => c.method === 'PUT')?.body))).toMatchObject({ approvalTimeoutS: 90, name: 'Galgamuwa' })
  })

  it('lists the villagers with their language, adds one, and says when a number is not accepted', async () => {
    let list = villagers
    let accept = false
    const calls = fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, [SITE]),
      'GET /api/sites/s1/subscribers': () => reply(200, list),
      'POST /api/sites/s1/subscribers': () => {
        if (!accept) return reply(400, { detail: 'phone must be in international format, e.g. +94771234567' })
        list = [...villagers, { id: 'v2', phone: '+94779999999', language: 'TA' }]
        return reply(201, list[1])
      },
    })
    renderApp('/admin/sites/s1')
    expect(await screen.findByText('+94771234567')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/Phone number/), '0771234567')
    await userEvent.click(screen.getByRole('button', { name: 'Add the villager' }))
    expect(await screen.findByText(/Not accepted: phone must be in international format/)).toBeInTheDocument()
    accept = true
    await userEvent.clear(screen.getByLabelText(/Phone number/))
    await userEvent.type(screen.getByLabelText(/Phone number/), '+94779999999')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Language' }), 'TA')
    await userEvent.click(screen.getByRole('button', { name: 'Add the villager' }))
    expect(await screen.findByText('+94779999999')).toBeInTheDocument()
    expect(screen.getByText('The villager was added.')).toBeInTheDocument()
    expect(JSON.parse(String(calls.filter((c) => c.method === 'POST').at(-1)?.body))).toEqual({ phone: '+94779999999', language: 'TA' })
  })

  it('removes a villager only after confirming, and says the past SMS records are kept without the number', async () => {
    let list = villagers
    const calls = fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, [SITE]),
      'GET /api/sites/s1/subscribers': () => reply(200, list),
      'DELETE /api/sites/s1/subscribers/v1': () => {
        list = []
        return reply(204)
      },
    })
    renderApp('/admin/sites/s1')
    const row = (await screen.findByText('+94771234567')).closest('li')!
    await userEvent.click(within(row).getByRole('button', { name: 'Remove +94771234567' }))
    expect(screen.getByText('Remove this villager? Their past SMS records are kept without the phone number.')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Yes, confirm' }))
    expect(await screen.findByText('No villagers yet.')).toBeInTheDocument()
    expect(screen.getByText('The villager was removed.')).toBeInTheDocument() // the result stays on the page
  })

  it('explains a refused removal (an SMS to this villager is still being sent) and keeps the villager', async () => {
    fakeBackend({
      ...asAdmin,
      'GET /api/sites': () => reply(200, [SITE]),
      'GET /api/sites/s1/subscribers': () => reply(200, villagers),
      'DELETE /api/sites/s1/subscribers/v1': () => reply(400, { detail: 'an SMS to this villager is still being sent; try again in a few minutes' }),
    })
    renderApp('/admin/sites/s1')
    await userEvent.click(await screen.findByRole('button', { name: 'Remove +94771234567' }))
    await userEvent.click(screen.getByRole('button', { name: 'Yes, confirm' }))
    expect(await screen.findByText(/an SMS to this villager is still being sent/)).toBeInTheDocument()
    expect(screen.getByText('+94771234567')).toBeInTheDocument()
  })
})

describe('the history filter', () => {
  const analysis = (id: string) => ({ id, siteId: 's1', status: 'COMPLETED', risk: 'low', createdAt: '2026-11-04T21:35:11Z' })

  it('shows one site\'s history when a site is chosen, and every site otherwise', async () => {
    const calls = fakeBackend({
      'GET /api/me': () => reply(200, OFFICER),
      'GET /api/site-options': () => reply(200, [{ id: 's1', name: 'Galgamuwa', mode: 'SHADOW' }, { id: 's2', name: 'Ehetuwewa', mode: 'LIVE' }]),
      'GET /api/analyses': () => reply(200, [analysis('a1'), analysis('a2')]),
      'GET /api/analyses?siteId=s2': () => reply(200, [analysis('a3')]),
    })
    renderApp('/')
    expect((await screen.findAllByText('Completed')).length).toBe(2)
    await userEvent.selectOptions(screen.getByLabelText('Site'), 's2')
    await vi.waitFor(() => expect(screen.getAllByText('Completed').length).toBe(1))
    expect(calls.some((c) => c.path === '/api/analyses?siteId=s2')).toBe(true)
  })

  it('offers no filter when there is only one site', async () => {
    fakeBackend({
      'GET /api/me': () => reply(200, OFFICER),
      'GET /api/site-options': () => reply(200, [{ id: 's1', name: 'Galgamuwa', mode: 'SHADOW' }]),
      'GET /api/analyses': () => reply(200, [analysis('a1')]),
    })
    renderApp('/')
    await screen.findByText('Completed')
    expect(screen.queryByLabelText('Site')).not.toBeInTheDocument()
  })
})
