import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeBackend, OFFICER, renderApp, reply, stubEventSource } from '../test/render'

const SHADOW_SITE = { id: 's1', name: 'Galgamuwa', mode: 'SHADOW' }
const LIVE_SITE = { id: 's2', name: 'Ehetuwewa', mode: 'LIVE' }
const clip = (name = 'clip.mp4', type = 'video/mp4') => new File(['x'], name, { type })
const signedIn = { 'GET /api/me': () => reply(200, OFFICER), 'GET /api/analyses/a9': () => reply(404) }

// jsdom has no layout: give the picture a size so a click means a position, and stub the preview URLs.
beforeEach(() => {
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
    left: 0, top: 0, width: 200, height: 100, right: 200, bottom: 100, x: 0, y: 0, toJSON: () => ({}),
  })
  URL.createObjectURL = vi.fn(() => 'blob:preview')
  URL.revokeObjectURL = vi.fn()
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

async function openWith(sites: object[], extra: Record<string, () => Response> = {}) {
  const calls = fakeBackend({ ...signedIn, 'GET /api/site-options': () => reply(200, sites), ...extra })
  renderApp('/new')
  return calls
}

function clickPicture(x: number, y: number) {
  fireEvent.click(screen.getByTestId('picker'), { clientX: x, clientY: y })
}

