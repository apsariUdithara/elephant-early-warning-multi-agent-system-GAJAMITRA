// Light/dark theme: an explicit user choice is saved; without one the system preference applies (index.css).
export type Theme = 'light' | 'dark'

const KEY = 'gajamitra.theme'

export function savedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null // storage blocked (private mode, policy): fall back to the system preference
  }
}

export function currentTheme(): Theme {
  return savedTheme() ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
}

export function applyTheme(theme: Theme | null): void {
  if (theme) document.documentElement.dataset.theme = theme
  else delete document.documentElement.dataset.theme
}

export function saveTheme(theme: Theme): void {
  applyTheme(theme)
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    // not persisted; the choice still applies for this visit
  }
}
