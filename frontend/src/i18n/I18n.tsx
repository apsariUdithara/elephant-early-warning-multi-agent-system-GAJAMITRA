import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { dictionaries, LANGUAGES, type Lang } from './messages'
import { I18nContext, type I18n } from './useI18n'

// The page language: a saved choice wins, then the browser's language, then English. Kept across visits.
const KEY = 'gajamitra.lang'

function saved(): Lang | null {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'en' || value === 'si' || value === 'ta' ? value : null
  } catch {
    return null // storage blocked: the choice still applies for this visit
  }
}

function initialLang(): Lang {
  const stored = saved()
  if (stored) return stored
  const browser = navigator.language.toLowerCase()
  return browser.startsWith('si') ? 'si' : browser.startsWith('ta') ? 'ta' : 'en'
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang)

  useEffect(() => {
    document.documentElement.lang = lang // screen readers and fonts pick the right language
  }, [lang])

  const value = useMemo<I18n>(() => {
    const locale = LANGUAGES.find((l) => l.code === lang)?.locale ?? 'en'
    return {
      lang,
      setLang: (next) => {
        try {
          localStorage.setItem(KEY, next)
        } catch {
          // not persisted
        }
        setLangState(next)
      },
      t: (key) => dictionaries[lang][key],
      formatDateTime: (iso) => {
        const date = new Date(iso)
        return Number.isNaN(date.getTime())
          ? iso
          : new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
      },
    }
  }, [lang])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}
