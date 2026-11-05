import { useSyncExternalStore } from 'react'
import { useI18n } from '../i18n/useI18n'
import { getTheme, saveTheme, subscribe } from '../theme'

// A toggle button: the visible label is the accessible name, aria-pressed announces the current state.
export default function ThemeToggle() {
  const { t } = useI18n()
  const theme = useSyncExternalStore(subscribe, getTheme)
  const dark = theme === 'dark'
  return (
    <button type="button" className="button button-quiet theme-toggle" aria-pressed={dark} onClick={() => saveTheme(dark ? 'light' : 'dark')}>
      {t('theme.dark')}
    </button>
  )
}
