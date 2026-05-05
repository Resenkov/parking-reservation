import { RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { EmptyState } from '../components/EmptyState'
import { StatTile } from '../components/StatTile'
import { useAppData } from '../context/AppDataContext'
import { useToast } from '../context/ToastContext'
import { accountStatusLabel, formatDateTime, formatMoney, operationTypeLabel } from '../lib/format'

export function WalletPage() {
  const { wallet, operations, topUp, refreshWallet, refreshOperations } = useAppData()
  const { showToast } = useToast()
  const [amount, setAmount] = useState('')
  const [pending, setPending] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const parsedAmount = Number.parseFloat(amount)

    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      showToast('Укажите корректную сумму', 'error')
      return
    }

    try {
      setPending(true)
      await topUp(parsedAmount)
      setAmount('')
      showToast('Счёт пополнен', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось пополнить счёт', 'error')
    } finally {
      setPending(false)
    }
  }

  async function handleRefresh() {
    try {
      setRefreshing(true)
      await Promise.all([refreshWallet(), refreshOperations()])
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось обновить счёт', 'error')
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <div className="page-grid">
      <section className="stats-grid">
        <StatTile label="Доступно" value={formatMoney(wallet?.balance)} />
        <StatTile label="Удержано" value={formatMoney(wallet?.heldAmount)} />
        <StatTile label="Статус счёта" value={accountStatusLabel(wallet?.status)} />
      </section>

      <section className="panel panel--elevated">
        <div className="panel__header">
          <h3 className="panel__title">Пополнение счёта</h3>
        </div>

        <form className="inline-form" onSubmit={handleSubmit}>
          <label className="field field--compact">
            <span>Сумма</span>
            <input
              type="number"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              min="0.01"
              step="0.01"
              placeholder="1000.00"
              required
            />
          </label>

          <button type="submit" className="primary-button" disabled={pending}>
            {pending ? 'Пополнение...' : 'Пополнить'}
          </button>
        </form>
      </section>

      <section className="panel panel--elevated">
        <div className="panel__header">
          <div>
            <h3 className="panel__title">Операции</h3>
            <p className="panel__meta">{operations.length}</p>
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

        {operations.length === 0 ? (
          <EmptyState title="Операций пока нет" />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Дата</th>
                  <th>Тип</th>
                  <th>Сумма</th>
                  <th>Детали</th>
                </tr>
              </thead>
              <tbody>
                {operations.map((operation) => (
                  <tr key={operation.id}>
                    <td>{formatDateTime(operation.createdAt)}</td>
                    <td>{operationTypeLabel(operation.type)}</td>
                    <td>{formatMoney(operation.amount)}</td>
                    <td>{operation.details || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
