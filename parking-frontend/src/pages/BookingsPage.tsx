import { RefreshCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { EmptyState } from '../components/EmptyState'
import { StatusBadge } from '../components/StatusBadge'
import { useAppData } from '../context/AppDataContext'
import { useToast } from '../context/ToastContext'
import { formatDateTime, formatMoney } from '../lib/format'
import type { Booking } from '../types/models'

const actionMap: Record<
  string,
  Array<{ action: 'confirm' | 'activate' | 'complete' | 'cancel'; label: string; tone?: 'danger' }>
> = {
  HOLD: [
    { action: 'confirm', label: 'Подтвердить' },
    { action: 'cancel', label: 'Отменить', tone: 'danger' },
  ],
  CONFIRMED: [
    { action: 'activate', label: 'Заехал' },
    { action: 'cancel', label: 'Отменить', tone: 'danger' },
  ],
  ACTIVE: [{ action: 'complete', label: 'Завершить' }],
}

export function BookingsPage() {
  const { bookings, applyBookingAction, refreshAll } = useAppData()
  const { showToast } = useToast()
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [currentTime, setCurrentTime] = useState(() => Date.now())

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setCurrentTime(Date.now())
    }, 15_000)

    return () => window.clearInterval(intervalId)
  }, [])

  async function handleRefresh() {
    try {
      setRefreshing(true)
      await refreshAll()
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось обновить список', 'error')
    } finally {
      setRefreshing(false)
    }
  }

  async function handleAction(
    booking: Booking,
    action: 'confirm' | 'activate' | 'complete' | 'cancel',
  ) {
    try {
      setBusyKey(`${booking.id}:${action}`)
      await applyBookingAction(booking.id, action)
      showToast('Статус брони обновлён', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось выполнить действие', 'error')
    } finally {
      setBusyKey(null)
    }
  }

  return (
    <section className="panel panel--elevated">
      <div className="panel__header">
        <div>
          <h3 className="panel__title">История бронирований</h3>
          <p className="panel__meta">{bookings.length}</p>
        </div>

        <button
          type="button"
          className="secondary-button"
          onClick={handleRefresh}
          disabled={refreshing}
        >
          <RefreshCw size={16} className={refreshing ? 'spin' : ''} />
          <span>Обновить</span>
        </button>
      </div>

      {bookings.length === 0 ? (
        <EmptyState title="Бронирований пока нет" />
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Место</th>
                <th>Интервал</th>
                <th>Статус</th>
                <th>Сумма</th>
                <th>Возврат</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {bookings.map((booking) => (
                <tr key={booking.id}>
                  <td>
                    <div className="table-primary">
                      <strong>{booking.spotCode}</strong>
                      <span>#{booking.id}</span>
                    </div>
                  </td>
                  <td>
                    <div className="table-primary">
                      <strong>{formatDateTime(booking.startTime)}</strong>
                      <span>{formatDateTime(booking.endTime)}</span>
                    </div>
                  </td>
                  <td>
                    <StatusBadge status={booking.status} />
                  </td>
                  <td>{formatMoney(booking.totalAmount)}</td>
                  <td>{formatMoney(booking.refundAmount)}</td>
                  <td className="table-actions">
                    <div className="inline-actions">
                      {(actionMap[booking.status] ?? []).map((actionItem) => {
                        const key = `${booking.id}:${actionItem.action}`
                        const activateTooEarly =
                          actionItem.action === 'activate' &&
                          new Date(booking.startTime).getTime() > currentTime

                        return (
                          <button
                            key={actionItem.action}
                            type="button"
                            className={
                              actionItem.tone === 'danger'
                                ? 'secondary-button secondary-button--danger'
                                : 'secondary-button'
                            }
                            onClick={() => handleAction(booking, actionItem.action)}
                            disabled={busyKey === key || activateTooEarly}
                            title={
                              activateTooEarly
                                ? `Заезд станет доступен после ${formatDateTime(booking.startTime)}`
                                : undefined
                            }
                          >
                            {busyKey === key ? '...' : actionItem.label}
                          </button>
                        )
                      })}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
