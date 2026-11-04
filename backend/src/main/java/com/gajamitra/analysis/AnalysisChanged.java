package com.gajamitra.analysis;

import java.util.UUID;

/**
 * Something changed for an analysis (its status, risk, an alert, an approval). Published inside the transaction that
 * made the change; {@link AnalysisEvents} tells the dashboard after commit. It carries no data on purpose: the
 * dashboard re-reads the analysis, so it can never act on a stale or partial copy.
 */
public record AnalysisChanged(UUID analysisId, String kind) {
}
