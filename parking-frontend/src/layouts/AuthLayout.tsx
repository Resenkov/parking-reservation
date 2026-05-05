import { CarFront, ChevronRight, ShieldCheck } from 'lucide-react'
import type { PropsWithChildren } from 'react'
import { NavLink, useLocation } from 'react-router-dom'

export function AuthLayout({ children }: PropsWithChildren) {
  const location = useLocation()
  const isRegister = location.pathname === '/register'

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
            <div className="garage-lane garage-lane--top" />
            <div className="garage-lane garage-lane--bottom" />
            <div className="garage-grid">
              {Array.from({ length: 18 }).map((_, index) => (
                <span
                  key={index}
                  className={
                    index === 2 || index === 7 || index === 12
                      ? 'garage-slot garage-slot--filled'
                      : 'garage-slot'
                  }
                />
              ))}
            </div>
          </div>

          <div className="auth-visual__content">
            <div className="auth-kicker">
              <ShieldCheck size={18} />
              <span>Единый доступ к бронированию и счёту</span>
            </div>
            <h2 className="auth-visual__title">Бронирование парковки без лишних шагов</h2>
            <div className="auth-metrics">
              <div className="metric-tile">
                <span>Шаг брони</span>
                <strong>15 мин</strong>
              </div>
              <div className="metric-tile">
                <span>Окно прибытия</span>
                <strong>15 мин</strong>
              </div>
            </div>
            <div className="auth-inline">
              <span>Городская сеть парковок</span>
              <ChevronRight size={16} />
            </div>
          </div>
        </aside>
      </div>
    </div>
  )
}
