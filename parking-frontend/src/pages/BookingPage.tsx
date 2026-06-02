import { Building2, CreditCard, Search, TimerReset } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { ParkingScheme } from '../components/ParkingScheme'
import { EmptyState } from '../components/EmptyState'
import { StatTile } from '../components/StatTile'
import { useAppData } from '../context/AppDataContext'
import { useToast } from '../context/ToastContext'
import { apiRequest } from '../lib/api'
import {
  addMinutes,
  calculateSessionAmount,
  formatDateTime,
  formatDuration,
  formatMoney,
  nextTimeStep,
  statusLabel,
  toDateTimeLocal,
} from '../lib/format'
import type {
  ParkingLayout,
  ParkingLayoutSpot,
  ReservationPolicySettings,
} from '../types/models'

const ALL_ZONES = '__ALL_ZONES__'

const defaultPolicy: ReservationPolicySettings = {
  bookingStepMinutes: 15,
  holdDurationMinutes: 5,
  arrivalDeadlineMinutesBeforeEnd: 15,
  standardCancellationRefundPercent: 60,
  noShowRefundPercent: 0,
  maxBookingDurationMinutes: 720,
  maxBookingAheadDays: 7,
}

function isAlignedToStep(value: string, stepMinutes: number): boolean {
  const date = new Date(value)
  const totalMinutes = Math.floor(date.getTime() / 60000)
  return totalMinutes % stepMinutes === 0
}

function buildDurationOptions(settings: ReservationPolicySettings): number[] {
  const steps = Math.floor(settings.maxBookingDurationMinutes / settings.bookingStepMinutes)
  return Array.from({ length: steps }, (_, index) => (index + 1) * settings.bookingStepMinutes)
}

