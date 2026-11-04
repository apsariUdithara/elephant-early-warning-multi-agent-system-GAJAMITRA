// Responses are checked at runtime: TypeScript types say nothing about what the network actually returned
// (frontend rules). Anything unexpected is an error the page shows, never a half-trusted value.
import type {
  AlertText,
  AlertView,
  AnalysisDetail,
  AnalysisStatus,
  AnalysisView,
  ApprovalView,
  DeliveryState,
  Me,
  Risk,
  Severity,
  SiteView,
} from './types'

export class UnexpectedResponse extends Error {}

const RISKS: readonly string[] = ['low', 'medium', 'high', 'critical']
const STATUSES: readonly string[] = ['QUEUED', 'RUNNING', 'AWAITING_APPROVAL', 'COMPLETED', 'FAILED']

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export function parseMe(value: unknown): Me {
  if (isRecord(value) && typeof value.username === 'string' && (value.role === 'ADMIN' || value.role === 'OFFICER')) {
    return { username: value.username, role: value.role }
  }
  throw new UnexpectedResponse('me')
}

function parseAnalysis(value: unknown): AnalysisView {
  if (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.siteId === 'string' &&
    typeof value.status === 'string' &&
    STATUSES.includes(value.status) &&
    (value.risk === null || (typeof value.risk === 'string' && RISKS.includes(value.risk))) &&
    typeof value.createdAt === 'string'
  ) {
    return {
      id: value.id,
      siteId: value.siteId,
      status: value.status as AnalysisStatus,
      risk: value.risk as Risk | null,
      createdAt: value.createdAt,
    }
  }
  throw new UnexpectedResponse('analysis')
}

export function parseAnalysisList(value: unknown): AnalysisView[] {
  if (!Array.isArray(value)) throw new UnexpectedResponse('analyses')
  return value.map(parseAnalysis)
}

export function parseSites(value: unknown): SiteView[] {
  if (!Array.isArray(value)) throw new UnexpectedResponse('sites')
  return value.map((v) => {
    if (isRecord(v) && typeof v.id === 'string' && typeof v.name === 'string' && (v.mode === 'SHADOW' || v.mode === 'LIVE')) {
      return { id: v.id, name: v.name, mode: v.mode }
    }
    throw new UnexpectedResponse('site')
  })
}

/** The answer to POST /api/analyses: the id of the queued analysis. */
export function parseCreatedId(value: unknown): string {
  if (isRecord(value) && typeof value.id === 'string' && value.id !== '') return value.id
  throw new UnexpectedResponse('created analysis')
}

const SEVERITIES: readonly string[] = ['advisory', 'warning', 'danger']
const DELIVERY_STATES: readonly string[] = ['PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED_SHADOW']

function parseAlertText(value: unknown): AlertText {
  if (
    isRecord(value) &&
    typeof value.severity === 'string' &&
    SEVERITIES.includes(value.severity) &&
    typeof value.en === 'string' &&
    typeof value.si === 'string' &&
    typeof value.ta === 'string'
  ) {
    return { severity: value.severity as Severity, en: value.en, si: value.si, ta: value.ta }
  }
  throw new UnexpectedResponse('alert')
}

function parseAlert(value: unknown): AlertView {
  if (
    isRecord(value) &&
    (value.stage === 'NOW' || value.stage === 'ESCALATION') &&
    typeof value.shadow === 'boolean' &&
    typeof value.createdAt === 'string' &&
    isRecord(value.deliveries)
  ) {
    const deliveries: Partial<Record<DeliveryState, number>> = {}
    for (const [state, count] of Object.entries(value.deliveries)) {
      if (!DELIVERY_STATES.includes(state) || typeof count !== 'number') throw new UnexpectedResponse('delivery')
      deliveries[state as DeliveryState] = count
    }
    return { ...parseAlertText(value), stage: value.stage, shadow: value.shadow, createdAt: value.createdAt, deliveries }
  }
  throw new UnexpectedResponse('alert')
}

export function parseAnalysisDetail(value: unknown): AnalysisDetail {
  if (!isRecord(value) || !Array.isArray(value.alerts)) throw new UnexpectedResponse('analysis detail')
  const base = parseAnalysis(value)
  const notes = isRecord(value.result) && Array.isArray(value.result.notes) ? value.result.notes : []
  return {
    ...base,
    failureReason: typeof value.failureReason === 'string' ? value.failureReason : null,
    notes: notes.filter((n): n is string => typeof n === 'string'),
    alerts: value.alerts.map(parseAlert),
    officerReport: typeof value.officerReport === 'string' ? value.officerReport : null,
  }
}

function parseApproval(value: unknown): ApprovalView {
  if (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.analysisId === 'string' &&
    typeof value.expiresAt === 'string' &&
    !Number.isNaN(Date.parse(value.expiresAt)) &&
    typeof value.serverNow === 'string' &&
    !Number.isNaN(Date.parse(value.serverNow))
  ) {
    let proposed: AlertText[] | null = null
    try {
      proposed = Array.isArray(value.proposedAlerts) ? value.proposedAlerts.map(parseAlertText) : null
    } catch {
      proposed = null // show "could not be shown" and the link, never a half-read alert
    }
    return {
      id: value.id,
      analysisId: value.analysisId,
      expiresAt: value.expiresAt,
      proposedAlerts: proposed,
      clockOffsetMs: Date.parse(value.serverNow) - Date.now(),
    }
  }
  throw new UnexpectedResponse('approval')
}

export function parseApprovalList(value: unknown): ApprovalView[] {
  if (!Array.isArray(value)) throw new UnexpectedResponse('approvals')
  return value.map(parseApproval)
}
