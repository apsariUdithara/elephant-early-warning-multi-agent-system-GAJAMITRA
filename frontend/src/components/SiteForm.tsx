import { useState, type FormEvent } from 'react'
import { useI18n } from '../i18n/useI18n'
import type { SiteValues } from './siteValues'

// The form for a site's fields, used to create a site and to edit one. Fields are in ./siteValues.
interface Props {
  initial: SiteValues
  submitLabel: string
  onSubmit: (values: SiteValues) => Promise<void>
}

export default function SiteForm({ initial, submitLabel, onSubmit }: Props) {
  const { t } = useI18n()
  const [values, setValues] = useState(initial)
  const [timeout, setTimeoutText] = useState(String(initial.approvalTimeoutS))
  const [working, setWorking] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (working) return
    setWorking(true)
    try {
      await onSubmit({ ...values, approvalTimeoutS: Number(timeout) })
    } finally {
      setWorking(false) // released even if the handler throws
    }
  }

  return (
    <form className="panel new-form" onSubmit={(e) => void submit(e)}>
      <label>
        {t('admin.field.name')}
        <input required maxLength={120} value={values.name} onChange={(e) => setValues({ ...values, name: e.target.value })} />
      </label>
      <label>
        {t('admin.field.villages')}
        <input maxLength={500} value={values.villages} onChange={(e) => setValues({ ...values, villages: e.target.value })} />
      </label>
      <label>
        {t('admin.field.contact')}
        <input maxLength={200} value={values.officerContact} onChange={(e) => setValues({ ...values, officerContact: e.target.value })} />
      </label>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={values.requiresApproval}
          onChange={(e) => setValues({ ...values, requiresApproval: e.target.checked })}
        />
        {t('admin.field.requiresApproval')}
      </label>
      <label>
        {t('admin.field.timeout')}
        <input type="number" required min={30} max={1800} step={1} value={timeout} onChange={(e) => setTimeoutText(e.target.value)} />
      </label>
      <button type="submit" className="button" disabled={working}>
        {working ? t('admin.working') : submitLabel}
      </button>
    </form>
  )
}
