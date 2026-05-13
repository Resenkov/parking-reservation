import { ArrowRight } from 'lucide-react'
import { useMemo } from 'react'
import { calculateSessionAmount, floorLabel, formatMoney } from '../lib/format'
import type { ParkingLayoutSpot } from '../types/models'

interface ParkingDeck {
  code: string
  name: string | null
  level: string | null
  totalSpots: number
  availableSpots: number
  leftSpots: ParkingLayoutSpot[]
  rightSpots: ParkingLayoutSpot[]
}

interface ParkingSchemeProps {
  spots: ParkingLayoutSpot[]
  mode: 'public' | 'booking' | 'admin'
  durationMinutes?: number
  bookingStepMinutes?: number
  selectedSpotCode?: string | null
  walletBalance?: number | null
  onSelectSpotCode?: (spotCode: string) => void
  compact?: boolean
  emptyMessage?: string
}

function splitDeckSpots(spots: ParkingLayoutSpot[]) {
  const midpoint = Math.ceil(spots.length / 2)
  return {
    leftSpots: spots.slice(0, midpoint),
    rightSpots: spots.slice(midpoint),
  }
}

function zoneBadgeLabel(code: string): string {
  return code === 'UNASSIGNED' ? 'Без зоны' : `Зона ${code}`
}

export function ParkingScheme({
  spots,
  mode,
  durationMinutes,
  bookingStepMinutes = 15,
  selectedSpotCode,
  walletBalance,
  onSelectSpotCode,
  compact = false,
  emptyMessage = 'Места не найдены',
}: ParkingSchemeProps) {
  const parkingDecks = useMemo<ParkingDeck[]>(() => {
    if (spots.length === 0) {
      return []
    }

    const grouped = new Map<string, ParkingLayoutSpot[]>()
    for (const spot of spots) {
      const key = spot.zone ?? 'UNASSIGNED'
      const existing = grouped.get(key)
      if (existing) {
        existing.push(spot)
      } else {
        grouped.set(key, [spot])
      }
    }

    return Array.from(grouped.entries())
      .map(([code, zoneSpots]) => {
        const orderedSpots = [...zoneSpots].sort((left, right) =>
          left.code.localeCompare(right.code, undefined, { numeric: true, sensitivity: 'base' }),
        )
        const split = splitDeckSpots(orderedSpots)
        return {
          code,
          name: orderedSpots[0]?.zoneName ?? null,
          level: orderedSpots[0]?.level ?? null,
          totalSpots: orderedSpots.length,
          availableSpots: orderedSpots.filter((spot) => spot.available).length,
          leftSpots: split.leftSpots,
          rightSpots: split.rightSpots,
        }
      })
      .sort((left, right) => {
        const levelCompare = (left.level ?? '').localeCompare(right.level ?? '', undefined, {
          numeric: true,
          sensitivity: 'base',
        })
        if (levelCompare !== 0) {
          return levelCompare
        }
        return left.code.localeCompare(right.code, undefined, { numeric: true, sensitivity: 'base' })
      })
  }, [spots])

  if (parkingDecks.length === 0) {
    return <div className="parking-scheme-empty">{emptyMessage}</div>
  }

  const rootClassName = compact ? 'parking-schematic parking-schematic--compact' : 'parking-schematic'

  const renderSpot = (spot: ParkingLayoutSpot) => {
    const amount =
      mode === 'booking' && durationMinutes !== undefined
        ? calculateSessionAmount(spot.price, durationMinutes, bookingStepMinutes)
        : spot.price
    const isSelected = selectedSpotCode === spot.code
    const isUnavailable = mode === 'admin' ? spot.occupied : !spot.available
    const isInsufficient =
      mode === 'booking' &&
      !isUnavailable &&
      walletBalance !== null &&
      walletBalance !== undefined &&
      durationMinutes !== undefined &&
      walletBalance < amount
    const className = isSelected
      ? 'parking-slot parking-slot--selected'
      : isUnavailable
        ? 'parking-slot parking-slot--unavailable'
        : isInsufficient
          ? 'parking-slot parking-slot--limited'
          : 'parking-slot'
    const disabled = !onSelectSpotCode || (mode !== 'admin' && isUnavailable)

    let subtitle = 'Свободно'
    if (mode === 'booking') {
      subtitle = formatMoney(amount)
    } else if (mode === 'admin') {
      subtitle = formatMoney(spot.price)
    } else if (!spot.available) {
      subtitle = 'Занято'
    }

    let title: string | undefined
    if (mode === 'admin') {
      title = spot.occupied ? 'Место сейчас занято' : 'Место доступно для настройки'
    } else if (isUnavailable) {
      title = 'Недоступно для выбранного интервала'
    }

    return (
      <button
        key={spot.id}
        type="button"
        className={className}
        onClick={() => onSelectSpotCode?.(spot.code)}
        disabled={disabled}
        title={title}
      >
        <span className="parking-slot__code">{spot.code}</span>
        <span className="parking-slot__price">{subtitle}</span>
      </button>
    )
  }

  return (
    <div className={rootClassName}>
      <div className="parking-legend">
        <span className="parking-legend__item">
          <span className="parking-legend__swatch parking-legend__swatch--free" />
          Свободно
        </span>

        {mode === 'booking' ? (
          <>
            <span className="parking-legend__item">
              <span className="parking-legend__swatch parking-legend__swatch--selected" />
              Выбрано
            </span>
            <span className="parking-legend__item">
              <span className="parking-legend__swatch parking-legend__swatch--unavailable" />
              Недоступно
            </span>
            <span className="parking-legend__item">
              <span className="parking-legend__swatch parking-legend__swatch--limited" />
              Недостаточно средств
            </span>
          </>
        ) : mode === 'admin' ? (
          <>
            <span className="parking-legend__item">
              <span className="parking-legend__swatch parking-legend__swatch--selected" />
              Выбрано
            </span>
            <span className="parking-legend__item">
              <span className="parking-legend__swatch parking-legend__swatch--unavailable" />
              Занято сейчас
            </span>
          </>
        ) : (
          <span className="parking-legend__item">
            <span className="parking-legend__swatch parking-legend__swatch--unavailable" />
            Занято
          </span>
        )}
      </div>

      {parkingDecks.map((deck) => (
        <article key={deck.code} className={compact ? 'parking-deck parking-deck--compact' : 'parking-deck'}>
          <div className="parking-deck__header">
            <div>
              <span className="parking-deck__badge">{zoneBadgeLabel(deck.code)}</span>
              <h4 className="parking-deck__title">
                {deck.name && deck.name !== deck.code ? deck.name : floorLabel(deck.level)}
              </h4>
              <p className="parking-deck__meta">{floorLabel(deck.level)}</p>
            </div>

            <div className="parking-deck__summary">
              <strong>{deck.availableSpots}</strong>
              <span>{deck.totalSpots} мест</span>
            </div>
          </div>

          <div className="parking-deck__body">
            <div className="parking-row parking-row--top">{deck.leftSpots.map(renderSpot)}</div>

            <div className="parking-lane">
              <div className="parking-lane__entry">
                <ArrowRight size={16} />
                <span>Въезд</span>
              </div>
              <div className="parking-lane__divider" />
              <div className="parking-lane__label">Проезд</div>
            </div>

            <div className="parking-row parking-row--bottom">{deck.rightSpots.map(renderSpot)}</div>
          </div>
        </article>
      ))}
    </div>
  )
}
