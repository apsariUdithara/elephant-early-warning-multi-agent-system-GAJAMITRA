import { useI18n } from '../i18n/useI18n'

// Shown when a refresh failed and the page is still displaying the last answer. For something like a site's mode this
// matters: an out-of-date "test mode" must never be mistaken for the truth.
export default function StaleNotice({ onRetry }: { onRetry: () => void }) {
  const { t } = useI18n()
  return (
    <div role="status" className="state-error">
      <p>{t('analysis.connection.reconnecting')}</p>
      <button type="button" className="button" onClick={onRetry}>
        {t('state.retry')}
      </button>
    </div>
  )
}
