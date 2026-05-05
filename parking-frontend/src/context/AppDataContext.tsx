import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import { ApiError, apiRequest } from '../lib/api'
import { useAuth } from './AuthContext'
import { useToast } from './ToastContext'
import type {
  AuthResponse,
  Booking,
  LoginPayload,
  RegisterPayload,
  ReservationCatalog,
  Session,
  SpotAvailability,
  SpotSearchInput,
  UpdateProfilePayload,
  UserProfile,
  Wallet,
  WalletOperation,
} from '../types/models'

interface AppDataContextValue {
  profile: UserProfile | null
  wallet: Wallet | null
  bookings: Booking[]
  operations: WalletOperation[]
  reservationCatalog: ReservationCatalog | null
  isInitializing: boolean
  refreshAll: () => Promise<void>
  refreshBookings: () => Promise<void>
  refreshWallet: () => Promise<void>
  refreshOperations: () => Promise<void>
  refreshReservationCatalog: () => Promise<void>
  login: (payload: LoginPayload) => Promise<AuthResponse>
  register: (payload: RegisterPayload) => Promise<AuthResponse>
  searchAvailableSpots: (input: SpotSearchInput) => Promise<SpotAvailability[]>
  bookSpot: (payload: { spotCode: string; from: string; to: string }) => Promise<Booking>
  applyBookingAction: (
    id: number,
    action: 'confirm' | 'activate' | 'complete' | 'cancel',
  ) => Promise<void>
  topUp: (amount: number) => Promise<void>
  updateProfile: (payload: UpdateProfilePayload) => Promise<void>
}

const AppDataContext = createContext<AppDataContextValue | null>(null)

