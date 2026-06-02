import { CheckCircle2, CreditCard, RefreshCw } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAppData } from '../context/AppDataContext'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { apiRequest } from '../lib/api'
import { formatDateTime, formatMoney, paymentStatusLabel } from '../lib/format'
import type { PaymentInfo } from '../types/models'

export function PaymentSuccessPage() {
  const [searchParams] = useSearchParams()
  const { session } = useAuth()
  const { refreshOperations, refreshWallet } = useAppData()
  const { showToast } = useToast()
  const [payment, setPayment] = useState<PaymentInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const paymentId = useMemo(() => Number(searchParams.get('paymentId')), [searchParams])

  async function loadPayment(showSuccessToast = false) {
    if (!session || !Number.isFinite(paymentId)) {
      setError('Не удалось определить платеж')
      setLoading(false)
      setRefreshing(false)
      return
    }

    try {
      const response = await apiRequest<PaymentInfo>(`/api/payments/${paymentId}`, {
        token: session.token,
      })
      setPayment(response)
      setError(null)

      if (response.status === 'SUCCEEDED') {
        await Promise.all([refreshWallet(), refreshOperations()])
        if (showSuccessToast) {
          showToast('Баланс пополнен', 'success')
        }
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Не удалось загрузить платеж')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadPayment(true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paymentId, session?.token])

  async function handleRefresh() {
    setRefreshing(true)
    await loadPayment(false)
  }

  return (
    <main className="page-grid" style={{ maxWidth: 720, margin: '48px auto', padding: '0 20px' }}>
      <section className="panel panel--elevated">
        <div className="panel__header">
          <div>
            <h2 className="panel__title">Результат оплаты</h2>
            <p className="panel__meta">Платеж подтверждается сервером перед зачислением на кошелек.</p>
          </div>
          <CheckCircle2 size={22} />
        </div>

        {loading ? (
          <p className="panel__meta">Загрузка платежа...</p>
        ) : error ? (
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
                    <th>Подтвержден</th>
                    <td>{formatDateTime(payment.confirmedAt)}</td>
                  </tr>
                  <tr>
                    <th>Зачислен</th>
                    <td>{formatDateTime(payment.creditedAt)}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <button type="button" className="secondary-button" onClick={handleRefresh} disabled={refreshing}>
                <RefreshCw size={16} className={refreshing ? 'spin' : ''} />
                <span>Обновить статус</span>
              </button>
              <Link className="primary-button" to="/app/wallet">
                <CreditCard size={16} />
                <span>К кошельку</span>
              </Link>
            </div>
          </div>
        ) : null}
      </section>
    </main>
  )
}
