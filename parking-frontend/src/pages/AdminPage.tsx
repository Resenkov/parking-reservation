import type { FormEvent } from 'react'
import { useEffect, useMemo, useState } from 'react'
import { ParkingScheme } from '../components/ParkingScheme'
import { EmptyState } from '../components/EmptyState'
import { StatTile } from '../components/StatTile'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { apiRequest } from '../lib/api'
import { formatMoney } from '../lib/format'
import type {
  ParkingLayoutSpot,
  ParkingLot,
  ParkingLotPayload,
  ParkingSpot,
  ParkingSpotPayload,
  ParkingZone,
  ParkingZonePayload,
  ReservationPolicySettings,
} from '../types/models'

const defaultPolicy: ReservationPolicySettings = {
  bookingStepMinutes: 15,
  holdDurationMinutes: 5,
  arrivalDeadlineMinutesBeforeEnd: 15,
  standardCancellationRefundPercent: 60,
  noShowRefundPercent: 0,
  maxBookingDurationMinutes: 720,
  maxBookingAheadDays: 7,
}

const defaultLotForm: ParkingLotPayload = {
  code: '',
  name: '',
  address: '',
  active: true,
}

const defaultZoneForm: ParkingZonePayload = {
  lotId: 0,
  code: '',
  name: '',
  level: '',
  active: true,
}

const defaultSpotForm: ParkingSpotPayload = {
  zoneId: 0,
  code: '',
  price: 0,
}

