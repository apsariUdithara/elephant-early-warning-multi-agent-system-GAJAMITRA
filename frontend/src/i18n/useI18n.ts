import { createContext, useContext } from 'react'
import type { Lang, MessageKey } from './messages'

export interface I18n {
  lang: Lang
  setLang: (lang: Lang) => void
  /** A complete sentence by key; `{name}` placeholders are filled from `values` (no concatenated fragments). */
  t: (key: MessageKey, values?: Record<string, string | number>) => string
  /** Date and time in the page language's locale (Sri Lanka). */
  formatDateTime: (iso: string) => string
}

export const I18nContext = createContext<I18n | null>(null)

export function useI18n(): I18n {
  const value = useContext(I18nContext)
  if (!value) throw new Error('useI18n needs an I18nProvider')
  return value
}
