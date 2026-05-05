export interface Session {
  token: string
  email: string
  userId: number | null
  roles: string[]
}

export interface UserProfile {
  id: number | null
  email: string
  firstName: string
  lastName: string
  roles: string[]
}

export interface Wallet {
  id: number
  userEmail: string
  userId: number | null
  balance: number
  heldAmount: number
  status: string
}

export interface Booking {
  id: number
  spotId: number
  spotCode: string
  userId: number
  userEmail: string
  startTime: string
  endTime: string
  holdExpiresAt: string | null
  arrivalDeadline: string | null
  totalAmount: number
  refundAmount: number
  refundPercent: number
  status: string
}

export interface WalletOperation {
  id: number
  operationId: string
  userEmail: string
  userId: number | null
  accountId: number
  reservationId: number | null
  type: string
  amount: number
  details: string
  createdAt: string
}

export interface SpotAvailability {
  id: number
  code: string
  zone: string | null
  level: string | null
  price: number
  occupied: boolean
}

export interface ReservationLevelOption {
  code: string
  spotCount: number
}

export interface ReservationZoneOption {
  code: string
  name: string | null
  level: string | null
  spotCount: number
}

export interface ReservationCatalog {
  levels: ReservationLevelOption[]
  zones: ReservationZoneOption[]
}

export interface SpotSearchInput {
  from: string
  to: string
  zone?: string
  level?: string
}

export interface LoginPayload {
  email: string
  password: string
}

export interface RegisterPayload {
  firstName: string
  lastName: string
  email: string
  password: string
}

export interface UpdateProfilePayload {
  firstName?: string
  lastName?: string
  password?: string
}

export interface AuthResponse {
  token: string
}
