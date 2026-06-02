import { ApiError, apiRequest } from '../lib/api'
import type {
  AuthResponse,
  Booking,
  LoginPayload,
  PaymentInitResponse,
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

export type BookingAction = 'confirm' | 'activate' | 'complete' | 'cancel'

export function buildFallbackProfile(activeSession: Session): UserProfile {
  return {
    id: activeSession.userId,
    email: activeSession.email,
    firstName: '',
    lastName: '',
    roles: activeSession.roles,
  }
}

export async function loadProfile(activeSession: Session): Promise<UserProfile> {
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
}

export async function ensureWallet(activeSession: Session, currentProfile: UserProfile) {
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
}

export async function loadWallet(activeSession: Session, email: string): Promise<Wallet> {
  return apiRequest<Wallet>(`/api/accounts/${encodeURIComponent(email)}`, {
    token: activeSession.token,
  })
}

export async function loadBookings(activeSession: Session): Promise<Booking[]> {
  const response = await apiRequest<Booking[]>('/api/reservation/my', {
    token: activeSession.token,
  })

  return response.sort(
    (left, right) => new Date(left.startTime).getTime() - new Date(right.startTime).getTime(),
  )
}

export async function loadOperations(activeSession: Session, email: string): Promise<WalletOperation[]> {
  const response = await apiRequest<WalletOperation[]>(
    `/api/accounts/${encodeURIComponent(email)}/operations`,
    {
      token: activeSession.token,
    },
  )

  return response.sort(
    (left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime(),
  )
}

export async function loadReservationCatalog(activeSession: Session): Promise<ReservationCatalog> {
  return apiRequest<ReservationCatalog>('/api/reservation/catalog', {
    token: activeSession.token,
  })
}

export async function loginRequest(payload: LoginPayload): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/login', {
    method: 'POST',
    body: payload,
  })
}

export async function registerRequest(payload: RegisterPayload): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/api/user/add', {
    method: 'POST',
    body: payload,
  })
}

export async function searchAvailableSpotsRequest(
  activeSession: Session,
  input: SpotSearchInput,
): Promise<SpotAvailability[]> {
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
}

export async function bookSpotRequest(
  activeSession: Session,
  payload: { spotCode: string; from: string; to: string },
): Promise<Booking> {
  return apiRequest<Booking>('/api/reservation/book', {
    method: 'POST',
    token: activeSession.token,
    body: payload,
  })
}

export async function applyBookingActionRequest(
  activeSession: Session,
  id: number,
  action: BookingAction,
): Promise<void> {
  const pathByAction: Record<BookingAction, string> = {
    confirm: `/api/reservation/${id}/confirm`,
    activate: `/api/reservation/${id}/activate`,
    complete: `/api/reservation/${id}/complete`,
    cancel: `/api/reservation/cancel/${id}`,
  }

  await apiRequest(pathByAction[action], {
    method: 'POST',
    token: activeSession.token,
  })
}

export async function initTopUpPayment(activeSession: Session, amount: number): Promise<PaymentInitResponse> {
  return apiRequest<PaymentInitResponse>('/api/payments/top-up/init', {
    method: 'POST',
    token: activeSession.token,
    body: {
      amount,
    },
  })
}

export async function updateProfileRequest(activeSession: Session, payload: UpdateProfilePayload): Promise<void> {
  await apiRequest('/api/user/update', {
    method: 'PUT',
    token: activeSession.token,
    body: payload,
  })
}
