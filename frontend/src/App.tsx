// App shell. The pages (login, new analysis, live analysis, approvals, history, admin) arrive in plan Phase 5.
import type { Risk } from './api/types'
import RiskBadge from './components/RiskBadge'
import ThemeToggle from './components/ThemeToggle'

const RISKS: Risk[] = ['low', 'medium', 'high', 'critical']

export default function App() {
  return (
    <>
      <header className="app-header">
        <h1>GajaMitra</h1>
        <ThemeToggle />
      </header>
      <main className="app-main">
        <section className="panel" aria-labelledby="legend-title">
          <h2 id="legend-title">Risk levels</h2>
          <p className="muted">How the dashboard shows the tracker&apos;s risk for a herd near a village boundary.</p>
          <ul className="risk-list">
            {RISKS.map((risk) => (
              <li key={risk}>
                <RiskBadge risk={risk} />
              </li>
            ))}
          </ul>
        </section>
      </main>
    </>
  )
}
