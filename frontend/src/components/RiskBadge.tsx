// Risk is always shown as text and colour together, never colour alone (WCAG 2.2, frontend rules).
export type Risk = 'low' | 'medium' | 'high' | 'critical'

const LABEL: Record<Risk, string> = {
  low: 'Low risk',
  medium: 'Medium risk',
  high: 'High risk',
  critical: 'Critical risk',
}

const MARK: Record<Risk, string> = { low: '○', medium: '◐', high: '●', critical: '▲' }

export default function RiskBadge({ risk }: { risk: Risk }) {
  return (
    <span className="risk-badge" data-risk={risk}>
      <span aria-hidden="true">{MARK[risk]}</span>
      {LABEL[risk]}
    </span>
  )
}
