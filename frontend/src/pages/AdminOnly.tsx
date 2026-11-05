import { Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useI18n } from '../i18n/useI18n'

// Shows the admin pages only to an admin. This is a convenience, not security: every admin endpoint checks the role
// again on the server, so nothing here can open data to someone who is not an admin.
export default function AdminOnly() {
  const { t } = useI18n()
  const { session } = useAuth()
  if (session.status !== 'signedIn' || session.me.role !== 'ADMIN') {
    return <p role="alert">{t('state.forbidden')}</p>
  }
  return <Outlet />
}
