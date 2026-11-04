import { useState, type MouseEvent } from 'react'
import { useI18n } from '../i18n/useI18n'
import type { MessageKey } from '../i18n/messages'

// The village boundary for an analysis: two points on the boundary line, then one point on the village side, each as a
// fraction (0 to 1) of the picture from its left and top edge. That is what the tracker needs (plan Phase 2, `track.py`).
// Points are set by clicking the picture, or typed as percentages: a mouse-only picker would leave keyboard and
// screen-reader users unable to start an analysis. The picture is shown at the clip's own aspect ratio and stretched to
// fill, so a fraction of the picture is exactly a fraction of the frame.
export type Point = readonly [number, number]

const LABELS: MessageKey[] = ['new.point.1', 'new.point.2', 'new.point.3']
const MARKS = ['1', '2', 'V'] // the third mark is the village side

const clamp = (value: number) => Math.min(1, Math.max(0, value))
const percent = (fraction: number) => String(Math.round(fraction * 1000) / 10)

function TypedPoints({ points, onApply }: { points: readonly Point[]; onApply: (points: Point[]) => void }) {
  const { t } = useI18n()
  const [fields, setFields] = useState<string[]>(() => [0, 1, 2].flatMap((i) => [points[i]?.[0], points[i]?.[1]].map((v) => (v === undefined ? '' : percent(v)))))
  const [invalid, setInvalid] = useState(false)

  function apply() {
    const numbers = fields.map((f) => (f.trim() === '' ? NaN : Number(f)))
    if (numbers.some((n) => Number.isNaN(n) || n < 0 || n > 100)) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    onApply([0, 1, 2].map((i) => [numbers[2 * i] / 100, numbers[2 * i + 1] / 100] as const))
  }

  return (
    <details
      className="typed-points"
      onKeyDown={(e) => {
        if (e.key === 'Enter' && e.target instanceof HTMLInputElement) {
          e.preventDefault() // Enter here applies the typed points; it must not submit the form with the old ones
          apply()
        }
      }}
    >
      <summary>{t('new.typed')}</summary>
      {LABELS.map((label, i) => (
        <fieldset key={label}>
          <legend>{t(label)}</legend>
          <label>
            {t('new.axis.x')}
            <input
              type="number"
              inputMode="decimal"
              min={0}
              max={100}
              step={0.1}
              value={fields[2 * i]}
              onChange={(e) => setFields(fields.map((f, j) => (j === 2 * i ? e.target.value : f)))}
            />
          </label>
          <label>
            {t('new.axis.y')}
            <input
              type="number"
              inputMode="decimal"
              min={0}
              max={100}
              step={0.1}
              value={fields[2 * i + 1]}
              onChange={(e) => setFields(fields.map((f, j) => (j === 2 * i + 1 ? e.target.value : f)))}
            />
          </label>
        </fieldset>
      ))}
      {invalid && (
        <p role="alert" className="form-error">
          {t('new.pointsInvalid')}
        </p>
      )}
      <button type="button" className="button" onClick={apply}>
        {t('new.apply')}
      </button>
    </details>
  )
}

interface Props {
  /** An object URL of the chosen clip, or null before one is chosen. */
  videoUrl: string | null
  points: readonly Point[]
  onChange: (points: Point[]) => void
}

export default function BoundaryPicker({ videoUrl, points, onChange }: Props) {
  const { t } = useI18n()
  const [ratio, setRatio] = useState(16 / 9)

  function pick(event: MouseEvent<SVGSVGElement>) {
    if (points.length >= 3) return // a full set is only replaced on purpose, with "Clear the points"
    const box = event.currentTarget.getBoundingClientRect()
    onChange([...points, [clamp((event.clientX - box.left) / box.width), clamp((event.clientY - box.top) / box.height)]])
  }

  return (
    <div>
      <p>{t('new.boundary.help')}</p>
      {videoUrl && (
        <div className="picker" style={{ aspectRatio: String(ratio) }}>
          <video
            src={videoUrl}
            muted
            playsInline
            preload="auto"
            aria-label={t('new.preview')}
            onLoadedMetadata={(e) => {
              const { videoWidth, videoHeight } = e.currentTarget
              if (videoWidth > 0 && videoHeight > 0) setRatio(videoWidth / videoHeight)
            }}
          />
          <svg aria-hidden="true" className="picker-overlay" viewBox="0 0 100 100" preserveAspectRatio="none" data-testid="picker" onClick={pick}>
            {points.length >= 2 && (
              <line
                x1={points[0][0] * 100}
                y1={points[0][1] * 100}
                x2={points[1][0] * 100}
                y2={points[1][1] * 100}
                vectorEffect="non-scaling-stroke"
                className="picker-line"
              />
            )}
          </svg>
          {points.map((p, i) => (
            <span key={i} className="marker" aria-hidden="true" style={{ left: `${p[0] * 100}%`, top: `${p[1] * 100}%` }}>
              {MARKS[i]}
            </span>
          ))}
        </div>
      )}
      <p className="muted" aria-live="polite">
        {points.map((p, i) => `${t(LABELS[i])}: ${percent(p[0])}%, ${percent(p[1])}%`).join(' · ')}
      </p>
      <button type="button" className="button button-quiet" disabled={points.length === 0} onClick={() => onChange([])}>
        {t('new.clear')}
      </button>
      <TypedPoints key={JSON.stringify(points)} points={points} onApply={onChange} />
    </div>
  )
}
