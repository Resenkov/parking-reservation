const statusLabels: Record<string, string> = {
  PENDING_HOLD: 'Подготовка HOLD',
  HOLD: 'Ожидает оплаты',
  HOLD_FAILED: 'Ошибка удержания',
  CONFIRMED: 'Подтверждена',
  ACTIVE: 'Активна',
  COMPLETED: 'Завершена',
  CANCELLED: 'Отменена',
  EXPIRED: 'Истекла',
  NO_SHOW: 'Неявка',
}

const operationTypeLabels: Record<string, string> = {
  TOP_UP: 'Пополнение',
  HOLD: 'Удержание',
  CAPTURE: 'Списание',
  REFUND: 'Возврат',
  PENALTY: 'Штраф',
}

const paymentStatusLabels: Record<string, string> = {
  NEW: 'Создан',
  PENDING: 'Ожидает оплаты',
  SUCCEEDED: 'Оплачен',
  FAILED: 'Ошибка оплаты',
  CANCELLED: 'Оплата отменена',
}

const accountStatusLabels: Record<string, string> = {
  OPEN: 'Активен',
  BLOCKED: 'Заблокирован',
  CLOSED: 'Закрыт',
}

const roleLabels: Record<string, string> = {
  USER: 'Пользователь',
  ADMIN: 'Администратор',
}

export function formatMoney(value: number | null | undefined): string {
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0))
}

export function formatDateTime(value: string | Date | null | undefined): string {
  if (!value) {
    return '-'
  }

  const date = value instanceof Date ? value : new Date(value)
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function formatDuration(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  if (!hours) {
    return `${totalMinutes} мин`
  }

  if (!minutes) {
    return `${hours} ч`
  }

  return `${hours} ч ${minutes} мин`
}

export function nextQuarterHour(date = new Date()): Date {
  return nextTimeStep(date, 15)
}

export function nextTimeStep(date = new Date(), stepMinutes: number): Date {
  const next = new Date(date)
  next.setSeconds(0, 0)
  const totalMinutes =
    Math.floor(next.getTime() / 60000) +
    ((stepMinutes - (Math.floor(next.getTime() / 60000) % stepMinutes)) % stepMinutes)
  return new Date(totalMinutes * 60000)
}

export function toDateTimeLocal(date: Date): string {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hours = `${date.getHours()}`.padStart(2, '0')
  const minutes = `${date.getMinutes()}`.padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

export function addMinutes(dateTimeLocal: string, minutes: number): string {
  const date = new Date(dateTimeLocal)
  date.setMinutes(date.getMinutes() + minutes)
  return toDateTimeLocal(date)
}

export function calculateSessionAmount(
  pricePerStep: number,
  durationMinutes: number,
  bookingStepMinutes = 15,
): number {
  return (pricePerStep * durationMinutes) / bookingStepMinutes
}

export function statusLabel(status: string): string {
  return statusLabels[status] ?? status.replaceAll('_', ' ')
}

export function operationTypeLabel(type: string): string {
  return operationTypeLabels[type] ?? type
}

export function paymentStatusLabel(status: string): string {
  return paymentStatusLabels[status] ?? status
}

export function accountStatusLabel(status: string | null | undefined): string {
  if (!status) {
    return '-'
  }
  return accountStatusLabels[status] ?? status
}

export function roleLabel(role: string): string {
  return roleLabels[role] ?? role
}

export function statusTone(status: string): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'PENDING_HOLD':
    case 'HOLD':
      return 'warning'
    case 'CONFIRMED':
    case 'ACTIVE':
    case 'COMPLETED':
      return 'success'
    case 'CANCELLED':
    case 'EXPIRED':
    case 'NO_SHOW':
    case 'HOLD_FAILED':
      return 'danger'
    default:
      return 'neutral'
  }
}

export function levelLabel(level: string | null | undefined): string {
  if (!level) {
    return 'Без уровня'
  }
  return `Уровень ${level}`
}

export function floorLabel(level: string | null | undefined): string {
  if (!level) {
    return 'Этаж не указан'
  }

  const match = level.match(/\d+/)
  if (!match) {
    return level
  }

  return `${Number(match[0])} этаж`
}
