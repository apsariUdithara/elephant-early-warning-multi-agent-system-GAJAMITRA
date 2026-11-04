// Risk is always shown as text and colour together, never colour alone (WCAG 2.2, frontend rules).
import type { Risk } from '../api/types'
import { useI18n } from '../i18n/useI18n'

const MARK: Record<Risk, string> = { low: '○', medium: '◐', high: '●', critical: '▲' }

export default function RiskBadge({ risk }: { risk: Risk }) {
  const { t } = useI18n()
  return (
    <span className="risk-badge" data-risk={risk}>
      <span aria-hidden="true">{MARK[risk]}</span>
      {t(`risk.${risk}`)}
    </span>
  )
}
