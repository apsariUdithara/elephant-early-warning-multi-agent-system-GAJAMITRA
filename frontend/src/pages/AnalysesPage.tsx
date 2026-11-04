import { Link } from 'react-router-dom'
import { parseAnalysisList } from '../api/parse'
import { useApi } from '../api/useApi'
import RiskBadge from '../components/RiskBadge'
import { LoadError, Loading } from '../components/StateMessage'
import { useI18n } from '../i18n/useI18n'

// The latest analyses, newest first. Status and risk are both text; a risk that is not recorded yet says so.
export default function AnalysesPage() {
  const { t, formatDateTime } = useI18n()
  const { state, reload } = useApi('/api/analyses', parseAnalysisList)

  return (
    <section aria-labelledby="analyses-title">
      <h2 id="analyses-title">{t('analyses.title')}</h2>
      {state.status === 'loading' && <Loading />}
      {state.status === 'error' && <LoadError code={state.code} onRetry={reload} />}
      {state.status === 'ready' && state.data.length === 0 && <p className="muted">{t('analyses.empty')}</p>}
      {state.status === 'ready' && state.data.length > 0 && (
        <ul className="analysis-list">
          {state.data.map((a) => (
            <li key={a.id} className="analysis-row">
              <Link className="analysis-status" data-status={a.status} to={`/analyses/${encodeURIComponent(a.id)}`}>
                {t(`status.${a.status}`)}
              </Link>
              {a.risk ? <RiskBadge risk={a.risk} /> : <span className="muted">{t('analyses.riskPending')}</span>}
              <span className="muted analysis-time">
                {t('analyses.started')}: <time dateTime={a.createdAt}>{formatDateTime(a.createdAt)}</time>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
