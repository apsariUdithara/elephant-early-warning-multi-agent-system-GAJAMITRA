// Types shared with the backend (Spring DTOs, plan Phase 3) and ai-service. Written once here; no `any` at the boundary.
// Risk levels match policy/policy_table.json and track.py.
export type Risk = 'low' | 'medium' | 'high' | 'critical'

export type Role = 'ADMIN' | 'OFFICER'

/** GET /api/me */
export interface Me {
  username: string
  role: Role
}

export type AnalysisStatus = 'QUEUED' | 'RUNNING' | 'AWAITING_APPROVAL' | 'COMPLETED' | 'FAILED'

/** One line of GET /api/analyses. The tracker risk is null until the AI service has recorded it. */
export interface AnalysisView {
  id: string
  siteId: string
  status: AnalysisStatus
  risk: Risk | null
  createdAt: string
}

/** One site in GET /api/sites (officers may read the list; changes and villagers stay with admins). */
export interface SiteView {
  id: string
  name: string
  mode: 'SHADOW' | 'LIVE'
}

/** GET /api/sites (admin only): everything about a site. */
export interface SiteFull extends SiteView {
  villages: string
  officerContact: string
  requiresApproval: boolean
  approvalTimeoutS: number
}

export type VillagerLanguage = 'EN' | 'SI' | 'TA'

/** A villager who receives SMS for a site (admin only: the phone number is personal data). */
export interface Subscriber {
  id: string
  phone: string
  language: VillagerLanguage
}

export type Severity = 'advisory' | 'warning' | 'danger'
export type DeliveryState = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED_SHADOW'

/** One alert in all three languages. The text is untrusted AI output: always rendered as plain text. */
export interface AlertText {
  severity: Severity
  en: string
  si: string
  ta: string
}

/** An alert that was accepted and stored. `deliveries` counts villagers per SMS state; never phone numbers. */
export interface AlertView extends AlertText {
  stage: 'NOW' | 'ESCALATION'
  shadow: boolean
  createdAt: string
  deliveries: Partial<Record<DeliveryState, number>>
}

/** GET /api/analyses/{id}. `notes` are the guard's and verifier's decisions, as text. */
export interface AnalysisDetail extends AnalysisView {
  failureReason: string | null
  notes: string[]
  alerts: AlertView[]
  officerReport: string | null
}

/** GET /api/approvals. `proposedAlerts` is null when the stored text could not be read. */
export interface ApprovalView {
  id: string
  analysisId: string
  expiresAt: string
  proposedAlerts: AlertText[] | null
  /** Server clock minus the browser clock when the list arrived: countdowns use server time (see ApprovalsPage). */
  clockOffsetMs: number
  /** How many frames the agents looked at are stored: /api/analyses/{analysisId}/frames/0 to frames-1. */
  frames: number
}
