import { AdminStats } from './admin/AdminStats'
import { ParkingLotsPanel } from './admin/ParkingLotsPanel'
import { ParkingSpotsPanel } from './admin/ParkingSpotsPanel'
import { ParkingZonesPanel } from './admin/ParkingZonesPanel'
import { PolicySettingsPanel } from './admin/PolicySettingsPanel'
import { useAdminData } from './admin/useAdminData'

export function AdminPage() {
  const admin = useAdminData()

  if (admin.loading) {
    return <div className="admin-loading">Загрузка админских данных...</div>
  }

  return (
    <div className="page-grid admin-page">
      <AdminStats lots={admin.lots} zones={admin.zones} spots={admin.spots} />

      <PolicySettingsPanel
        policy={admin.policy}
        savingSettings={admin.savingSettings}
        setPolicy={admin.setPolicy}
        onSubmit={admin.handleSettingsSubmit}
      />

      <div className="admin-grid">
        <ParkingLotsPanel
          lots={admin.lots}
          lotForm={admin.lotForm}
          editingLotId={admin.editingLotId}
          setLotForm={admin.setLotForm}
          onSubmit={admin.handleLotSubmit}
          onReset={admin.resetLotForm}
          onEdit={admin.editLot}
          onDelete={(id) => admin.handleDelete('lot', id)}
        />

        <ParkingZonesPanel
          lots={admin.lots}
          zones={admin.zones}
          zoneForm={admin.zoneForm}
          editingZoneId={admin.editingZoneId}
          setZoneForm={admin.setZoneForm}
          onSubmit={admin.handleZoneSubmit}
          onReset={admin.resetZoneForm}
          onEdit={admin.editZone}
          onDelete={(id) => admin.handleDelete('zone', id)}
        />
      </div>

      <ParkingSpotsPanel
        zones={admin.zones}
        spots={admin.spots}
        adminSchemeSpots={admin.adminSchemeSpots}
        selectedSpot={admin.selectedSpot}
        selectedSpotCode={admin.selectedSpotCode}
        spotForm={admin.spotForm}
        editingSpotId={admin.editingSpotId}
        setSpotForm={admin.setSpotForm}
        onSubmit={admin.handleSpotSubmit}
        onReset={admin.resetSpotForm}
        onEditSpot={admin.editSpot}
        onDelete={(id) => admin.handleDelete('spot', id)}
      />
    </div>
  )
}
