// The fields an admin sets on a site. The approval time limit is bounded (30 to 1800 s) so an escalation can never be held
// for long; the server enforces the same range.
export interface SiteValues {
  name: string
  villages: string
  officerContact: string
  requiresApproval: boolean
  approvalTimeoutS: number
}

/** A new site: escalations wait for an officer for two minutes, as the server's default. */
export const NEW_SITE: SiteValues = { name: '', villages: '', officerContact: '', requiresApproval: true, approvalTimeoutS: 120 }
