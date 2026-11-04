import { useI18n } from '../i18n/useI18n'
import { LANGUAGES } from '../i18n/messages'

// Language names are written in their own language and carry a lang attribute, so screen readers pronounce each one.
export default function LanguageSwitcher() {
  const { lang, setLang, t } = useI18n()
  return (
    <div role="group" aria-label={t('language.label')} className="lang-switch">
      {LANGUAGES.map((l) => (
        <button
          key={l.code}
          type="button"
          lang={l.code}
          className="button button-quiet"
          aria-pressed={lang === l.code}
          onClick={() => setLang(l.code)}
        >
          {l.name}
        </button>
      ))}
    </div>
  )
}
