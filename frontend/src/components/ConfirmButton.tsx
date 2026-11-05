import { useId, useRef, useState } from 'react'
import { useI18n } from '../i18n/useI18n'

// A button for a consequential action: the first click asks, stating the consequence; only "Yes, confirm" does it.
// One action at a time. `onConfirm` handles its own errors and reports the result on the page (it should not throw, but
// the button is released either way). Focus follows the steps: into the question, back to the button on cancel; after a
// confirmed action the page moves focus to its result message.
interface Props {
  label: string
  /** A longer accessible name when several identical buttons are on the page (for example "Remove +94…"). */
  ariaLabel?: string
  question: string
  onConfirm: () => Promise<void>
}

export default function ConfirmButton({ label, ariaLabel, question, onConfirm }: Props) {
  const { t } = useI18n()
  const [asking, setAsking] = useState(false)
  const [working, setWorking] = useState(false)
  const running = useRef(false)
  const trigger = useRef<HTMLButtonElement | null>(null)
  const questionRef = useRef<HTMLParagraphElement | null>(null)
  const questionId = useId()

  async function confirm() {
    if (running.current) return
    running.current = true
    setWorking(true)
    try {
      await onConfirm()
    } finally {
      running.current = false
      setWorking(false)
      setAsking(false)
    }
  }

  if (!asking) {
    return (
      <button
        ref={trigger}
        type="button"
        className="button button-quiet"
        aria-label={ariaLabel}
        onClick={() => {
          setAsking(true)
          queueMicrotask(() => questionRef.current?.focus()) // the question is announced and the next Tab is "confirm"
        }}
      >
        {label}
      </button>
    )
  }
  return (
    <div role="group" aria-labelledby={questionId} className="confirm-box">
      <p id={questionId} className="confirm" tabIndex={-1} ref={questionRef}>
        {question}
      </p>
      <div className="actions">
        <button type="button" className="button" disabled={working} onClick={() => void confirm()}>
          {working ? t('admin.working') : t('admin.confirmYes')}
        </button>
        <button
          type="button"
          className="button button-quiet"
          disabled={working}
          onClick={() => {
            setAsking(false)
            queueMicrotask(() => trigger.current?.focus()) // back to the button that opened the question
          }}
        >
          {t('admin.cancel')}
        </button>
      </div>
    </div>
  )
}
