import type { Dispatch, FormEvent, SetStateAction } from 'react'
import { EmptyState } from '../../components/EmptyState'
import { ParkingScheme } from '../../components/ParkingScheme'
import type {
  ParkingLayoutSpot,
  ParkingSpot,
  ParkingSpotPayload,
  ParkingZone,
} from '../../types/models'

interface ParkingSpotsPanelProps {
  zones: ParkingZone[]
  spots: ParkingSpot[]
  adminSchemeSpots: ParkingLayoutSpot[]
  selectedSpot: ParkingSpot | null
  selectedSpotCode: string | null
  spotForm: ParkingSpotPayload
  editingSpotId: number | null
  setSpotForm: Dispatch<SetStateAction<ParkingSpotPayload>>
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onReset: () => void
  onEditSpot: (spot: ParkingSpot) => void
  onDelete: (id: number) => void
}

export function ParkingSpotsPanel({
  zones,
  spots,
  adminSchemeSpots,
  selectedSpot,
  selectedSpotCode,
  spotForm,
  editingSpotId,
  setSpotForm,
  onSubmit,
  onReset,
  onEditSpot,
  onDelete,
}: ParkingSpotsPanelProps) {
  return (
    <section className="panel panel--elevated">
      <div className="panel__header">
        <div>
          <h3 className="panel__title">Парковочные места</h3>
          <p className="panel__meta">Редактирование через схему парковки</p>
        </div>

        <button type="button" className="secondary-button" onClick={onReset}>
          Новое место
        </button>
      </div>

      {spots.length === 0 ? (
        <EmptyState title="Места не созданы" />
      ) : (
        <div className="admin-spot-workspace">
          <div className="admin-spot-workspace__scheme">
            <ParkingScheme
              spots={adminSchemeSpots}
              mode="admin"
              selectedSpotCode={selectedSpotCode}
              onSelectSpotCode={(spotCode) => {
                const nextSpot = spots.find((spot) => spot.code === spotCode)
                if (nextSpot) {
                  onEditSpot(nextSpot)
                }
              }}
            />
          </div>

          <div className="admin-spot-workspace__editor">
            <form className="admin-form" onSubmit={onSubmit}>
              {selectedSpot ? (
                <div className="selected-spot-card selected-spot-card--admin">
                  <div className="selected-spot-card__code">{selectedSpot.code}</div>
                  <div className="selected-spot-card__meta">
                    <span>{selectedSpot.zoneCode ?? 'Без зоны'}</span>
                    <span>{selectedSpot.level ?? 'Без уровня'}</span>
                    <span>{selectedSpot.occupied ? 'Занято' : 'Свободно'}</span>
                  </div>
                </div>
              ) : (
                <div className="admin-spot-placeholder">
                  Выберите место на схеме или создайте новое через форму.
                </div>
              )}

              <div className="field-grid field-grid--two">
                <label className="field">
                  <span>Зона</span>
                  <select
                    value={spotForm.zoneId}
                    onChange={(event) =>
                      setSpotForm((current) => ({ ...current, zoneId: Number(event.target.value) }))
                    }
                  >
                    {zones.map((zone) => (
                      <option key={zone.id} value={zone.id}>
                        {zone.code} · {zone.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span>Код места</span>
                  <input
                    value={spotForm.code}
                    onChange={(event) =>
                      setSpotForm((current) => ({ ...current, code: event.target.value }))
                    }
                  />
                </label>
                <label className="field field--compact">
                  <span>Цена за шаг</span>
                  <input
                    type="number"
                    min={0}
                    step="0.01"
                    value={spotForm.price}
                    onChange={(event) =>
                      setSpotForm((current) => ({ ...current, price: Number(event.target.value) }))
                    }
                  />
                </label>
              </div>

              <div className="admin-form__actions">
                <button type="submit" className="primary-button">
                  {editingSpotId === null ? 'Добавить место' : 'Сохранить место'}
                </button>
                {editingSpotId !== null ? (
                  <>
                    <button type="button" className="secondary-button" onClick={onReset}>
                      Сбросить
                    </button>
                    <button
                      type="button"
                      className="secondary-button secondary-button--danger"
                      onClick={() => onDelete(editingSpotId)}
                    >
                      Удалить
                    </button>
                  </>
                ) : null}
              </div>
            </form>
          </div>
        </div>
      )}
    </section>
  )
}
