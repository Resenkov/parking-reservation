import {
  Building2,
  CalendarRange,
  CarFront,
  CreditCard,
  LogOut,
  Search,
  UserRound,
} from 'lucide-react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { LoadingScreen } from '../components/LoadingScreen'
import { useAppData } from '../context/AppDataContext'
import { useAuth } from '../context/AuthContext'
import { formatMoney } from '../lib/format'

const navigationItems = [
  { to: '/app/book', label: 'Подбор мест', icon: Search },
  { to: '/app/bookings', label: 'Мои брони', icon: CalendarRange },
  { to: '/app/wallet', label: 'Счёт', icon: CreditCard },
  { to: '/app/profile', label: 'Профиль', icon: UserRound },
]

const pageTitles: Record<string, { eyebrow: string; title: string }> = {
  '/app/book': { eyebrow: 'Парковка', title: 'Подбор и бронирование мест' },
  '/app/bookings': { eyebrow: 'История', title: 'Мои бронирования' },
  '/app/wallet': { eyebrow: 'Финансы', title: 'Счёт и операции' },
  '/app/profile': { eyebrow: 'Аккаунт', title: 'Личные данные' },
}

export function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const { signOut } = useAuth()
  const { profile, wallet, isInitializing } = useAppData()

  const pageMeta = pageTitles[location.pathname] ?? pageTitles['/app/book']
  const displayName =
    [profile?.firstName, profile?.lastName].filter(Boolean).join(' ') ||
    profile?.email ||
    'Пользователь'

  function handleSignOut() {
    signOut()
    navigate('/login', { replace: true })
  }

  if (isInitializing && !profile) {
    return <LoadingScreen />
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <div className="brand-lockup__mark brand-lockup__mark--compact">
            <CarFront size={18} />
          </div>
          <div>
            <p className="eyebrow">Городской паркинг</p>
            <strong className="sidebar__name">{displayName}</strong>
          </div>
        </div>

        <div className="sidebar__summary">
          <div className="sidebar__summary-icon">
            <Building2 size={18} />
          </div>
          <div>
            <strong>Единый доступ к парковке</strong>
            <span>Брони, счёт и история в одном приложении</span>
          </div>
        </div>

        <nav className="sidebar__nav" aria-label="Навигация">
          {navigationItems.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  isActive ? 'sidebar__nav-link sidebar__nav-link--active' : 'sidebar__nav-link'
                }
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </NavLink>
            )
          })}
        </nav>
      </aside>

      <div className="app-shell__content">
        <header className="topbar">
          <div>
            <p className="eyebrow">{pageMeta.eyebrow}</p>
            <h2 className="section-title">{pageMeta.title}</h2>
          </div>

          <div className="topbar__actions">
            <div className="balance-chip">
              <span>Доступно</span>
              <strong>{formatMoney(wallet?.balance)}</strong>
            </div>

            <button type="button" className="secondary-button" onClick={handleSignOut}>
              <LogOut size={16} />
              <span>Выйти</span>
            </button>
          </div>
        </header>

        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