export function AdminPage() {
  const { session } = useAuth()
  const { showToast } = useToast()
  const [loading, setLoading] = useState(true)
  const [savingSettings, setSavingSettings] = useState(false)
  const [policy, setPolicy] = useState<ReservationPolicySettings>(defaultPolicy)
  const [lots, setLots] = useState<ParkingLot[]>([])
  const [zones, setZones] = useState<ParkingZone[]>([])
  const [spots, setSpots] = useState<ParkingSpot[]>([])
  const [lotForm, setLotForm] = useState<ParkingLotPayload>(defaultLotForm)
  const [zoneForm, setZoneForm] = useState<ParkingZonePayload>(defaultZoneForm)
  const [spotForm, setSpotForm] = useState<ParkingSpotPayload>(defaultSpotForm)
  const [editingLotId, setEditingLotId] = useState<number | null>(null)
  const [editingZoneId, setEditingZoneId] = useState<number | null>(null)
  const [editingSpotId, setEditingSpotId] = useState<number | null>(null)
  const [selectedSpotCode, setSelectedSpotCode] = useState<string | null>(null)

  useEffect(() => {
    void loadAdminData()
  }, [])

  const adminSchemeSpots = useMemo<ParkingLayoutSpot[]>(() => {
    return spots.map((spot) => ({
      id: spot.id,
      code: spot.code,
      zone: spot.zoneCode,
      zoneName: spot.zoneName,
      level: spot.level,
      price: spot.price,
      occupied: spot.occupied,
      available: !spot.occupied,
    }))
  }, [spots])

  const selectedSpot = useMemo(() => {
    if (selectedSpotCode) {
      return spots.find((spot) => spot.code === selectedSpotCode) ?? null
    }
    if (editingSpotId !== null) {
      return spots.find((spot) => spot.id === editingSpotId) ?? null
    }
    return null
  }, [editingSpotId, selectedSpotCode, spots])

  async function adminRequest<T>(path: string, init?: { method?: string; body?: unknown }) {
    if (!session?.token) {
      throw new Error('Сессия отсутствует')
    }
    return apiRequest<T>(path, {
      method: init?.method,
      body: init?.body,
      token: session.token,
    })
  }

  async function loadAdminData() {
    try {
      setLoading(true)
      const [nextPolicy, nextLots, nextZones, nextSpots] = await Promise.all([
        adminRequest<ReservationPolicySettings>('/api/reservation/admin/settings'),
        adminRequest<ParkingLot[]>('/api/reservation/admin/lots'),
        adminRequest<ParkingZone[]>('/api/reservation/admin/zones'),
        adminRequest<ParkingSpot[]>('/api/reservation/admin/spots'),
      ])

      setPolicy(nextPolicy)
      setLots(nextLots)
      setZones(nextZones)
      setSpots(nextSpots)

      setSpotForm((current) => ({
        ...current,
        zoneId: current.zoneId === 0 ? (nextZones[0]?.id ?? 0) : current.zoneId,
      }))
      setZoneForm((current) => ({
        ...current,
        lotId: current.lotId === 0 ? (nextLots[0]?.id ?? 0) : current.lotId,
      }))
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось загрузить админские данные', 'error')
    } finally {
      setLoading(false)
    }
  }

  function resetLotForm() {
    setLotForm(defaultLotForm)
    setEditingLotId(null)
  }

  function resetZoneForm() {
    setZoneForm({
      ...defaultZoneForm,
      lotId: lots[0]?.id ?? 0,
    })
    setEditingZoneId(null)
  }

  function resetSpotForm() {
    setSpotForm({
      ...defaultSpotForm,
      zoneId: zones[0]?.id ?? 0,
    })
    setEditingSpotId(null)
    setSelectedSpotCode(null)
  }

  function editSpot(spot: ParkingSpot) {
    setEditingSpotId(spot.id)
    setSelectedSpotCode(spot.code)
    setSpotForm({
      zoneId: spot.zoneId,
      code: spot.code,
      price: spot.price,
    })
  }

  async function handleSettingsSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      setSavingSettings(true)
      const nextPolicy = await adminRequest<ReservationPolicySettings>('/api/reservation/admin/settings', {
        method: 'PUT',
        body: policy,
      })
      setPolicy(nextPolicy)
      showToast('Настройки сохранены', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось сохранить настройки', 'error')
    } finally {
      setSavingSettings(false)
    }
  }

  async function handleLotSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await adminRequest(
        editingLotId === null ? '/api/reservation/admin/lots' : `/api/reservation/admin/lots/${editingLotId}`,
        {
          method: editingLotId === null ? 'POST' : 'PUT',
          body: lotForm,
        },
      )
      await loadAdminData()
      resetLotForm()
      showToast('Площадка сохранена', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось сохранить площадку', 'error')
    }
  }

  async function handleZoneSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await adminRequest(
        editingZoneId === null ? '/api/reservation/admin/zones' : `/api/reservation/admin/zones/${editingZoneId}`,
        {
          method: editingZoneId === null ? 'POST' : 'PUT',
          body: zoneForm,
        },
      )
      await loadAdminData()
      resetZoneForm()
      showToast('Зона сохранена', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось сохранить зону', 'error')
    }
  }

  async function handleSpotSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await adminRequest(
        editingSpotId === null ? '/api/reservation/admin/spots' : `/api/reservation/admin/spots/${editingSpotId}`,
        {
          method: editingSpotId === null ? 'POST' : 'PUT',
          body: spotForm,
        },
      )
      await loadAdminData()
      resetSpotForm()
      showToast('Место сохранено', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось сохранить место', 'error')
    }
  }

  async function handleDelete(kind: 'lot' | 'zone' | 'spot', id: number) {
    const label = kind === 'lot' ? 'площадку' : kind === 'zone' ? 'зону' : 'место'
    if (!window.confirm(`Удалить ${label}?`)) {
      return
    }
    try {
      await adminRequest(`/api/reservation/admin/${kind}s/${id}`, { method: 'DELETE' })
      await loadAdminData()
      if (kind === 'lot') {
        resetLotForm()
      } else if (kind === 'zone') {
        resetZoneForm()
      } else {
        resetSpotForm()
      }
      showToast('Удаление выполнено', 'success')
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Не удалось удалить запись', 'error')
    }
  }

  if (loading) {
    return <div className="admin-loading">Загрузка админских данных...</div>
  }

  return (
    <div className="page-grid admin-page">
      <section className="stats-grid">
        <StatTile label="Площадок" value={lots.length} />
        <StatTile label="Зон" value={zones.length} />
        <StatTile label="Мест" value={spots.length} />
        <StatTile label="Базовая цена шага" value={formatMoney(spots[0]?.price ?? 0)} />
      </section>

      <section className="panel panel--elevated">
        <div className="panel__header">
          <div>
            <h3 className="panel__title">Политика бронирования</h3>
            <p className="panel__meta">Все значения доступны для изменения администратором</p>
          </div>
        </div>

        <form className="admin-form" onSubmit={handleSettingsSubmit}>
          <div className="field-grid field-grid--four">
            <label className="field">
              <span>Шаг брони, мин</span>
              <input
                type="number"
                min={1}
                value={policy.bookingStepMinutes}
                onChange={(event) =>
                  setPolicy((current) => ({
                    ...current,
                    bookingStepMinutes: Number(event.target.value),
                  }))
                }
              />
            </label>
            <label className="field">
              <span>HOLD, мин</span>
              <input
                type="number"
                min={1}
                value={policy.holdDurationMinutes}
                onChange={(event) =>
                  setPolicy((current) => ({
                    ...current,
                    holdDurationMinutes: Number(event.target.value),
                  }))
                }
              />
            </label>
            <label className="field">
              <span>Крайний заезд до конца, мин</span>
              <input
                type="number"
                min={0}
                value={policy.arrivalDeadlineMinutesBeforeEnd}
                onChange={(event) =>
                  setPolicy((current) => ({
                    ...current,
                    arrivalDeadlineMinutesBeforeEnd: Number(event.target.value),
                  }))
                }
              />
            </label>
            <label className="field">
              <span>Обычный возврат, %</span>
              <input
                type="number"
                min={0}
                max={100}
                value={policy.standardCancellationRefundPercent}
                onChange={(event) =>
                  setPolicy((current) => ({
                    ...current,
                    standardCancellationRefundPercent: Number(event.target.value),
                  }))
                }
              />
            </label>
            <label className="field">
              <span>NO_SHOW возврат, %</span>
              <input
                type="number"
                min={0}
                max={100}
                value={policy.noShowRefundPercent}
                onChange={(event) =>
                  setPolicy((current) => ({
                    ...current,
                    noShowRefundPercent: Number(event.target.value),
                  }))
                }
              />
            </label>
            <label className="field">
              <span>Макс. длительность, мин</span>
              <input
                type="number"
                min={1}
                value={policy.maxBookingDurationMinutes}
                onChange={(event) =>
                  setPolicy((current) => ({
                    ...current,
                    maxBookingDurationMinutes: Number(event.target.value),
                  }))
                }
              />
            </label>
            <label className="field">
              <span>Бронирование вперёд, дней</span>
              <input
                type="number"
                min={1}
                value={policy.maxBookingAheadDays}
                onChange={(event) =>
                  setPolicy((current) => ({
                    ...current,
                    maxBookingAheadDays: Number(event.target.value),
                  }))
                }
              />
            </label>
          </div>

          <div className="admin-form__actions">
            <button type="submit" className="primary-button" disabled={savingSettings}>
              {savingSettings ? 'Сохранение...' : 'Сохранить настройки'}
            </button>
          </div>
        </form>
      </section>

      <div className="admin-grid">
        <section className="panel panel--elevated">
          <div className="panel__header">
            <div>
              <h3 className="panel__title">Площадки</h3>
              <p className="panel__meta">{lots.length}</p>
            </div>
          </div>

          <form className="admin-form" onSubmit={handleLotSubmit}>
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
                <button type="button" className="secondary-button" onClick={resetLotForm}>
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
                          <button
                            type="button"
                            className="secondary-button"
                            onClick={() => {
                              setEditingLotId(lot.id)
                              setLotForm({
                                code: lot.code,
                                name: lot.name,
                                address: lot.address ?? '',
                                active: lot.active,
                              })
                            }}
                          >
                            Изменить
                          </button>
                          <button
                            type="button"
                            className="secondary-button secondary-button--danger"
                            onClick={() => handleDelete('lot', lot.id)}
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

        <section className="panel panel--elevated">
          <div className="panel__header">
            <div>
              <h3 className="panel__title">Зоны</h3>
              <p className="panel__meta">{zones.length}</p>
            </div>
          </div>

          <form className="admin-form" onSubmit={handleZoneSubmit}>
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
                <button type="button" className="secondary-button" onClick={resetZoneForm}>
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
                          <button
                            type="button"
                            className="secondary-button"
                            onClick={() => {
                              setEditingZoneId(zone.id)
                              setZoneForm({
                                lotId: zone.lotId,
                                code: zone.code,
                                name: zone.name,
                                level: zone.level ?? '',
                                active: zone.active,
                              })
                            }}
                          >
                            Изменить
                          </button>
                          <button
                            type="button"
                            className="secondary-button secondary-button--danger"
                            onClick={() => handleDelete('zone', zone.id)}
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
      </div>

      <section className="panel panel--elevated">
        <div className="panel__header">
          <div>
            <h3 className="panel__title">Парковочные места</h3>
            <p className="panel__meta">Редактирование через схему парковки</p>
          </div>

          <button type="button" className="secondary-button" onClick={resetSpotForm}>
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
                  if (!nextSpot) {
                    return
                  }
                  editSpot(nextSpot)
                }}
              />
            </div>

            <div className="admin-spot-workspace__editor">
              <form className="admin-form" onSubmit={handleSpotSubmit}>
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
                      <button type="button" className="secondary-button" onClick={resetSpotForm}>
                        Сбросить
                      </button>
                      <button
                        type="button"
                        className="secondary-button secondary-button--danger"
                        onClick={() => handleDelete('spot', editingSpotId)}
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
    </div>
  )
}