export function BookingPage() {
  const { wallet, bookings, reservationCatalog, refreshReservationCatalog, bookSpot } = useAppData()
  const { showToast } = useToast()
  const [policy, setPolicy] = useState<ReservationPolicySettings>(defaultPolicy)
  const [start, setStart] = useState(() =>
    toDateTimeLocal(nextTimeStep(new Date(Date.now() + defaultPolicy.bookingStepMinutes * 60 * 1000), defaultPolicy.bookingStepMinutes)),
  )
  const [durationMinutes, setDurationMinutes] = useState(60)
  const [selectedZone, setSelectedZone] = useState(ALL_ZONES)
  const [layout, setLayout] = useState<ParkingLayout | null>(null)
  const [hasSearched, setHasSearched] = useState(false)
  const [searching, setSearching] = useState(false)
  const [bookingCode, setBookingCode] = useState<string | null>(null)
  const [selectedSpotCode, setSelectedSpotCode] = useState<string | null>(null)
  const [policyInitialized, setPolicyInitialized] = useState(false)

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

  useEffect(() => {
    let active = true

    void Promise.all([
      apiRequest<ReservationPolicySettings>('/api/reservation/public/settings'),
      apiRequest<ParkingLayout>('/api/reservation/public/layout'),
    ])
      .then(([nextPolicy, nextLayout]) => {
        if (!active) {
          return
        }
        setPolicy(nextPolicy)
        setLayout(nextLayout)
      })
      .catch((error: unknown) => {
        const message =
          error instanceof Error ? error.message : 'Не удалось загрузить схему парковки'
        showToast(message, 'error')
      })

    return () => {
      active = false
    }
  }, [showToast])

  useEffect(() => {
    if (policyInitialized) {
      return
    }
    const defaultStart = toDateTimeLocal(
      nextTimeStep(new Date(Date.now() + policy.bookingStepMinutes * 60 * 1000), policy.bookingStepMinutes),
    )
    const defaultDuration = Math.min(
      Math.max(policy.bookingStepMinutes * 4, policy.bookingStepMinutes),
      policy.maxBookingDurationMinutes,
    )

    /* eslint-disable react-hooks/set-state-in-effect */
    setStart(defaultStart)
    setDurationMinutes(defaultDuration)
    setPolicyInitialized(true)
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [policy, policyInitialized])

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
  const durationOptions = useMemo(() => buildDurationOptions(policy), [policy])

  const effectiveSelectedZone = useMemo(() => {
    if (selectedZone === ALL_ZONES) {
      return ALL_ZONES
    }
    return zoneOptions.some((zone) => zone.code === selectedZone) ? selectedZone : ALL_ZONES
  }, [selectedZone, zoneOptions])

  const searchPayload = useMemo(() => {
    return {
      from: start,
      to: addMinutes(start, durationMinutes),
      zone: effectiveSelectedZone === ALL_ZONES ? undefined : effectiveSelectedZone,
    }
  }, [durationMinutes, effectiveSelectedZone, start])

  const schemeSpots = useMemo(() => layout?.spots ?? [], [layout?.spots])

  const selectedSpot = useMemo<ParkingLayoutSpot | null>(() => {
    return schemeSpots.find((spot) => spot.code === selectedSpotCode) ?? null
  }, [schemeSpots, selectedSpotCode])

  const selectedSpotAmount = useMemo(() => {
    return selectedSpot
      ? calculateSessionAmount(selectedSpot.price, durationMinutes, policy.bookingStepMinutes)
      : 0
  }, [durationMinutes, policy.bookingStepMinutes, selectedSpot])

  const isSelectedSpotInsufficient = useMemo(() => {
    if (!selectedSpot) {
      return false
    }
    return Number(wallet?.balance ?? 0) < selectedSpotAmount
  }, [selectedSpot, selectedSpotAmount, wallet?.balance])

  const availableSpotCount = useMemo(() => {
    return schemeSpots.filter((spot) => spot.available).length
  }, [schemeSpots])

  function validateSearch() {
    const fromDate = new Date(searchPayload.from)
    const toDate = new Date(searchPayload.to)
    const now = new Date()

    if (fromDate <= now) {
      throw new Error('Начало брони должно быть позже текущего времени')
    }
    if (durationMinutes > policy.maxBookingDurationMinutes) {
      throw new Error(
        `Максимальная длительность брони - ${formatDuration(policy.maxBookingDurationMinutes)}`,
      )
    }
    if (
      fromDate.getTime() >
      now.getTime() + policy.maxBookingAheadDays * 24 * 60 * 60 * 1000
    ) {
      throw new Error(
        `Бронирование вперёд доступно максимум на ${policy.maxBookingAheadDays} дн.`,
      )
    }
    if (
      !isAlignedToStep(searchPayload.from, policy.bookingStepMinutes) ||
      !isAlignedToStep(searchPayload.to, policy.bookingStepMinutes) ||
      durationMinutes % policy.bookingStepMinutes !== 0
    ) {
      throw new Error(
        `Шаг бронирования должен быть кратен ${policy.bookingStepMinutes} минутам`,
      )
    }
    if (toDate <= fromDate) {
      throw new Error('Окончание брони должно быть позже начала')
    }
  }

  async function loadLayout(withInterval: boolean) {
    const search = new URLSearchParams()
    if (withInterval) {
      search.set('from', searchPayload.from)
      search.set('to', searchPayload.to)
      if (searchPayload.zone) {
        search.set('zone', searchPayload.zone)
      }
    }

    return apiRequest<ParkingLayout>(
      `/api/reservation/public/layout${search.size > 0 ? `?${search.toString()}` : ''}`,
    )
  }

  async function handleSearch() {
    try {
      validateSearch()
      setSearching(true)
      const nextLayout = await loadLayout(true)
      setLayout(nextLayout)
      setHasSearched(true)
      setSelectedSpotCode(nextLayout.spots.find((spot) => spot.available)?.code ?? null)
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
        showToast('Место удержано на время подтверждения', 'success')
      } else if (booking.status === 'HOLD_FAILED') {
        showToast('Не удалось удержать средства для брони', 'error')
      } else {
        showToast(`Бронь создана: ${statusLabel(booking.status)}`, 'success')
      }

      const nextLayout = await loadLayout(true)
      setLayout(nextLayout)
      setSelectedSpotCode(nextLayout.spots.find((spot) => spot.available)?.code ?? null)
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
          <h3 className="booking-hero__title">Схема свободных и занятых мест для выбранного интервала</h3>
          <p className="booking-hero__text">
            До поиска показывается текущая занятость. После поиска схема переключается на выбранный
            интервал и оставляет кликабельными только доступные места.
          </p>
        </div>

        <div className="booking-rule-list">
          <div className="booking-rule-card">
            <Building2 size={18} />
            <div>
              <strong>Все места на схеме</strong>
              <span>Серым отмечены недоступные места, зелёным — доступные к бронированию.</span>
            </div>
          </div>
          <div className="booking-rule-card">
            <TimerReset size={18} />
            <div>
              <strong>Шаг {policy.bookingStepMinutes} минут</strong>
              <span>Максимум за одну сессию — {formatDuration(policy.maxBookingDurationMinutes)}.</span>
            </div>
          </div>
          <div className="booking-rule-card">
            <CreditCard size={18} />
            <div>
              <strong>Отмена {policy.standardCancellationRefundPercent}%</strong>
              <span>
                Неявка наступает за {policy.arrivalDeadlineMinutesBeforeEnd} минут до конца брони.
              </span>
            </div>
          </div>
        </div>
      </section>

      <div className="booking-workspace">
        <section className="panel panel--elevated">
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

            <label className="field field--compact">
              <span>Выберите зону</span>
              <select
                value={effectiveSelectedZone}
                onChange={(event) => setSelectedZone(event.target.value)}
              >
                <option value={ALL_ZONES}>Все зоны</option>
                {zoneOptions.map((zone) => (
                  <option key={zone.code} value={zone.code}>
                    {zone.name ? `${zone.code} · ${zone.name}` : zone.code}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </section>

        <section className="panel panel--elevated">
          <div className="panel__header">
            <div>
              <h3 className="panel__title">Выбранное место</h3>
              <p className="panel__meta">
                {hasSearched ? `${availableSpotCount} доступно` : 'Сначала выберите интервал'}
              </p>
            </div>
          </div>

          {!selectedSpot ? (
            <EmptyState
              title={hasSearched ? 'Выберите место на схеме' : 'Поиск ещё не выполнен'}
              description={
                hasSearched
                  ? 'После выбора места здесь появится расчёт суммы и действие бронирования.'
                  : 'После поиска схема переключится на выбранный интервал.'
              }
            />
          ) : (
            <div className="booking-selection__body">
              <div className="selected-spot-card">
                <div className="selected-spot-card__code">{selectedSpot.code}</div>
                <div className="selected-spot-card__meta">
                  <span>{selectedSpot.zoneName ?? selectedSpot.zone ?? 'Без зоны'}</span>
                  <span>{selectedSpot.level ?? 'Без уровня'}</span>
                </div>
              </div>

              <dl className="booking-detail-list">
                <div>
                  <dt>Интервал</dt>
                  <dd>{formatDuration(durationMinutes)}</dd>
                </div>
                <div>
                  <dt>Начало</dt>
                  <dd>{formatDateTime(searchPayload.from)}</dd>
                </div>
                <div>
                  <dt>Стоимость</dt>
                  <dd>{formatMoney(selectedSpotAmount)}</dd>
                </div>
                <div>
                  <dt>Доступно на счёте</dt>
                  <dd>{formatMoney(wallet?.balance)}</dd>
                </div>
              </dl>

              {isSelectedSpotInsufficient ? (
                <div className="booking-warning">
                  Недостаточно средств для удержания суммы. Пополните счёт или выберите другое место.
                </div>
              ) : null}

              <button
                type="button"
                className="primary-button primary-button--wide"
                onClick={() => handleBook(selectedSpot.code)}
                disabled={
                  bookingCode === selectedSpot.code ||
                  isSelectedSpotInsufficient ||
                  !hasSearched ||
                  !selectedSpot.available
                }
              >
                <span>{bookingCode === selectedSpot.code ? 'Бронирование...' : 'Забронировать место'}</span>
              </button>
            </div>
          )}
        </section>
      </div>

      <section className="panel panel--elevated">
        <div className="panel__header">
          <div>
            <h3 className="panel__title">{hasSearched ? 'Схема мест на интервал' : 'Текущая схема мест'}</h3>
            <p className="panel__meta">
              {schemeSpots.length > 0
                ? `${availableSpotCount} из ${schemeSpots.length} доступны`
                : 'Места пока не загружены'}
            </p>
          </div>
        </div>

        {schemeSpots.length === 0 ? (
          <EmptyState title="Схема парковки недоступна" />
        ) : (
          <ParkingScheme
            spots={schemeSpots}
            mode={hasSearched ? 'booking' : 'public'}
            durationMinutes={durationMinutes}
            bookingStepMinutes={policy.bookingStepMinutes}
            selectedSpotCode={selectedSpotCode}
            walletBalance={wallet?.balance}
            onSelectSpotCode={hasSearched ? setSelectedSpotCode : undefined}
          />
        )}
      </section>
    </div>
  )
}
