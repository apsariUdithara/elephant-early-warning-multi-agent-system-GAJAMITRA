import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { parseCreatedId, parseSites } from '../api/parse'
import { useApi } from '../api/useApi'
import BoundaryPicker, { type Point } from '../components/BoundaryPicker'
import { LoadError, Loading } from '../components/StateMessage'
import { useI18n } from '../i18n/useI18n'
import type { MessageKey } from '../i18n/messages'

// Start an analysis: pick the site, choose the clip, set the village boundary, upload. The server checks everything again
// (MP4 content, size, boundary, site, and which mode the site is in); the checks here only save a round trip. A site that
// is LIVE means real SMS can go to villagers, so that needs an explicit tick, and the form refuses to submit without it
// however it is submitted (button, Enter key, script). A failed upload keeps the clip and the points.
const MAX_BYTES = 200 * 1024 * 1024

function failureKey(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 415) return 'new.err.type'
    if (error.status === 413) return 'new.err.size'
    if (error.status === 400 || error.status === 404) return 'new.err.rejected'
  }
  return 'new.err.failed'
}

export default function NewAnalysisPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const { state, reload } = useApi('/api/site-options', parseSites)
  const [chosenSite, setChosenSite] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [videoUrl, setVideoUrl] = useState<string | null>(null)
  const [fileError, setFileError] = useState<MessageKey | null>(null)
  const [points, setPoints] = useState<Point[]>([])
  const [liveConfirmed, setLiveConfirmed] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [failure, setFailure] = useState<MessageKey | null>(null)
  const currentUrl = useRef<string | null>(null)
  const sending = useRef(false) // set in the same tick as the click: state alone could let a quick second submit through
  const failureRef = useRef<HTMLParagraphElement | null>(null)

  useEffect(() => () => {
    if (currentUrl.current) URL.revokeObjectURL(currentUrl.current) // no preview is kept after the page is left
  }, [])

  useEffect(() => {
    if (failure) failureRef.current?.focus() // the error is announced and reachable, next to the button that caused it
  }, [failure])

  const sites = state.status === 'ready' ? state.data : []
  const siteId = chosenSite || (sites.length === 1 ? sites[0].id : '')
  const site = sites.find((s) => s.id === siteId)
  const live = site?.mode === 'LIVE'
  const ready = Boolean(site && file && points.length === 3 && (!live || liveConfirmed))

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0] ?? null
    if (currentUrl.current) URL.revokeObjectURL(currentUrl.current)
    currentUrl.current = null
    setFile(null)
    setVideoUrl(null)
    setPoints([]) // a boundary belongs to one picture
    setFileError(null)
    if (!chosen) return
    if (!(chosen.type === 'video/mp4' || chosen.name.toLowerCase().endsWith('.mp4'))) return setFileError('new.err.type')
    if (chosen.size > MAX_BYTES) return setFileError('new.err.size')
    currentUrl.current = URL.createObjectURL(chosen)
    setFile(chosen)
    setVideoUrl(currentUrl.current)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (sending.current || !ready || !file) return // the same rule as the button, whatever triggered the submit
    sending.current = true
    setSubmitting(true)
    setFailure(null)
    const form = new FormData()
    form.append('video', file)
    form.append('siteId', siteId)
    form.append('boundary', points.flat().map((v) => v.toFixed(4)).join(','))
    try {
      const id = parseCreatedId(await api<unknown>('/api/analyses', { method: 'POST', form }))
      navigate(`/analyses/${encodeURIComponent(id)}`)
    } catch (error) {
      setFailure(failureKey(error))
      sending.current = false
      setSubmitting(false)
    }
  }

  return (
    <section aria-labelledby="new-title">
      <h2 id="new-title">{t('new.title')}</h2>
      {state.status === 'loading' && <Loading />}
      {state.status === 'error' && <LoadError code={state.code} onRetry={reload} />}
      {state.status === 'ready' && sites.length === 0 && <p>{t('new.noSites')}</p>}
      {state.status === 'ready' && sites.length > 0 && (
        <form className="panel new-form" onSubmit={(e) => void submit(e)}>
          <label>
            {t('new.site')}
            <select
              value={siteId}
              required
              onChange={(e) => {
                setChosenSite(e.target.value)
                setLiveConfirmed(false) // the tick belongs to one site
              }}
            >
              <option value="" disabled>
                {t('new.sitePlaceholder')}
              </option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {t('new.siteOption', { name: s.name, mode: t(`mode.${s.mode}`) })}
                </option>
              ))}
            </select>
          </label>
          {site && (
            <p className="notice" data-mode={site.mode}>
              {t(`new.mode.${site.mode}`)}
            </p>
          )}

          <label>
            {t('new.file')}
            <input type="file" accept="video/mp4,.mp4" onChange={chooseFile} aria-describedby={fileError ? 'file-error' : undefined} />
          </label>
          {fileError && (
            <p id="file-error" role="alert" className="form-error">
              {t(fileError)}
            </p>
          )}

          <h3>{t('new.boundary.title')}</h3>
          <BoundaryPicker key={videoUrl ?? 'no-clip'} videoUrl={videoUrl} points={points} onChange={setPoints} />

          {live && (
            <label className="checkbox">
              <input type="checkbox" checked={liveConfirmed} onChange={(e) => setLiveConfirmed(e.target.checked)} />
              {t('new.liveConfirm')}
            </label>
          )}
          <p className="muted">{t('new.costNote')}</p>
          {failure && (
            <p role="alert" className="form-error" tabIndex={-1} ref={failureRef}>
              {t(failure)}
            </p>
          )}
          {file && points.length !== 3 && <p className="muted">{t('new.pointsMissing')}</p>}
          <button type="submit" className="button" disabled={!ready || submitting}>
            {submitting ? t('new.submitting') : t('new.submit')}
          </button>
        </form>
      )}
    </section>
  )
}
