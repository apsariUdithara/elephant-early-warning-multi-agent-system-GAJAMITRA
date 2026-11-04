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
