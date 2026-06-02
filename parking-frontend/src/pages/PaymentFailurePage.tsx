import { AlertCircle, CreditCard } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { apiRequest } from '../lib/api'
import { formatDateTime, formatMoney, paymentStatusLabel } from '../lib/format'
import type { PaymentInfo } from '../types/models'

export function PaymentFailurePage() {
  const [searchParams] = useSearchParams()
  const { session } = useAuth()
  const [payment, setPayment] = useState<PaymentInfo | null>(null)
  const [error, setError] = useState<string | null>(null)

  const paymentId = useMemo(() => Number(searchParams.get('paymentId')), [searchParams])

  useEffect(() => {
    if (!session || !Number.isFinite(paymentId)) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setError('Не удалось определить платеж')
      return
    }

    void apiRequest<PaymentInfo>(`/api/payments/${paymentId}`, {
      token: session.token,
    })
      .then((response) => {
        setPayment(response)
        setError(null)
      })
      .catch((requestError: unknown) => {
        setError(requestError instanceof Error ? requestError.message : 'Не удалось загрузить платеж')
      })
  }, [paymentId, session])

  return (
    <main className="page-grid" style={{ maxWidth: 720, margin: '48px auto', padding: '0 20px' }}>
      <section className="panel panel--elevated">
        <div className="panel__header">
          <div>
            <h2 className="panel__title">Оплата не завершена</h2>
            <p className="panel__meta">Баланс не меняется, пока платеж не подтвержден.</p>
          </div>
          <AlertCircle size={22} />
        </div>

        {error ? (
          <p className="panel__meta">{error}</p>
        ) : payment ? (
          <div className="page-grid">
            <section className="stats-grid">
              <div className="stat-tile">
                <span className="stat-tile__label">Статус</span>
                <strong className="stat-tile__value">{paymentStatusLabel(payment.status)}</strong>
              </div>
              <div className="stat-tile">
                <span className="stat-tile__label">Сумма</span>
                <strong className="stat-tile__value">{formatMoney(payment.amount)}</strong>
              </div>
            </section>

            <div className="table-wrap">
              <table className="data-table">
                <tbody>
                  <tr>
                    <th>Платеж</th>
                    <td>#{payment.paymentId}</td>
                  </tr>
                  <tr>
                    <th>Провайдер</th>
                    <td>{payment.provider}</td>
                  </tr>
                  <tr>
                    <th>Создан</th>
                    <td>{formatDateTime(payment.createdAt)}</td>
                  </tr>
                  <tr>
                    <th>Последнее обновление</th>
                    <td>{formatDateTime(payment.updatedAt)}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          <p className="panel__meta">Загрузка платежа...</p>
        )}

        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 20 }}>
          <Link className="primary-button" to="/app/wallet">
            <CreditCard size={16} />
            <span>Вернуться в кошелек</span>
          </Link>
          <Link className="secondary-button" to="/app/book">
            <span>К бронированию</span>
          </Link>
        </div>
      </section>
    </main>
  )
}
