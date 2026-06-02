import type { FormEvent } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import { apiRequest } from '../../lib/api'
import type {
  ParkingLayoutSpot,
  ParkingLot,
  ParkingLotPayload,
  ParkingSpot,
  ParkingSpotPayload,
  ParkingZone,
  ParkingZonePayload,
  ReservationPolicySettings,
} from '../../types/models'
import { defaultLotForm, defaultPolicy, defaultSpotForm, defaultZoneForm } from './adminDefaults'

export function useAdminData() {
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

  const adminRequest = useCallback(
    async <T,>(path: string, init?: { method?: string; body?: unknown }) => {
      if (!session?.token) {
        throw new Error('Сессия отсутствует')
      }
      return apiRequest<T>(path, {
        method: init?.method,
        body: init?.body,
        token: session.token,
      })
    },
    [session],
  )

  const loadAdminData = useCallback(async () => {
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
  }, [adminRequest, showToast])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadAdminData()
  }, [loadAdminData])

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

  const resetLotForm = useCallback(() => {
    setLotForm(defaultLotForm)
    setEditingLotId(null)
  }, [])

  const resetZoneForm = useCallback(() => {
    setZoneForm({
      ...defaultZoneForm,
      lotId: lots[0]?.id ?? 0,
    })
    setEditingZoneId(null)
  }, [lots])

  const resetSpotForm = useCallback(() => {
    setSpotForm({
      ...defaultSpotForm,
      zoneId: zones[0]?.id ?? 0,
    })
    setEditingSpotId(null)
    setSelectedSpotCode(null)
  }, [zones])

  const editLot = useCallback((lot: ParkingLot) => {
    setEditingLotId(lot.id)
    setLotForm({
      code: lot.code,
      name: lot.name,
      address: lot.address ?? '',
      active: lot.active,
    })
  }, [])

  const editZone = useCallback((zone: ParkingZone) => {
    setEditingZoneId(zone.id)
    setZoneForm({
      lotId: zone.lotId,
      code: zone.code,
      name: zone.name,
      level: zone.level ?? '',
      active: zone.active,
    })
  }, [])

  const editSpot = useCallback((spot: ParkingSpot) => {
    setEditingSpotId(spot.id)
    setSelectedSpotCode(spot.code)
    setSpotForm({
      zoneId: spot.zoneId,
      code: spot.code,
      price: spot.price,
    })
  }, [])

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

  return {
    loading,
    savingSettings,
    policy,
    lots,
    zones,
    spots,
    lotForm,
    zoneForm,
    spotForm,
    editingLotId,
    editingZoneId,
    editingSpotId,
    selectedSpotCode,
    adminSchemeSpots,
    selectedSpot,
    setPolicy,
    setLotForm,
    setZoneForm,
    setSpotForm,
    resetLotForm,
    resetZoneForm,
    resetSpotForm,
    editLot,
    editZone,
    editSpot,
    handleSettingsSubmit,
    handleLotSubmit,
    handleZoneSubmit,
    handleSpotSubmit,
    handleDelete,
  }
}
