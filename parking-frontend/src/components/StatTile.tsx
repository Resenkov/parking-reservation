import type { ReactNode } from 'react'

interface StatTileProps {
  label: string
  value: ReactNode
}

export function StatTile({ label, value }: StatTileProps) {
  return (
    <article className="stat-tile">
      <span className="stat-tile__label">{label}</span>
      <strong className="stat-tile__value">{value}</strong>
    </article>
  )
}
