import { useEffect, useState } from 'react'

/** The current time in milliseconds, refreshed every `everyMs` (a countdown needs a clock that moves). */
export function useNow(everyMs = 1000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), everyMs)
    return () => clearInterval(timer)
  }, [everyMs])
  return now
}
