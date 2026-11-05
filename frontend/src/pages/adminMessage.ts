import { ApiError } from '../api/client'
import type { MessageKey } from '../i18n/messages'

// What to tell an admin when a change did not work: a message they can act on, by kind of failure. A 400 carries the
// server's own reason (English, shown as plain text); a 409 is a clash with existing data (for example a phone number
// that is already added); a 404 means it no longer exists.
export type Problem = { key: MessageKey; values?: Record<string, string> }

export function problemOf(error: unknown): Problem {
  if (error instanceof ApiError) {
    if (error.status === 400) return { key: 'admin.rejected', values: { detail: error.message } }
    if (error.status === 409) return { key: 'admin.conflict' }
    if (error.status === 404) return { key: 'admin.gone' }
  }
  return { key: 'admin.failed' }
}
