import { StatTile } from '../../components/StatTile'
import { formatMoney } from '../../lib/format'
import type { ParkingLot, ParkingSpot, ParkingZone } from '../../types/models'

interface AdminStatsProps {
  lots: ParkingLot[]
  zones: ParkingZone[]
  spots: ParkingSpot[]
}

export function AdminStats({ lots, zones, spots }: AdminStatsProps) {
  return (
    <section className="stats-grid">
      <StatTile label="Площадок" value={lots.length} />
      <StatTile label="Зон" value={zones.length} />
      <StatTile label="Мест" value={spots.length} />
      <StatTile label="Базовая цена шага" value={formatMoney(spots[0]?.price ?? 0)} />
    </section>
  )
}
