import { useI18n } from '../i18n/useI18n'

// Loading and failure are designed states, not blank screens. `code`: 0 server unreachable, 403 not allowed,
// anything else a failed or unexpected answer. A failure offers a retry; it never reads as "nothing found".
export function Loading() {
  const { t } = useI18n()
  return (
    <p role="status" className="muted">
      {t('state.loading')}
    </p>
  )
}

export function LoadError({ code, onRetry }: { code: number; onRetry: () => void }) {
  const { t } = useI18n()
  const message = code === 0 ? t('state.unreachable') : code === 403 ? t('state.forbidden') : t('state.error')
  return (
    <div role="alert" className="state-error">
      <p>{message}</p>
      {code !== 403 && (
        <button type="button" className="button" onClick={onRetry}>
          {t('state.retry')}
        </button>
      )}
    </div>
  )
}
