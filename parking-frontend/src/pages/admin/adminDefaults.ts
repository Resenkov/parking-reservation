import type {
  ParkingLotPayload,
  ParkingSpotPayload,
  ParkingZonePayload,
  ReservationPolicySettings,
} from '../../types/models'

export const defaultPolicy: ReservationPolicySettings = {
  bookingStepMinutes: 15,
  holdDurationMinutes: 5,
  arrivalDeadlineMinutesBeforeEnd: 15,
  standardCancellationRefundPercent: 60,
  noShowRefundPercent: 0,
  maxBookingDurationMinutes: 720,
  maxBookingAheadDays: 7,
}

export const defaultLotForm: ParkingLotPayload = {
  code: '',
  name: '',
  address: '',
  active: true,
}

export const defaultZoneForm: ParkingZonePayload = {
  lotId: 0,
  code: '',
  name: '',
  level: '',
  active: true,
}

export const defaultSpotForm: ParkingSpotPayload = {
  zoneId: 0,
  code: '',
  price: 0,
}
