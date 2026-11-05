import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { parseApprovalList } from '../api/parse'
import type { ApprovalView } from '../api/types'
import { useApi } from '../api/useApi'
import { useNow } from '../api/useNow'
import AlertTexts from '../components/AlertTexts'
import { LoadError, Loading } from '../components/StateMessage'
import { useI18n } from '../i18n/useI18n'
import type { MessageKey } from '../i18n/messages'

// The officer's queue. Three things must be unmistakable: the warning for each analysis has ALREADY gone out, approving
// sends only the escalation, and if nobody decides the escalation is sent automatically when the time runs out (the
// countdown). The page never blocks anything: it only lets an officer decide sooner. A decision is confirmed with its
// consequence, cannot be submitted twice, and a 409 (already decided, or the time ran out) is explained. The result of a
// decision is shown at the top of the page, because the card itself disappears when the list refreshes.
const REFRESH_MS = 10_000

type Decision = 'APPROVE' | 'REJECT'
type Step = { name: 'idle' } | { name: 'confirm'; decision: Decision } | { name: 'saving'; decision: Decision }

function clock(ms: number): string {
  const seconds = Math.ceil(ms / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

// Counts down to the server's own expiry time using the server's clock (offset measured when the list arrived), so a
// wrong clock in the browser can never make the escalation look sent, or held, when it is not.
function FrameLink({ analysisId, n, total }: { analysisId: string; n: number; total: number }) {
  const { t } = useI18n()
  const [failed, setFailed] = useState(false)
  const url = `/api/analyses/${encodeURIComponent(analysisId)}/frames/${n - 1}`
  if (failed) return <p role="status">{t('approvals.frameMissing', { n })}</p>
  return (
    <a href={url} target="_blank" rel="noreferrer" aria-label={t('approvals.frameOpens', { n, total })}>
      <img loading="lazy" src={url} alt={t('approvals.frameAlt', { n, total })} onError={() => setFailed(true)} />
    </a>
  )
}

function Countdown({ approval }: { approval: ApprovalView }) {
  const { t } = useI18n()
  const left = Date.parse(approval.expiresAt) - (useNow() + approval.clockOffsetMs)
  // No live region: a countdown that ticks every second must not be read out every second.
  return <p className="countdown">{left > 0 ? t('approvals.countdown', { time: clock(left) }) : t('approvals.expired')}</p>
}

function ApprovalCard({ approval, onDecided }: { approval: ApprovalView; onDecided: (message: MessageKey) => void }) {
  const { t } = useI18n()
  const [step, setStep] = useState<Step>({ name: 'idle' })
  const [note, setNote] = useState('')
  const [failed, setFailed] = useState(false)
  const trigger = useRef<HTMLButtonElement | null>(null)
  const confirmText = useRef<HTMLParagraphElement | null>(null)
  const unreadable = approval.proposedAlerts === null || approval.proposedAlerts.length === 0

  // Keyboard and screen-reader users follow the step: into the confirmation, and back to the button that opened it.
  useEffect(() => {
    if (step.name === 'confirm') confirmText.current?.focus()
    else if (step.name === 'idle') trigger.current?.focus()
  }, [step.name])

  async function submit(decision: Decision) {
    if (step.name === 'saving') return // one submission at a time
    setStep({ name: 'saving', decision })
    setFailed(false)
    try {
      await api<void>(`/api/approvals/${encodeURIComponent(approval.id)}`, {
        method: 'POST',
        json: { decision, note: note.trim() || null },
      })
      onDecided('approvals.saved')
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        onDecided('approvals.alreadyDecided') // the escalation was already released: the list is refreshed
      } else {
        setFailed(true)
        setStep({ name: 'idle' })
      }
    }
  }

  function open(decision: Decision, button: HTMLButtonElement) {
    trigger.current = button
    setStep({ name: 'confirm', decision })
  }

  const asking = step.name === 'confirm' || step.name === 'saving'
  return (
    <li className="approval-card">
      <Countdown approval={approval} />
      <h3>{t('approvals.proposed')}</h3>
      {unreadable ? (
        <p>{t('approvals.unreadable')}</p>
      ) : (
        approval.proposedAlerts?.map((alert, i) => (
          <div key={i}>
            <p className="tag">{t(`alert.severity.${alert.severity}`)}</p>
            <AlertTexts alert={alert} />
          </div>
        ))
      )}
      {approval.frames > 0 && (
        <>
          <h3>{t('approvals.frames')}</h3>
          <div className="frames">
            {Array.from({ length: approval.frames }, (_, i) => (
              <FrameLink key={i} analysisId={approval.analysisId} n={i + 1} total={approval.frames} />
            ))}
          </div>
        </>
      )}
      <p>
        <Link to={`/analyses/${encodeURIComponent(approval.analysisId)}`}>{t('approvals.viewAnalysis')}</Link>
      </p>

      {asking ? (
        <div role="group" aria-label={t(step.decision === 'APPROVE' ? 'approvals.confirmApprove' : 'approvals.confirmReject')}>
          <p className="confirm" tabIndex={-1} ref={confirmText}>
            {t(step.decision === 'APPROVE' ? 'approvals.confirmApprove' : 'approvals.confirmReject')}
          </p>
          <div className="actions">
            <button type="button" className="button" disabled={step.name === 'saving'} onClick={() => void submit(step.decision)}>
              {step.name === 'saving' ? t('approvals.saving') : t('approvals.confirmYes')}
            </button>
            <button type="button" className="button button-quiet" disabled={step.name === 'saving'} onClick={() => setStep({ name: 'idle' })}>
              {t('approvals.cancel')}
            </button>
          </div>
        </div>
      ) : (
        <>
          <label>
            {t('approvals.note')}
            <textarea value={note} maxLength={500} rows={2} onChange={(e) => setNote(e.target.value)} />
          </label>
          {failed && (
            <p role="alert" className="form-error">
              {t('approvals.failed')}
            </p>
          )}
          {unreadable && (
            <p id={`unreadable-${approval.id}`} className="muted">
              {t('approvals.unreadableApprove')}
            </p>
          )}
          <div className="actions">
            <button
              type="button"
              className="button"
              disabled={unreadable} // an officer can't approve text they cannot see; if nobody decides it is sent anyway
              aria-describedby={unreadable ? `unreadable-${approval.id}` : undefined}
              onClick={(e) => open('APPROVE', e.currentTarget)}
            >
              {t('approvals.approve')}
            </button>
            <button type="button" className="button button-quiet" onClick={(e) => open('REJECT', e.currentTarget)}>
              {t('approvals.reject')}
            </button>
          </div>
        </>
      )}
    </li>
  )
}

export default function ApprovalsPage() {
  const { t, formatDateTime } = useI18n()
  const { state, reload } = useApi('/api/approvals', parseApprovalList)
  const [notice, setNotice] = useState<MessageKey | null>(null)
  const noticeRef = useRef<HTMLParagraphElement | null>(null)

  useEffect(() => {
    const timer = setInterval(reload, REFRESH_MS) // there is no live stream for the queue: poll, and say when it is stale
    return () => clearInterval(timer)
  }, [reload])

  useEffect(() => {
    if (notice) noticeRef.current?.focus() // the result of a decision is announced and reachable, then the card is gone
  }, [notice])

  function decided(message: MessageKey) {
    setNotice(message)
    reload()
  }

  return (
    <section aria-labelledby="approvals-title">
      <h2 id="approvals-title">{t('approvals.title')}</h2>
      <p className="notice">{t('approvals.intro')}</p>
      {notice && (
        <p role="status" className="notice" tabIndex={-1} ref={noticeRef}>
          {t(notice)}
        </p>
      )}
      {state.status === 'loading' && <Loading />}
      {state.status === 'error' && <LoadError code={state.code} onRetry={reload} />}
      {state.status === 'ready' && (
        <>
          <p className="muted">{t('approvals.updated', { time: formatDateTime(new Date(state.at).toISOString()) })}</p>
          {state.stale && (
            <p role="status" className="connection" data-connection="reconnecting">
              {t('analysis.connection.reconnecting')}
            </p>
          )}
          {state.data.length === 0 ? (
            <p>{t('approvals.empty')}</p>
          ) : (
            <ul className="approval-list">
              {state.data.map((a) => (
                <ApprovalCard key={a.id} approval={a} onDecided={decided} />
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}
