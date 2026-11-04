// Light/dark theme. A saved choice wins; otherwise the system preference applies and is followed live.
// index.html sets the first value before paint with the same rules; this module keeps it up to date.
export type Theme = 'light' | 'dark'

const KEY = 'gajamitra.theme'
const listeners = new Set<() => void>()
const systemQuery = () => matchMedia('(prefers-color-scheme: dark)')

export function savedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null // storage blocked (private mode, policy): follow the system preference
  }
}

function systemTheme(): Theme {
  return systemQuery().matches ? 'dark' : 'light'
}

function apply(theme: Theme): void {
  document.documentElement.dataset.theme = theme
  listeners.forEach((listener) => listener())
}

export function getTheme(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function initTheme(): void {
  apply(savedTheme() ?? systemTheme())
  systemQuery().addEventListener('change', () => {
    if (!savedTheme()) apply(systemTheme())
  })
}

export function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    // not persisted; the choice still applies for this visit
  }
  apply(theme)
}
