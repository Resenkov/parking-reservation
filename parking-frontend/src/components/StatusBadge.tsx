import { statusLabel, statusTone } from '../lib/format'

interface StatusBadgeProps {
  status: string
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const tone = statusTone(status)
  return <span className={`status-badge status-badge--${tone}`}>{statusLabel(status)}</span>
}
