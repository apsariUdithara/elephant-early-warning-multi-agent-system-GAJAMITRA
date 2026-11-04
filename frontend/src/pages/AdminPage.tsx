import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { parseSitesFull } from '../api/parse'
import { useApi } from '../api/useApi'
import SiteForm from '../components/SiteForm'
import { NEW_SITE, type SiteValues } from '../components/siteValues'
import ResultMessage from '../components/ResultMessage'
import StaleNotice from '../components/StaleNotice'
import { LoadError, Loading } from '../components/StateMessage'
import { useI18n } from '../i18n/useI18n'
import { problemOf } from './adminMessage'

// Sites (admin only; the server checks the role again). Each site shows its mode as text, whether escalations wait for an
// officer, and the time limit. New sites start in test mode: alerts are stored, no SMS is sent.
export default function AdminPage() {
  const { t } = useI18n()
  const { state, reload } = useApi('/api/sites', parseSitesFull)
  const [message, setMessage] = useState<string | null>(null)
  const [formKey, setFormKey] = useState(0)

  async function create(values: SiteValues) {
    setMessage(null)
    try {
      await api<unknown>('/api/sites', { method: 'POST', json: values })
      setMessage(t('admin.saved'))
      setFormKey((k) => k + 1) // a fresh, empty form
      reload()
    } catch (error) {
      const problem = problemOf(error)
      setMessage(t(problem.key, problem.values))
    }
  }

  return (
    <section aria-labelledby="admin-title">
      <h2 id="admin-title">{t('admin.title')}</h2>
      <ResultMessage message={message} />
      {state.status === 'loading' && <Loading />}
      {state.status === 'error' && <LoadError code={state.code} onRetry={reload} />}
      {state.status === 'ready' && state.stale && <StaleNotice onRetry={reload} />}
      {state.status === 'ready' && (
        <>
          {state.data.length === 0 ? (
            <p>{t('admin.none')}</p>
          ) : (
            <ul className="analysis-list">
              {state.data.map((s) => (
                <li key={s.id} className="analysis-row">
                  <strong>{s.name}</strong>
                  <span className="tag">{t(`mode.${s.mode}`)}</span>
                  <span className="muted">{t(s.requiresApproval ? 'admin.approval.yes' : 'admin.approval.no')}</span>
                  <Link className="analysis-time" to={`/admin/sites/${encodeURIComponent(s.id)}`}>
                    {t('admin.open')}
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <h3>{t('admin.create')}</h3>
          <SiteForm key={formKey} initial={NEW_SITE} submitLabel={t('admin.create')} onSubmit={create} />
        </>
      )}
    </section>
  )
}
