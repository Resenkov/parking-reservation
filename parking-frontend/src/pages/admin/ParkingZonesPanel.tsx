import type { Dispatch, FormEvent, SetStateAction } from 'react'
import { EmptyState } from '../../components/EmptyState'
import type { ParkingLot, ParkingZone, ParkingZonePayload } from '../../types/models'

interface ParkingZonesPanelProps {
  lots: ParkingLot[]
  zones: ParkingZone[]
  zoneForm: ParkingZonePayload
  editingZoneId: number | null
  setZoneForm: Dispatch<SetStateAction<ParkingZonePayload>>
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onReset: () => void
  onEdit: (zone: ParkingZone) => void
  onDelete: (id: number) => void
}

export function ParkingZonesPanel({
  lots,
  zones,
  zoneForm,
  editingZoneId,
  setZoneForm,
  onSubmit,
  onReset,
  onEdit,
  onDelete,
}: ParkingZonesPanelProps) {
  return (
    <section className="panel panel--elevated">
      <div className="panel__header">
        <div>
          <h3 className="panel__title">Зоны</h3>
          <p className="panel__meta">{zones.length}</p>
        </div>
      </div>

      <form className="admin-form" onSubmit={onSubmit}>
        <div className="field-grid field-grid--two">
          <label className="field">
            <span>Площадка</span>
            <select
              value={zoneForm.lotId}
              onChange={(event) =>
                setZoneForm((current) => ({ ...current, lotId: Number(event.target.value) }))
              }
            >
              {lots.map((lot) => (
                <option key={lot.id} value={lot.id}>
                  {lot.code} · {lot.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Код зоны</span>
            <input
              value={zoneForm.code}
              onChange={(event) => setZoneForm((current) => ({ ...current, code: event.target.value }))}
            />
          </label>
          <label className="field">
            <span>Название</span>
            <input
              value={zoneForm.name}
              onChange={(event) => setZoneForm((current) => ({ ...current, name: event.target.value }))}
            />
          </label>
          <label className="field">
            <span>Уровень</span>
            <input
              value={zoneForm.level ?? ''}
              onChange={(event) => setZoneForm((current) => ({ ...current, level: event.target.value }))}
            />
          </label>
          <label className="field field--compact">
            <span>Статус</span>
            <select
              value={zoneForm.active ? 'true' : 'false'}
              onChange={(event) =>
                setZoneForm((current) => ({ ...current, active: event.target.value === 'true' }))
              }
            >
              <option value="true">Активна</option>
              <option value="false">Выключена</option>
            </select>
          </label>
        </div>

        <div className="admin-form__actions">
          <button type="submit" className="primary-button">
            {editingZoneId === null ? 'Добавить зону' : 'Сохранить зону'}
          </button>
          {editingZoneId !== null ? (
            <button type="button" className="secondary-button" onClick={onReset}>
              Сбросить
            </button>
          ) : null}
        </div>
      </form>

      {zones.length === 0 ? (
        <EmptyState title="Зоны не созданы" />
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Площадка</th>
                <th>Код</th>
                <th>Название</th>
                <th>Уровень</th>
                <th>Статус</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {zones.map((zone) => (
                <tr key={zone.id}>
                  <td>{zone.lotCode ?? '-'}</td>
                  <td>{zone.code}</td>
                  <td>{zone.name}</td>
                  <td>{zone.level ?? '-'}</td>
                  <td>{zone.active ? 'Активна' : 'Выключена'}</td>
                  <td className="table-actions">
                    <div className="inline-actions">
                      <button type="button" className="secondary-button" onClick={() => onEdit(zone)}>
                        Изменить
                      </button>
                      <button
                        type="button"
                        className="secondary-button secondary-button--danger"
                        onClick={() => onDelete(zone.id)}
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