describe('starting a new analysis', () => {
  it('cannot start until the site, the clip and all three boundary points are set', async () => {
    await openWith([SHADOW_SITE])
    const start = await screen.findByRole('button', { name: 'Start the analysis' })
    expect(start).toBeDisabled()
    expect(screen.getByText('This site is in test mode: alerts are stored but no SMS is sent.')).toBeInTheDocument()
    await userEvent.upload(screen.getByLabelText(/Video clip/), clip())
    expect(start).toBeDisabled()
    expect(screen.getByText('Set all three points before starting.')).toBeInTheDocument()
    clickPicture(20, 50)
    clickPicture(180, 50)
    expect(start).toBeDisabled()
    clickPicture(100, 90)
    expect(start).toBeEnabled()
    // 20/200, 50/100 ... as fractions of the picture, shown back as percentages
    expect(screen.getByText(/Boundary point 1: 10%, 50% · Boundary point 2: 90%, 50% · Point on the village side: 50%, 90%/)).toBeInTheDocument()
  })

  it('uploads the clip with the site and the six boundary fractions, with the CSRF token, then opens the analysis', async () => {
    document.cookie = 'XSRF-TOKEN=tok'
    stubEventSource() // the analysis page it opens listens for live updates
    const calls = await openWith([SHADOW_SITE], { 'POST /api/analyses': () => reply(202, { id: 'a9' }) })
    await userEvent.upload(await screen.findByLabelText(/Video clip/), clip())
    clickPicture(0, 62)
    clickPicture(200, 62)
    clickPicture(100, 98)
    await userEvent.click(screen.getByRole('button', { name: 'Start the analysis' }))
    await vi.waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true))
    const post = calls.find((c) => c.method === 'POST')!
    expect(post.headers['X-XSRF-TOKEN']).toBe('tok')
    const form = post.body as FormData
    expect(form.get('siteId')).toBe('s1')
    expect(form.get('boundary')).toBe('0.0000,0.6200,1.0000,0.6200,0.5000,0.9800')
    expect((form.get('video') as File).name).toBe('clip.mp4')
    expect(await screen.findByText('This analysis does not exist.')).toBeInTheDocument() // navigated to /analyses/a9
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('accepts the boundary typed in as percentages, so it can be set without a mouse', async () => {
    await openWith([SHADOW_SITE])
    await userEvent.upload(await screen.findByLabelText(/Video clip/), clip())
    await userEvent.click(screen.getByText('Type the positions'))
    const fields = screen.getAllByRole('spinbutton')
    for (const [i, value] of ['0', '62', '100', '62', '50', '98'].entries()) await userEvent.type(fields[i], value)
    await userEvent.click(screen.getByRole('button', { name: 'Use these points' }))
    expect(screen.getByRole('button', { name: 'Start the analysis' })).toBeEnabled()
    expect(screen.getByText(/Boundary point 1: 0%, 62%/)).toBeInTheDocument()
  })

  it('rejects typed positions that are missing or outside 0 to 100', async () => {
    await openWith([SHADOW_SITE])
    await userEvent.click(await screen.findByText('Type the positions'))
    await userEvent.click(screen.getByRole('button', { name: 'Use these points' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter all six positions as numbers from 0 to 100.')
    expect(screen.getByRole('button', { name: 'Start the analysis' })).toBeDisabled()
  })

  it('ignores more clicks once all three points are set, and clears them only on purpose', async () => {
    await openWith([SHADOW_SITE])
    await userEvent.upload(await screen.findByLabelText(/Video clip/), clip())
    clickPicture(20, 50)
    clickPicture(180, 50)
    clickPicture(100, 90)
    clickPicture(5, 5) // an accidental extra click must not change a finished boundary
    expect(screen.getByText(/Point on the village side: 50%, 90%/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Clear the points' }))
    expect(screen.queryByText(/Boundary point 1: /)).not.toBeInTheDocument()
  })

  it('needs an explicit tick before a LIVE site, where real SMS can go to villagers', async () => {
    await openWith([SHADOW_SITE, LIVE_SITE])
    await userEvent.selectOptions(await screen.findByLabelText('Site'), 's2')
    expect(screen.getByText(/This site is LIVE: villagers receive real SMS alerts/)).toBeInTheDocument()
    await userEvent.upload(screen.getByLabelText(/Video clip/), clip())
    clickPicture(20, 50)
    clickPicture(180, 50)
    clickPicture(100, 90)
    const start = screen.getByRole('button', { name: 'Start the analysis' })
    expect(start).toBeDisabled() // everything is set, but the live site is not confirmed
    await userEvent.click(screen.getByLabelText('I understand that real SMS may be sent to villagers.'))
    expect(start).toBeEnabled()
    await userEvent.selectOptions(screen.getByLabelText('Site'), 's1') // back to a test site: the tick is forgotten
    await userEvent.selectOptions(screen.getByLabelText('Site'), 's2')
    expect(screen.getByRole('button', { name: 'Start the analysis' })).toBeDisabled()
  })

  it('does not submit on Enter in a typed field, and applies the typed points instead', async () => {
    const calls = await openWith([SHADOW_SITE])
    await userEvent.upload(await screen.findByLabelText(/Video clip/), clip())
    clickPicture(20, 50)
    clickPicture(180, 50)
    clickPicture(100, 90)
    await userEvent.click(screen.getByText('Type the positions'))
    const first = screen.getAllByRole('spinbutton')[0]
    await userEvent.clear(first)
    await userEvent.type(first, '30{Enter}')
    expect(calls.some((c) => c.method === 'POST')).toBe(false) // nothing was uploaded
    expect(screen.getByText(/Boundary point 1: 30%, 50%/)).toBeInTheDocument() // the edit was applied
  })

  it('refuses to submit a LIVE site that was not confirmed, even when the form is submitted directly', async () => {
    const calls = await openWith([LIVE_SITE], { 'POST /api/analyses': () => reply(202, { id: 'a9' }) })
    await userEvent.upload(await screen.findByLabelText(/Video clip/), clip())
    clickPicture(20, 50)
    clickPicture(180, 50)
    clickPicture(100, 90)
    fireEvent.submit(screen.getByRole('button', { name: 'Start the analysis' }).closest('form')!) // bypasses the button
    await Promise.resolve()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('refuses a file that is not an MP4 or is too large, before uploading anything', async () => {
    const calls = await openWith([SHADOW_SITE])
    const input = await screen.findByLabelText(/Video clip/)
    await userEvent.upload(input, clip('notes.txt', 'text/plain'), { applyAccept: false })
    expect(await screen.findByRole('alert')).toHaveTextContent('The file is not an MP4 video.')
    const big = clip()
    Object.defineProperty(big, 'size', { value: 201 * 1024 * 1024 })
    await userEvent.upload(input, big)
    expect(await screen.findByRole('alert')).toHaveTextContent('The video is larger than 200 MB.')
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it.each([
    [415, 'The file is not an MP4 video.'],
    [400, 'The clip was not accepted. Check the site and the boundary points.'],
    [500, 'The upload failed. Your clip and points are kept; try again.'],
  ])('explains a %i answer from the server and keeps the clip and the points', async (status, message) => {
    await openWith([SHADOW_SITE], { 'POST /api/analyses': () => reply(status, { detail: 'x' }) })
    await userEvent.upload(await screen.findByLabelText(/Video clip/), clip())
    clickPicture(20, 50)
    clickPicture(180, 50)
    clickPicture(100, 90)
    await userEvent.click(screen.getByRole('button', { name: 'Start the analysis' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByText(/Point on the village side: 50%, 90%/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start the analysis' })).toBeEnabled()
  })

  it('says there is no site yet, and does not call a failed site list "no site"', async () => {
    await openWith([])
    expect(await screen.findByText('There is no site yet. An admin must create one.')).toBeInTheDocument()
    vi.unstubAllGlobals()
    fakeBackend({ ...signedIn, 'GET /api/site-options': () => reply(500) })
    renderApp('/new')
    expect((await screen.findAllByRole('button', { name: 'Try again' })).length).toBeGreaterThan(0)
  })
})
