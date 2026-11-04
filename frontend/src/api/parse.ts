// Responses are checked at runtime: TypeScript types say nothing about what the network actually returned
// (frontend rules). Anything unexpected is an error the page shows, never a half-trusted value.
import type { AnalysisStatus, AnalysisView, Me, Risk } from './types'

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