export function AppDataProvider({ children }: PropsWithChildren) {
  const { session } = useAuth()
  const { showToast } = useToast()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [wallet, setWallet] = useState<Wallet | null>(null)
  const [bookings, setBookings] = useState<Booking[]>([])
  const [operations, setOperations] = useState<WalletOperation[]>([])
  const [reservationCatalog, setReservationCatalog] = useState<ReservationCatalog | null>(null)
  const [isInitializing, setIsInitializing] = useState(false)

  const requireSession = useCallback((): Session => {
    if (!session) {
      throw new Error('Сессия отсутствует')
    }
    return session
  }, [session])

  const buildFallbackProfile = useCallback((activeSession: Session): UserProfile => {
    return {
      id: activeSession.userId,
      email: activeSession.email,
      firstName: '',
      lastName: '',
      roles: activeSession.roles,
    }
  }, [])

  const loadProfile = useCallback(
    async (activeSession: Session): Promise<UserProfile> => {
      try {
        const response = await apiRequest<{
          id?: number
          email?: string
          firstName?: string
          lastName?: string
          roles?: string[]
        }>('/api/user/me', {
          token: activeSession.token,
        })

        return {
          id: response.id ?? activeSession.userId,
          email: response.email ?? activeSession.email,
          firstName: response.firstName ?? '',
          lastName: response.lastName ?? '',
          roles: response.roles?.map((role) => role.replace(/^ROLE_/, '')) ?? activeSession.roles,
        }
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          throw error
        }
        return buildFallbackProfile(activeSession)
      }
    },
    [buildFallbackProfile],
  )

  const ensureWallet = useCallback(
    async (activeSession: Session, currentProfile: UserProfile) => {
      if (!currentProfile.email || currentProfile.id === null) {
        return
      }

      await apiRequest<Wallet>('/api/accounts/wallets', {
        method: 'POST',
        token: activeSession.token,
        body: {
          userId: currentProfile.id,
          email: currentProfile.email,
        },
      })
    },
    [],
  )

  const loadWallet = useCallback(async (activeSession: Session, email: string): Promise<Wallet> => {
    return apiRequest<Wallet>(`/api/accounts/${encodeURIComponent(email)}`, {
      token: activeSession.token,
    })
  }, [])

  const loadBookings = useCallback(async (activeSession: Session): Promise<Booking[]> => {
    const response = await apiRequest<Booking[]>('/api/reservation/my', {
      token: activeSession.token,
    })

    return response.sort(
      (left, right) => new Date(left.startTime).getTime() - new Date(right.startTime).getTime(),
    )
  }, [])

  const loadOperations = useCallback(
    async (activeSession: Session, email: string): Promise<WalletOperation[]> => {
      const response = await apiRequest<WalletOperation[]>(
        `/api/accounts/${encodeURIComponent(email)}/operations`,
        {
          token: activeSession.token,
        },
      )

      return response.sort(
        (left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime(),
      )
    },
    [],
  )

  const loadReservationCatalog = useCallback(
    async (activeSession: Session): Promise<ReservationCatalog> => {
      return apiRequest<ReservationCatalog>('/api/reservation/catalog', {
        token: activeSession.token,
      })
    },
    [],
  )

  const refreshAll = useCallback(async () => {
    const activeSession = requireSession()
    setIsInitializing(true)

    try {
      const currentProfile = await loadProfile(activeSession)
      setProfile(currentProfile)
      await ensureWallet(activeSession, currentProfile)

      const [nextWallet, nextBookings, nextOperations, nextCatalog] = await Promise.all([
        loadWallet(activeSession, currentProfile.email),
        loadBookings(activeSession),
        loadOperations(activeSession, currentProfile.email),
        loadReservationCatalog(activeSession),
      ])

      setWallet(nextWallet)
      setBookings(nextBookings)
      setOperations(nextOperations)
      setReservationCatalog(nextCatalog)
    } finally {
      setIsInitializing(false)
    }
  }, [
    ensureWallet,
    loadBookings,
    loadOperations,
    loadProfile,
    loadReservationCatalog,
    loadWallet,
    requireSession,
  ])

  const refreshWallet = useCallback(async () => {
    const activeSession = requireSession()
    const currentProfile = profile ?? buildFallbackProfile(activeSession)
    setWallet(await loadWallet(activeSession, currentProfile.email))
  }, [buildFallbackProfile, loadWallet, profile, requireSession])

  const refreshBookings = useCallback(async () => {
    const activeSession = requireSession()
    setBookings(await loadBookings(activeSession))
  }, [loadBookings, requireSession])

  const refreshOperations = useCallback(async () => {
    const activeSession = requireSession()
    const currentProfile = profile ?? buildFallbackProfile(activeSession)
    setOperations(await loadOperations(activeSession, currentProfile.email))
  }, [buildFallbackProfile, loadOperations, profile, requireSession])

  const refreshReservationCatalog = useCallback(async () => {
    const activeSession = requireSession()
    setReservationCatalog(await loadReservationCatalog(activeSession))
  }, [loadReservationCatalog, requireSession])

  const refreshFinancialsAndBookings = useCallback(async () => {
    await Promise.all([refreshWallet(), refreshBookings(), refreshOperations()])
  }, [refreshBookings, refreshOperations, refreshWallet])

  const login = useCallback(async (payload: LoginPayload) => {
    return apiRequest<AuthResponse>('/auth/login', {
      method: 'POST',
      body: payload,
    })
  }, [])

  const register = useCallback(async (payload: RegisterPayload) => {
    return apiRequest<AuthResponse>('/api/user/add', {
      method: 'POST',
      body: payload,
    })
  }, [])

  const searchAvailableSpots = useCallback(
    async (input: SpotSearchInput) => {
      const activeSession = requireSession()
      const search = new URLSearchParams({
        from: input.from,
        to: input.to,
      })

      if (input.zone) {
        search.set('zone', input.zone)
      }
      if (input.level) {
        search.set('level', input.level)
      }

      return apiRequest<SpotAvailability[]>(
        `/api/reservation/spots/available?${search.toString()}`,
        { token: activeSession.token },
      )
    },
    [requireSession],
  )

  const bookSpot = useCallback(
    async (payload: { spotCode: string; from: string; to: string }) => {
      const activeSession = requireSession()
      const booking = await apiRequest<Booking>('/api/reservation/book', {
        method: 'POST',
        token: activeSession.token,
        body: payload,
      })
      await refreshFinancialsAndBookings()
      return booking
    },
    [refreshFinancialsAndBookings, requireSession],
  )

  const applyBookingAction = useCallback(
    async (id: number, action: 'confirm' | 'activate' | 'complete' | 'cancel') => {
      const activeSession = requireSession()
      const pathByAction: Record<typeof action, string> = {
        confirm: `/api/reservation/${id}/confirm`,
        activate: `/api/reservation/${id}/activate`,
        complete: `/api/reservation/${id}/complete`,
        cancel: `/api/reservation/cancel/${id}`,
      }

      await apiRequest(pathByAction[action], {
        method: 'POST',
        token: activeSession.token,
      })

      await refreshFinancialsAndBookings()
    },
    [refreshFinancialsAndBookings, requireSession],
  )

  const topUp = useCallback(
    async (amount: number) => {
      const activeSession = requireSession()
      const email = (profile ?? buildFallbackProfile(activeSession)).email
      await apiRequest(`/api/accounts/${encodeURIComponent(email)}/top-up`, {
        method: 'POST',
        token: activeSession.token,
        body: {
          operationId: `topup-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          amount,
        },
      })
      await Promise.all([refreshWallet(), refreshOperations()])
    },
    [buildFallbackProfile, profile, refreshOperations, refreshWallet, requireSession],
  )

  const updateProfile = useCallback(
    async (payload: UpdateProfilePayload) => {
      const activeSession = requireSession()
      await apiRequest('/api/user/update', {
        method: 'PUT',
        token: activeSession.token,
        body: payload,
      })
      setProfile(await loadProfile(activeSession))
    },
    [loadProfile, requireSession],
  )

  useEffect(() => {
    if (!session) {
      // Synchronize local caches on logout.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setProfile(null)
      setWallet(null)
      setBookings([])
      setOperations([])
      setReservationCatalog(null)
      setIsInitializing(false)
      return
    }

    void refreshAll().catch((error: unknown) => {
      const message =
        error instanceof Error ? error.message : 'Не удалось загрузить данные пользователя'
      showToast(message, 'error')
    })
  }, [refreshAll, session, showToast])

  const value = useMemo<AppDataContextValue>(
    () => ({
      profile,
      wallet,
      bookings,
      operations,
      reservationCatalog,
      isInitializing,
      refreshAll,
      refreshBookings,
      refreshWallet,
      refreshOperations,
      refreshReservationCatalog,
      login,
      register,
      searchAvailableSpots,
      bookSpot,
      applyBookingAction,
      topUp,
      updateProfile,
    }),
    [
      applyBookingAction,
      bookSpot,
      bookings,
      isInitializing,
      login,
      operations,
      profile,
      refreshAll,
      refreshBookings,
      refreshOperations,
      refreshReservationCatalog,
      refreshWallet,
      register,
      reservationCatalog,
      searchAvailableSpots,
      topUp,
      updateProfile,
      wallet,
    ],
  )

  return <AppDataContext.Provider value={value}>{children}</AppDataContext.Provider>
}

export function useAppData(): AppDataContextValue {
  const context = useContext(AppDataContext)
  if (!context) {
    throw new Error('useAppData must be used within AppDataProvider')
  }
  return context
}
