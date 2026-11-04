import { useState } from 'react'
import { currentTheme, saveTheme, type Theme } from '../theme'

export default function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme)
  const next: Theme = theme === 'dark' ? 'light' : 'dark'
  return (
    <button
      type="button"
      className="button"
      aria-label={`Switch to ${next} theme`}
      onClick={() => {
        saveTheme(next)
        setTheme(next)
      }}
    >
      {theme === 'dark' ? 'Light theme' : 'Dark theme'}
    </button>
  )
}
