import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { parseSitesFull, parseSubscribers } from '../api/parse'
import type { SiteFull, VillagerLanguage } from '../api/types'
import { useApi } from '../api/useApi'
import ConfirmButton from '../components/ConfirmButton'
import ResultMessage from '../components/ResultMessage'
import SiteForm from '../components/SiteForm'
import type { SiteValues } from '../components/siteValues'
import StaleNotice from '../components/StaleNotice'
import { LoadError, Loading } from '../components/StateMessage'
import { LANGUAGES } from '../i18n/messages'
import { useI18n } from '../i18n/useI18n'
import { problemOf } from './adminMessage'

// One site: its settings, its mode (a switch to LIVE states plainly that villagers will receive real SMS and asks first),
// and the villagers who receive its alerts. Phone numbers are personal data and are shown only to an admin. The mode is
// what decides whether real SMS go out, so a view that could not be refreshed says it may be out of date.
export default function AdminSitePage() {
  const { id = '' } = useParams()
  const { t } = useI18n()
  const sites = useApi('/api/sites', parseSitesFull)
  const villagers = useApi(`/api/sites/${encodeURIComponent(id)}/subscribers`, parseSubscribers)
  const [message, setMessage] = useState<string | null>(null)
  const [phone, setPhone] = useState('')
  const [language, setLanguage] = useState<VillagerLanguage>('SI')
  const [adding, setAdding] = useState(false)
  const site: SiteFull | undefined = sites.state.status === 'ready' ? sites.state.data.find((s) => s.id === id) : undefined

  function explain(error: unknown) {
    const problem = problemOf(error)
    setMessage(t(problem.key, problem.values))
  }

  async function save(values: SiteValues) {
    setMessage(null)
    try {
      await api<unknown>(`/api/sites/${encodeURIComponent(id)}`, { method: 'PUT', json: values })
      setMessage(t('admin.saved'))
      sites.reload()
    } catch (error) {
      explain(error)
    }
  }

  async function switchMode(mode: 'SHADOW' | 'LIVE') {
    setMessage(null)
    try {
      await api<unknown>(`/api/sites/${encodeURIComponent(id)}/mode`, { method: 'PUT', json: { mode } })
      setMessage(t('admin.modeNow', { mode: t(`mode.${mode}`) })) // says which mode is now active
      sites.reload()
    } catch (error) {
      explain(error)
    }
  }

  async function addVillager(event: FormEvent) {
    event.preventDefault()
    if (adding) return
    setAdding(true)
    setMessage(null)
    try {
      await api<unknown>(`/api/sites/${encodeURIComponent(id)}/subscribers`, { method: 'POST', json: { phone, language } })
      setPhone('')
      setMessage(t('admin.added'))
      villagers.reload()
    } catch (error) {
      explain(error)
    } finally {
      setAdding(false)
    }
  }

  async function remove(subscriberId: string) {
    setMessage(null)
    try {
      await api<void>(`/api/sites/${encodeURIComponent(id)}/subscribers/${encodeURIComponent(subscriberId)}`, { method: 'DELETE' })
      setMessage(t('admin.removed'))
      villagers.reload()
    } catch (error) {
      explain(error)
    }
  }

  return (
    <section aria-labelledby="site-title">
      <p>
        <Link to="/admin">{t('admin.back')}</Link>
      </p>
      <ResultMessage message={message} />
      {sites.state.status === 'loading' && <Loading />}
      {sites.state.status === 'error' && <LoadError code={sites.state.code} onRetry={sites.reload} />}
      {sites.state.status === 'ready' && sites.state.stale && <StaleNotice onRetry={sites.reload} />}
      {sites.state.status === 'ready' && !site && <p role="alert">{t('admin.gone')}</p>}
      {site && (
        <>
          <h2 id="site-title">{site.name}</h2>

          <h3>{t('admin.mode.title')}</h3>
          <div className="notice" data-mode={site.mode}>
            <p>
              <strong>{t(`mode.${site.mode}`)}</strong>
            </p>
            <p>{t(`new.mode.${site.mode}`)}</p>
          </div>
          <ConfirmButton
            label={t(site.mode === 'LIVE' ? 'admin.mode.toShadow' : 'admin.mode.toLive')}
            question={t(site.mode === 'LIVE' ? 'admin.mode.confirmShadow' : 'admin.mode.confirmLive')}
            onConfirm={() => switchMode(site.mode === 'LIVE' ? 'SHADOW' : 'LIVE')}
          />

          <SiteForm
            key={JSON.stringify([site.name, site.villages, site.officerContact, site.requiresApproval, site.approvalTimeoutS])}
            initial={site}
            submitLabel={t('admin.save')}
            onSubmit={save}
          />

          <h3>{t('admin.subscribers')}</h3>
          {villagers.state.status === 'loading' && <Loading />}
          {villagers.state.status === 'error' && <LoadError code={villagers.state.code} onRetry={villagers.reload} />}
          {villagers.state.status === 'ready' &&
            (villagers.state.data.length === 0 ? (
              <p className="muted">{t('admin.subscribers.none')}</p>
            ) : (
              <ul className="analysis-list narrow">
                {villagers.state.data.map((v) => (
                  <li key={v.id} className="analysis-row">
                    <span className="count">{v.phone}</span>
                    <span className="tag" lang={v.language.toLowerCase()}>
                      {LANGUAGES.find((l) => l.code === v.language.toLowerCase())?.name ?? v.language}
                    </span>
                    <ConfirmButton
                      label={t('admin.remove')}
                      ariaLabel={t('admin.removeNamed', { phone: v.phone })}
                      question={t('admin.removeConfirm')}
                      onConfirm={() => remove(v.id)}
                    />
                  </li>
                ))}
              </ul>
            ))}
          <form className="panel new-form" onSubmit={(e) => void addVillager(e)}>
            <label>
              {t('admin.phone')}
              <input
                required
                type="tel"
                inputMode="tel"
                autoComplete="off"
                placeholder="+94771234567"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            </label>
            <label>
              {t('admin.language')}
              <select value={language} onChange={(e) => setLanguage(e.target.value as VillagerLanguage)}>
                {LANGUAGES.map((l) => (
                  <option key={l.code} value={l.code.toUpperCase()} lang={l.code}>
                    {l.name}
                  </option>
                ))}
              </select>
            </label>
            <button type="submit" className="button" disabled={adding}>
              {adding ? t('admin.working') : t('admin.add')}
            </button>
          </form>
        </>
      )}
    </section>
  )
}
