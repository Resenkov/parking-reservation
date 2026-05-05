import {
  ArrowRight,
  Building2,
  CreditCard,
  Search,
  TimerReset,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { EmptyState } from '../components/EmptyState'
import { StatTile } from '../components/StatTile'
import { useAppData } from '../context/AppDataContext'
import { useToast } from '../context/ToastContext'
import {
  addMinutes,
  calculateSessionAmount,
  floorLabel,
  formatDateTime,
  formatDuration,
  formatMoney,
  nextQuarterHour,
  statusLabel,
  toDateTimeLocal,
} from '../lib/format'
import type { ReservationZoneOption, SpotAvailability, SpotSearchInput } from '../types/models'

const durationOptions = Array.from({ length: 48 }, (_, index) => (index + 1) * 15)
const ALL_ZONES = '__ALL_ZONES__'

interface ParkingDeck {
  code: string
  name: string | null
  level: string | null
  availableSpots: number
  leftSpots: SpotAvailability[]
  rightSpots: SpotAvailability[]
}

function zoneLabel(zone: ReservationZoneOption): string {
  return `Зона ${zone.code}`
}

function zoneDescription(zone: ReservationZoneOption): string {
  const floor = floorLabel(zone.level)
  if (zone.name && zone.name !== zone.code) {
    return `${zone.name} · ${floor}`
  }
  return floor
}

function splitDeckSpots(spots: SpotAvailability[]) {
  const midpoint = Math.ceil(spots.length / 2)
  return {
    leftSpots: spots.slice(0, midpoint),
    rightSpots: spots.slice(midpoint),
  }
}

export function BookingPage() {
  const {
    wallet,
    bookings,
    reservationCatalog,
    refreshReservationCatalog,
    searchAvailableSpots,
    bookSpot,
  } = useAppData()
  const { showToast } = useToast()
  const [start, setStart] = useState(() =>
    toDateTimeLocal(nextQuarterHour(new Date(Date.now() + 15 * 60 * 1000))),
  )
  const [durationMinutes, setDurationMinutes] = useState(60)
  const [selectedZone, setSelectedZone] = useState(ALL_ZONES)
  const [spots, setSpots] = useState<SpotAvailability[]>([])
  const [hasSearched, setHasSearched] = useState(false)
  const [searching, setSearching] = useState(false)
  const [bookingCode, setBookingCode] = useState<string | null>(null)
  const [selectedSpotCode, setSelectedSpotCode] = useState<string | null>(null)

  useEffect(() => {
    if (reservationCatalog) {
      return
    }
    void refreshReservationCatalog().catch((error: unknown) => {
      const message =
        error instanceof Error ? error.message : 'Не удалось загрузить каталог парковки'
      showToast(message, 'error')
    })
  }, [refreshReservationCatalog, reservationCatalog, showToast])

  const nextBooking = useMemo(() => {
    return [...bookings]
      .filter((booking) => ['HOLD', 'CONFIRMED', 'ACTIVE'].includes(booking.status))
      .sort((left, right) => new Date(left.startTime).getTime() - new Date(right.startTime).getTime())[0]
  }, [bookings])

  const activeBookingsCount = useMemo(() => {
    return bookings.filter((booking) => ['HOLD', 'CONFIRMED', 'ACTIVE'].includes(booking.status))
      .length
  }, [bookings])

  const zoneOptions = useMemo(() => reservationCatalog?.zones ?? [], [reservationCatalog])

  const effectiveSelectedZone = useMemo(() => {
    if (selectedZone === ALL_ZONES) {
      return ALL_ZONES
    }
    return zoneOptions.some((zone) => zone.code === selectedZone) ? selectedZone : ALL_ZONES
  }, [selectedZone, zoneOptions])

  const searchPayload = useMemo<SpotSearchInput>(() => {
    return {
      from: start,
      to: addMinutes(start, durationMinutes),
      zone: effectiveSelectedZone === ALL_ZONES ? undefined : effectiveSelectedZone,
    }
  }, [durationMinutes, effectiveSelectedZone, start])

  const zoneLookup = useMemo(() => {
    return new Map(zoneOptions.map((zone) => [zone.code, zone]))
  }, [zoneOptions])

  const selectedSpot = useMemo(() => {
    return spots.find((spot) => spot.code === selectedSpotCode) ?? null
  }, [selectedSpotCode, spots])

  const selectedSpotAmount = useMemo(() => {
    return selectedSpot ? calculateSessionAmount(selectedSpot.price, durationMinutes) : 0
  }, [durationMinutes, selectedSpot])

  const isSelectedSpotInsufficient = useMemo(() => {
    if (!selectedSpot) {
      return false
    }
    return Number(wallet?.balance ?? 0) < selectedSpotAmount
  }, [selectedSpot, selectedSpotAmount, wallet?.balance])

  const parkingDecks = useMemo<ParkingDeck[]>(() => {
    if (!hasSearched || spots.length === 0) {
      return []
    }

    const grouped = new Map<string, SpotAvailability[]>()
    for (const spot of spots) {
      const code = spot.zone ?? 'Без зоны'
      const existing = grouped.get(code)
      if (existing) {
        existing.push(spot)
      } else {
        grouped.set(code, [spot])
      }
    }

    const zoneOrder = new Map(zoneOptions.map((zone, index) => [zone.code, index]))

    return Array.from(grouped.entries())
      .map(([code, zoneSpots]) => {
        const orderedSpots = [...zoneSpots].sort((left, right) => left.code.localeCompare(right.code))
        const split = splitDeckSpots(orderedSpots)
        const zoneMeta = zoneLookup.get(code)

        return {
          code,
          name: zoneMeta?.name ?? null,
          level: zoneMeta?.level ?? orderedSpots[0]?.level ?? null,
          availableSpots: orderedSpots.length,
          leftSpots: split.leftSpots,
          rightSpots: split.rightSpots,
        }
      })
      .sort((left, right) => {
        const leftOrder = zoneOrder.get(left.code) ?? Number.MAX_SAFE_INTEGER
        const rightOrder = zoneOrder.get(right.code) ?? Number.MAX_SAFE_INTEGER
        if (leftOrder !== rightOrder) {
          return leftOrder - rightOrder
        }
        return left.code.localeCompare(right.code)
      })
  }, [hasSearched, spots, zoneLookup, zoneOptions])

  function validateSearch() {
    const fromDate = new Date(searchPayload.from)
    const toDate = new Date(searchPayload.to)
    const now = new Date()

    if (fromDate <= now) {
      throw new Error('Начало брони должно быть позже текущего времени')
    }
    if (durationMinutes > 720) {
      throw new Error('Максимальная длительность брони - 12 часов')
    }
    if (fromDate.getTime() > now.getTime() + 7 * 24 * 60 * 60 * 1000) {
      throw new Error('Бронирование вперёд доступно максимум на 7 дней')
    }
    if (fromDate.getMinutes() % 15 !== 0 || toDate.getMinutes() % 15 !== 0) {
      throw new Error('Шаг бронирования должен быть кратен 15 минутам')
    }
  }

  async function handleSearch() {
    try {
      validateSearch()
      setSearching(true)
      const nextSpots = await searchAvailableSpots(searchPayload)
      setHasSearched(true)
      setSpots(nextSpots)
      setSelectedSpotCode(nextSpots[0]?.code ?? null)
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось выполнить поиск', 'error')
    } finally {
      setSearching(false)
    }
  }

  async function handleBook(spotCode: string) {
    try {
      setBookingCode(spotCode)
      const booking = await bookSpot({
        spotCode,
        from: searchPayload.from,
        to: searchPayload.to,
      })
      if (booking.status === 'HOLD') {
        showToast('Место удержано на 5 минут', 'success')
        return
      }
      if (booking.status === 'HOLD_FAILED') {
        showToast('Не удалось удержать средства для брони', 'error')
        return
      }
      showToast(`Бронь создана: ${statusLabel(booking.status)}`, 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось создать бронь', 'error')
    } finally {
      setBookingCode(null)
    }
  }

  return (
    <div className="page-grid booking-page">
      <section className="stats-grid">
        <StatTile label="Доступно на счёте" value={formatMoney(wallet?.balance)} />
        <StatTile label="Удержано" value={formatMoney(wallet?.heldAmount)} />
        <StatTile label="Активные брони" value={activeBookingsCount} />
        <StatTile
          label="Следующий заезд"
          value={nextBooking ? `${nextBooking.spotCode} · ${formatDateTime(nextBooking.startTime)}` : 'Нет'}
        />
      </section>

      <section className="panel panel--elevated booking-hero">
        <div className="booking-hero__copy">
          <p className="eyebrow">Навигация по парковке</p>
          <h3 className="booking-hero__title">Выберите зону и место на схеме парковки</h3>
          <p className="booking-hero__text">
            Для пользователя остаются только зоны. Каждая зона соответствует своему этажу,
            а свободные места показываются на схеме с проездом и парковочными рядами.
          </p>
        </div>

        <div className="booking-rule-list">
          <div className="booking-rule-card">
            <Building2 size={18} />
            <div>
              <strong>Зоны A, B, C</strong>
              <span>Выбор выполняется только по существующим зонам парковки</span>
            </div>
          </div>
          <div className="booking-rule-card">
            <TimerReset size={18} />
            <div>
              <strong>Шаг 15 минут</strong>
              <span>Интервал и тарификация кратны 15 минутам</span>
            </div>
          </div>
          <div className="booking-rule-card">
            <CreditCard size={18} />
            <div>
              <strong>HOLD на 5 минут</strong>
              <span>Сумма удерживается на время подтверждения брони</span>
            </div>
          </div>
        </div>
      </section>

      <div className="booking-workspace">
        <section className="panel panel--elevated booking-controls">
          <div className="panel__header">
            <div>
              <h3 className="panel__title">Параметры поиска</h3>
              <p className="panel__meta">
                {formatDateTime(searchPayload.from)} - {formatDateTime(searchPayload.to)} ·{' '}
                {formatDuration(durationMinutes)}
              </p>
            </div>

            <button
              type="button"
              className="primary-button"
              onClick={handleSearch}
              disabled={searching}
            >
              <Search size={16} />
              <span>{searching ? 'Поиск...' : 'Найти места'}</span>
            </button>
          </div>

          <div className="field-grid field-grid--two booking-controls__grid">
            <label className="field">
              <span>Начало</span>
              <input
                type="datetime-local"
                value={start}
                onChange={(event) => setStart(event.target.value)}
              />
            </label>

            <label className="field">
              <span>Длительность</span>
              <select
                value={durationMinutes}
                onChange={(event) => setDurationMinutes(Number(event.target.value))}
              >
                {durationOptions.map((option) => (
                  <option key={option} value={option}>
                    {formatDuration(option)}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="booking-filter-group">
            <div className="booking-filter-group__header">
              <Building2 size={16} />
              <span>Зона парковки</span>
            </div>
            <label className="field">
              <span>Выберите существующую зону</span>
              <select
                value={effectiveSelectedZone}
                onChange={(event) => setSelectedZone(event.target.value)}
              >
                <option value={ALL_ZONES}>Все зоны</option>
                {zoneOptions.map((zone) => (
                  <option key={zone.code} value={zone.code}>
                    {zoneLabel(zone)} · {zoneDescription(zone)}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </section>

        <aside className="panel panel--elevated panel--accent booking-selection">
          <div className="panel__header">
            <div>
              <h3 className="panel__title">Выбранное место</h3>
              <p className="panel__meta">
                {selectedSpot ? `${selectedSpot.code} · ${formatDuration(durationMinutes)}` : 'Пока не выбрано'}
              </p>
            </div>
          </div>

          {!selectedSpot ? (
            <EmptyState
              title="Выберите место на схеме"
              description="После поиска нажмите на парковочное место, чтобы увидеть стоимость и подтвердить бронь."
            />
          ) : (
            <div className="booking-selection__body">
              <div className="selected-spot-card">
                <div className="selected-spot-card__code">{selectedSpot.code}</div>
                <div className="selected-spot-card__meta">
                  <span>{selectedSpot.zone ? `Зона ${selectedSpot.zone}` : 'Без зоны'}</span>
                  <span>{floorLabel(selectedSpot.level)}</span>
                </div>
              </div>

              <dl className="booking-detail-list">
                <div>
                  <dt>Ставка за 15 минут</dt>
                  <dd>{formatMoney(selectedSpot.price)}</dd>
                </div>
                <div>
                  <dt>Сумма за сессию</dt>
                  <dd>{formatMoney(selectedSpotAmount)}</dd>
                </div>
                <div>
                  <dt>Интервал</dt>
                  <dd>
                    {formatDateTime(searchPayload.from)} - {formatDateTime(searchPayload.to)}
                  </dd>
                </div>
                <div>
                  <dt>Баланс</dt>
                  <dd>{formatMoney(wallet?.balance)}</dd>
                </div>
              </dl>

              {isSelectedSpotInsufficient ? (
                <div className="booking-warning">
                  На счёте недостаточно средств для предавторизации этой брони.
                </div>
              ) : null}

              <button
                type="button"
                className="primary-button primary-button--wide"
                onClick={() => handleBook(selectedSpot.code)}
                disabled={bookingCode === selectedSpot.code || isSelectedSpotInsufficient}
              >
                {bookingCode === selectedSpot.code ? 'Бронирование...' : 'Забронировать место'}
              </button>
            </div>
          )}
        </aside>
      </div>

      <section className="panel panel--elevated">
        <div className="panel__header">
          <div>
            <h3 className="panel__title">Схема свободных мест</h3>
            <p className="panel__meta">{spots.length}</p>
          </div>
        </div>

        {!hasSearched ? (
          <EmptyState
            title="Выберите интервал и запустите поиск"
            description="После этого схема покажет свободные зоны и конкретные парковочные места."
          />
        ) : parkingDecks.length === 0 ? (
          <EmptyState title="Свободные места не найдены" />
        ) : (
          <div className="parking-schematic">
            <div className="parking-legend">
              <span className="parking-legend__item">
                <span className="parking-legend__swatch parking-legend__swatch--free" />
                Свободно
              </span>
              <span className="parking-legend__item">
                <span className="parking-legend__swatch parking-legend__swatch--selected" />
                Выбрано
              </span>
              <span className="parking-legend__item">
                <span className="parking-legend__swatch parking-legend__swatch--limited" />
                Недостаточно средств
              </span>
            </div>

            {parkingDecks.map((deck) => (
              <article key={deck.code} className="parking-deck">
                <div className="parking-deck__header">
                  <div>
                    <span className="parking-deck__badge">{`Зона ${deck.code}`}</span>
                    <h4 className="parking-deck__title">
                      {deck.name && deck.name !== deck.code ? deck.name : floorLabel(deck.level)}
                    </h4>
                    <p className="parking-deck__meta">{floorLabel(deck.level)}</p>
                  </div>

                  <div className="parking-deck__summary">
                    <strong>{deck.availableSpots}</strong>
                    <span>свободных мест</span>
                  </div>
                </div>

                <div className="parking-deck__body">
                  <div className="parking-row parking-row--top">
                    {deck.leftSpots.map((spot) => {
                      const spotAmount = calculateSessionAmount(spot.price, durationMinutes)
                      const isSelected = selectedSpotCode === spot.code
                      const isInsufficient = Number(wallet?.balance ?? 0) < spotAmount

                      return (
                        <button
                          key={spot.id}
                          type="button"
                          className={
                            isSelected
                              ? 'parking-slot parking-slot--selected'
                              : isInsufficient
                                ? 'parking-slot parking-slot--limited'
                                : 'parking-slot'
                          }
                          onClick={() => setSelectedSpotCode(spot.code)}
                        >
                          <span className="parking-slot__code">{spot.code}</span>
                          <span className="parking-slot__price">{formatMoney(spotAmount)}</span>
                        </button>
                      )
                    })}
                  </div>

                  <div className="parking-lane">
                    <div className="parking-lane__entry">
                      <ArrowRight size={16} />
                      <span>Въезд</span>
                    </div>
                    <div className="parking-lane__divider" />
                    <div className="parking-lane__label">Проезд</div>
                  </div>

                  <div className="parking-row parking-row--bottom">
                    {deck.rightSpots.map((spot) => {
                      const spotAmount = calculateSessionAmount(spot.price, durationMinutes)
                      const isSelected = selectedSpotCode === spot.code
                      const isInsufficient = Number(wallet?.balance ?? 0) < spotAmount

                      return (
                        <button
                          key={spot.id}
                          type="button"
                          className={
                            isSelected
                              ? 'parking-slot parking-slot--selected'
                              : isInsufficient
                                ? 'parking-slot parking-slot--limited'
                                : 'parking-slot'
                          }
                          onClick={() => setSelectedSpotCode(spot.code)}
                        >
                          <span className="parking-slot__code">{spot.code}</span>
                          <span className="parking-slot__price">{formatMoney(spotAmount)}</span>
                        </button>
                      )
                    })}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
