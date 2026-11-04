import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import LanguageSwitcher from '../components/LanguageSwitcher'
import ThemeToggle from '../components/ThemeToggle'
import { useI18n } from '../i18n/useI18n'
import type { MessageKey } from '../i18n/messages'

function failureKey(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'login.failed'
    if (error.status === 429) return 'login.locked'
    if (error.status !== 0) return 'login.error' // e.g. a refused CSRF token: not "wrong password", not "unreachable"
  }
  return 'login.unreachable'
}

export default function Login() {
  const { t } = useI18n()
  const { session, login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false) // one attempt at a time: no repeated submissions
  const [failure, setFailure] = useState<MessageKey | null>(null)

  if (session.status === 'signedIn') return <Navigate to="/" replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setPending(true)
    setFailure(null)
    try {
      await login(username, password)
    } catch (error) {
      setFailure(failureKey(error))
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="login">
      <div className="login-tools">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>
      <form className="panel login-form" onSubmit={submit} aria-labelledby="login-title">
        <h1 id="login-title">{t('app.title')}</h1>
        <h2>{t('login.title')}</h2>
        <label>
          {t('login.username')}
          <input
            name="username"
            autoComplete="username"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            aria-describedby={failure ? 'login-error' : undefined}
          />
        </label>
        <label>
          {t('login.password')}
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-describedby={failure ? 'login-error' : undefined}
          />
        </label>
        {failure && (
          <p id="login-error" role="alert" className="form-error">
            {t(failure)}
          </p>
        )}
        <button type="submit" className="button" disabled={pending}>
          {pending ? t('login.submitting') : t('login.submit')}
        </button>
      </form>
    </main>
  )
}
