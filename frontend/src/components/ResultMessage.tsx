import { useEffect, useRef } from 'react'

// The result of the last change, in a live region that is always on the page (a region created together with its text is
// often not announced). It sits above the forms so it is on screen after a click, and takes focus when it changes.
export default function ResultMessage({ message }: { message: string | null }) {
  const ref = useRef<HTMLDivElement | null>(null)
  useEffect(() => {
    if (message) ref.current?.focus()
  }, [message])
  return (
    <div role="status" tabIndex={-1} ref={ref}>
      {message && <p className="notice">{message}</p>}
    </div>
  )
}
