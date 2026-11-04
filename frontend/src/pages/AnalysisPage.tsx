import { Link, useParams } from 'react-router-dom'
import { parseAnalysisDetail } from '../api/parse'
import type { AlertView, DeliveryState } from '../api/types'
import { useAnalysisEvents } from '../api/useAnalysisEvents'
import { useApi } from '../api/useApi'
import AlertTexts from '../components/AlertTexts'
import RiskBadge from '../components/RiskBadge'
import { LoadError, Loading } from '../components/StateMessage'
import { useI18n } from '../i18n/useI18n'

// One analysis: its state and risk, what was sent (in all three languages, test mode or real, per SMS state), the officer
// report and how the guard and verifier decided. Updates live; if the live connection drops, the page says it may be out
// of date. Everything written by the AI service is shown as plain text.
function AlertCard({ alert }: { alert: AlertView }) {
  const { t } = useI18n()
  const counts = Object.entries(alert.deliveries) as [DeliveryState, number][]
  return (
    <article className="alert-card" data-severity={alert.severity}>
      <h4>
        {t(`alert.severity.${alert.severity}`)} · {t(`alert.stage.${alert.stage}`)}
      </h4>
      {alert.shadow && <p className="tag">{t('alert.shadow')}</p>}
      <AlertTexts alert={alert} />
      {counts.length > 0 && (
        <>
          <p className="muted">{t('alert.sms')}</p>
          <ul className="delivery-list">
            {counts.map(([state, count]) => (
              <li key={state}>
                {t(`delivery.${state}`)}: <span className="count">{count}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </article>
  )
}

export default function AnalysisPage() {
  const { id = '' } = useParams()
  const { t, formatDateTime } = useI18n()
  const { state, reload } = useApi(`/api/analyses/${encodeURIComponent(id)}`, parseAnalysisDetail)
  const connection = useAnalysisEvents(id, reload)

  return (
    <section aria-labelledby="analysis-title">
      <p>
        <Link to="/">{t('analysis.back')}</Link>
      </p>
      {state.status === 'loading' && <Loading />}
      {state.status === 'error' && state.code === 404 && <p role="alert">{t('analysis.notFound')}</p>}
      {state.status === 'error' && state.code !== 404 && <LoadError code={state.code} onRetry={reload} />}
      {state.status === 'ready' && (
        <>
          <h2 id="analysis-title">{t(`status.${state.data.status}`)}</h2>
          <p className="analysis-meta">
            {state.data.risk ? <RiskBadge risk={state.data.risk} /> : <span className="muted">{t('analyses.riskPending')}</span>}
            <span className="muted">
              {t('analyses.started')}:{' '}
              <time dateTime={state.data.createdAt}>{formatDateTime(state.data.createdAt)}</time>
            </span>
          </p>
          <p
            className="connection"
            data-connection={connection === 'live' && !state.stale ? 'live' : connection}
            aria-live="polite"
          >
            {connection === 'closed'
              ? t('analysis.connection.closed')
              : connection === 'reconnecting' || state.stale
                ? t('analysis.connection.reconnecting')
                : connection === 'live'
                  ? t('analysis.connection.live')
                  : t('analysis.connection.connecting')}
          </p>

          {state.data.status === 'AWAITING_APPROVAL' && (
            <div className="notice">
              <p>{t('analysis.approvalWaiting')}</p>
              <Link to="/approvals">{t('analysis.openApprovals')}</Link>
            </div>
          )}
          {state.data.status === 'FAILED' && (
            <div role="alert" className="state-error">
              <p>{t('analysis.failureReason')}</p>
              <p>{state.data.failureReason}</p>
            </div>
          )}

          <h3>{t('analysis.alerts')}</h3>
          {state.data.alerts.length === 0 ? (
            <p className="muted">{t('analysis.noAlerts')}</p>
          ) : (
            state.data.alerts.map((a, i) => <AlertCard key={`${a.stage}-${a.createdAt}-${i}`} alert={a} />)
          )}

          {state.data.officerReport && (
            <>
              <h3>{t('analysis.report')}</h3>
              <p>{state.data.officerReport}</p>
            </>
          )}
          {state.data.notes.length > 0 && (
            <>
              <h3>{t('analysis.notes')}</h3>
              <ul>
                {state.data.notes.map((note, i) => (
                  <li key={i}>{note}</li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  )
}
