import type { AlertText } from '../api/types'
import { LANGUAGES } from '../i18n/messages'

// An alert in all three languages, each in its own paragraph with a lang attribute so it is read and shaped correctly.
// The text is written by the AI service: it is rendered as plain text and never as HTML.
export default function AlertTexts({ alert }: { alert: AlertText }) {
  return (
    <div className="alert-texts">
      {LANGUAGES.map((l) => (
        <p key={l.code} lang={l.code}>
          <span className="lang-tag">{l.name}</span> {alert[l.code]}
        </p>
      ))}
    </div>
  )
}
