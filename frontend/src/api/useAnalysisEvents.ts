import { useEffect, useState } from 'react'

// Live updates for one analysis (Server-Sent Events, plan Phase 3). An event carries no data: it only says "something
// changed", and the page re-reads the analysis, so a missed, repeated or out-of-order event is harmless. The browser
// reconnects by itself; each reconnect gets a first event from the server, so changes during the gap are caught.
export type Connection = 'connecting' | 'live' | 'reconnecting' | 'closed'

export function useAnalysisEvents(analysisId: string, onChange: () => void): Connection {
  const [connection, setConnection] = useState<Connection>('connecting')

  useEffect(() => {
    const source = new EventSource(`/api/analyses/${encodeURIComponent(analysisId)}/events`)
    source.onopen = () => setConnection('live')
    // A dropped connection is retried by the browser ('reconnecting'). A refused one (not signed in, unknown id) is
    // CLOSED and never retried: say so instead of showing "reconnecting" forever.
    source.onerror = () => setConnection(source.readyState === EventSource.CLOSED ? 'closed' : 'reconnecting')
    source.addEventListener('analysis', onChange)
    return () => source.close() // no stream is left open when the page is left
  }, [analysisId, onChange])

  return connection
}
