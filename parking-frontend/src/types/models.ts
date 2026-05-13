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

export interface PaymentInitResponse {
  paymentId: number
  amount: number
  status: string
  provider: string
  checkoutUrl: string
}

export interface PaymentInfo {
  paymentId: number
  amount: number
  status: string
  provider: string
  checkoutUrl: string | null
  createdAt: string
  updatedAt: string
  confirmedAt: string | null
  creditedAt: string | null
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

export interface ParkingLayoutSpot {
  id: number
  code: string
  zone: string | null
  zoneName: string | null
  level: string | null
  price: number
  occupied: boolean
  available: boolean
}

export interface ParkingLayout {
  generatedAt: string
  from: string | null
  to: string | null
  spots: ParkingLayoutSpot[]
}

export interface ReservationPolicySettings {
  bookingStepMinutes: number
  holdDurationMinutes: number
  arrivalDeadlineMinutesBeforeEnd: number
  standardCancellationRefundPercent: number
  noShowRefundPercent: number
  maxBookingDurationMinutes: number
  maxBookingAheadDays: number
}

export interface ParkingLot {
  id: number
  code: string
  name: string
  address: string | null
  active: boolean
}

export interface ParkingLotPayload {
  code: string
  name: string
  address?: string
  active: boolean
}

export interface ParkingZone {
  id: number
  lotId: number
  lotCode: string | null
  code: string
  name: string
  level: string | null
  active: boolean
}

export interface ParkingZonePayload {
  lotId: number
  code: string
  name: string
  level?: string
  active: boolean
}

export interface ParkingSpot {
  id: number
  zoneId: number
  zoneCode: string | null
  zoneName: string | null
  lotId: number | null
  lotCode: string | null
  code: string
  occupied: boolean
  price: number
  level: string | null
}

export interface ParkingSpotPayload {
  zoneId: number
  code: string
  price: number
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
