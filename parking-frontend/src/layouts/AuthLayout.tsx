import { CarFront, ChevronRight, ShieldCheck } from 'lucide-react'
import { type PropsWithChildren, useEffect, useMemo, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { ParkingScheme } from '../components/ParkingScheme'
import { apiRequest } from '../lib/api'
import { floorLabel } from '../lib/format'
import type { ParkingLayout, ReservationPolicySettings } from '../types/models'

const defaultPolicy: ReservationPolicySettings = {
  bookingStepMinutes: 15,
  holdDurationMinutes: 5,
  arrivalDeadlineMinutesBeforeEnd: 15,
  standardCancellationRefundPercent: 60,
  noShowRefundPercent: 0,
  maxBookingDurationMinutes: 720,
  maxBookingAheadDays: 7,
}

export function AuthLayout({ children }: PropsWithChildren) {
  const location = useLocation()
  const isRegister = location.pathname === '/register'
  const [layout, setLayout] = useState<ParkingLayout | null>(null)
  const [policy, setPolicy] = useState<ReservationPolicySettings>(defaultPolicy)

  useEffect(() => {
    let active = true

    void Promise.all([
      apiRequest<ParkingLayout>('/api/reservation/public/layout'),
      apiRequest<ReservationPolicySettings>('/api/reservation/public/settings'),
    ])
      .then(([nextLayout, nextPolicy]) => {
        if (!active) {
          return
        }
        setLayout(nextLayout)
        setPolicy(nextPolicy)
      })
      .catch(() => {
        // Keep auth screen usable even when public preview is temporarily unavailable.
      })

    return () => {
      active = false
    }
  }, [])

  const previewLevel = useMemo(() => {
    const levels = (layout?.spots ?? [])
      .map((spot) => spot.level)
      .filter((level): level is string => Boolean(level))
      .sort((left, right) => left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' }))
    return levels[0] ?? null
  }, [layout?.spots])

  const previewSpots = useMemo(() => {
    if (!layout) {
      return []
    }
    if (!previewLevel) {
      return layout.spots
    }
    return layout.spots.filter((spot) => spot.level === previewLevel)
  }, [layout, previewLevel])

  const availableNow = previewSpots.filter((spot) => spot.available).length
  const occupiedNow = previewSpots.length - availableNow

  return (
    <div className="auth-page">
      <div className="auth-shell">
        <section className="auth-card">
          <div className="brand-lockup">
            <div className="brand-lockup__mark">
              <CarFront size={20} />
            </div>
            <div>
              <p className="eyebrow">Городской паркинг</p>
              <h1 className="page-title">{isRegister ? 'Регистрация' : 'Вход'}</h1>
            </div>
          </div>

          <div className="auth-tabs" role="tablist" aria-label="Аутентификация">
            <NavLink
              to="/login"
              className={({ isActive }) => (isActive ? 'auth-tab auth-tab--active' : 'auth-tab')}
            >
              Вход
            </NavLink>
            <NavLink
              to="/register"
              className={({ isActive }) => (isActive ? 'auth-tab auth-tab--active' : 'auth-tab')}
            >
              Регистрация
            </NavLink>
          </div>

          {children}
        </section>

        <aside className="auth-visual" aria-hidden="true">
          <div className="auth-visual__scene">
            {previewSpots.length > 0 ? (
              <div className="auth-visual__scheme">
                <ParkingScheme spots={previewSpots} mode="public" compact />
              </div>
            ) : (
              <div className="auth-visual__placeholder">Схема парковки загружается</div>
            )}
          </div>

          <div className="auth-visual__content">
            <div className="auth-kicker">
              <ShieldCheck size={18} />
              <span>Свободные и занятые места видны до авторизации</span>
            </div>
            <h2 className="auth-visual__title">Схема парковки по текущей занятости</h2>
            <div className="auth-metrics">
              <div className="metric-tile">
                <span>{previewLevel ? floorLabel(previewLevel) : 'Текущий уровень'}</span>
                <strong>{availableNow}</strong>
              </div>
              <div className="metric-tile">
                <span>Занято сейчас</span>
                <strong>{occupiedNow}</strong>
              </div>
            </div>
            <div className="auth-metrics auth-metrics--secondary">
              <div className="metric-tile">
                <span>Шаг брони</span>
                <strong>{policy.bookingStepMinutes} мин</strong>
              </div>
              <div className="metric-tile">
                <span>Крайний заезд</span>
                <strong>за {policy.arrivalDeadlineMinutesBeforeEnd} мин</strong>
              </div>
            </div>
            <div className="auth-inline">
              <span>HOLD на {policy.holdDurationMinutes} минут</span>
              <ChevronRight size={16} />
            </div>
          </div>
        </aside>
      </div>
    </div>
  )
}
