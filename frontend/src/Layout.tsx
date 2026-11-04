import { NavLink, Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './auth/useAuth'
import LanguageSwitcher from './components/LanguageSwitcher'
import { Loading } from './components/StateMessage'
import ThemeToggle from './components/ThemeToggle'
import { useI18n } from './i18n/useI18n'

// The signed-in shell: one header and navigation for every page. Anything but a signed-in session never shows a page.
export default function Layout() {
  const { t } = useI18n()
  const { session, logout, retry } = useAuth()

  if (session.status === 'loading') return <Loading />
  if (session.status === 'anonymous') return <Navigate to="/login" replace />
  if (session.status === 'unreachable') {
    return (
      <main className="app-main">
        <div role="alert" className="state-error">
          <p>{t('state.unreachable')}</p>
          <button type="button" className="button" onClick={() => void retry()}>
            {t('state.retry')}
          </button>
        </div>
      </main>
    )
  }

  return (
    <>
      <header className="app-header">
        <h1>{t('app.title')}</h1>
        <nav aria-label={t('nav.main')} className="app-nav">
          <NavLink to="/" end>
            {t('nav.analyses')}
          </NavLink>
          <NavLink to="/new">{t('nav.new')}</NavLink>
          <NavLink to="/approvals">{t('nav.approvals')}</NavLink>
        </nav>
        <div className="app-tools">
          <LanguageSwitcher />
          <ThemeToggle />
          <span className="muted-on-dark">{session.me.username}</span>
          <button type="button" className="button button-quiet" onClick={() => void logout()}>
            {t('nav.signOut')}
          </button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </>
  )
}
