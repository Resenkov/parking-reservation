import type { Dispatch, FormEvent, SetStateAction } from 'react'
import { EmptyState } from '../../components/EmptyState'
import type { ParkingLot, ParkingLotPayload } from '../../types/models'

interface ParkingLotsPanelProps {
  lots: ParkingLot[]
  lotForm: ParkingLotPayload
  editingLotId: number | null
  setLotForm: Dispatch<SetStateAction<ParkingLotPayload>>
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onReset: () => void
  onEdit: (lot: ParkingLot) => void
  onDelete: (id: number) => void
}

export function ParkingLotsPanel({
  lots,
  lotForm,
  editingLotId,
  setLotForm,
  onSubmit,
  onReset,
  onEdit,
  onDelete,
}: ParkingLotsPanelProps) {
  return (
    <section className="panel panel--elevated">
      <div className="panel__header">
        <div>
          <h3 className="panel__title">Площадки</h3>
          <p className="panel__meta">{lots.length}</p>
        </div>
      </div>

      <form className="admin-form" onSubmit={onSubmit}>
        <div className="field-grid field-grid--two">
          <label className="field">
            <span>Код</span>
            <input
              value={lotForm.code}
              onChange={(event) => setLotForm((current) => ({ ...current, code: event.target.value }))}
            />
          </label>
          <label className="field">
            <span>Название</span>
            <input
              value={lotForm.name}
              onChange={(event) => setLotForm((current) => ({ ...current, name: event.target.value }))}
            />
          </label>
          <label className="field field--full">
            <span>Адрес</span>
            <input
              value={lotForm.address ?? ''}
              onChange={(event) => setLotForm((current) => ({ ...current, address: event.target.value }))}
            />
          </label>
          <label className="field field--compact">
            <span>Статус</span>
            <select
              value={lotForm.active ? 'true' : 'false'}
              onChange={(event) =>
                setLotForm((current) => ({ ...current, active: event.target.value === 'true' }))
              }
            >
              <option value="true">Активна</option>
              <option value="false">Выключена</option>
            </select>
          </label>
        </div>

        <div className="admin-form__actions">
          <button type="submit" className="primary-button">
            {editingLotId === null ? 'Добавить площадку' : 'Сохранить площадку'}
          </button>
          {editingLotId !== null ? (
            <button type="button" className="secondary-button" onClick={onReset}>
              Сбросить
            </button>
          ) : null}
        </div>
      </form>

      {lots.length === 0 ? (
        <EmptyState title="Площадки не созданы" />
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Код</th>
                <th>Название</th>
                <th>Адрес</th>
                <th>Статус</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {lots.map((lot) => (
                <tr key={lot.id}>
                  <td>{lot.code}</td>
                  <td>{lot.name}</td>
                  <td>{lot.address ?? '-'}</td>
                  <td>{lot.active ? 'Активна' : 'Выключена'}</td>
                  <td className="table-actions">
                    <div className="inline-actions">
                      <button type="button" className="secondary-button" onClick={() => onEdit(lot)}>
                        Изменить
                      </button>
                      <button
                        type="button"
                        className="secondary-button secondary-button--danger"
                        onClick={() => onDelete(lot.id)}
                      >
                        Удалить
                      </button>
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
