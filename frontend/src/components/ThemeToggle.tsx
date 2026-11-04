import { useSyncExternalStore } from 'react'
import { getTheme, saveTheme, subscribe } from '../theme'

// A toggle button: the visible label is the accessible name, aria-pressed announces the current state.
export default function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, getTheme)
  const dark = theme === 'dark'
  return (
    <button type="button" className="button" aria-pressed={dark} onClick={() => saveTheme(dark ? 'light' : 'dark')}>
      Dark theme
    </button>
  )
}
