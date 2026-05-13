package resenkov.work.parkingreservationservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import resenkov.work.parkingreservationservice.dto.ReservationPolicySettingsResponse;
import resenkov.work.parkingreservationservice.dto.admin.ParkingLotRequest;
import resenkov.work.parkingreservationservice.dto.admin.ParkingLotResponse;
import resenkov.work.parkingreservationservice.dto.admin.ParkingSpotRequest;
import resenkov.work.parkingreservationservice.dto.admin.ParkingSpotResponse;
import resenkov.work.parkingreservationservice.dto.admin.ParkingZoneRequest;
import resenkov.work.parkingreservationservice.dto.admin.ParkingZoneResponse;
import resenkov.work.parkingreservationservice.dto.admin.ReservationPolicySettingsUpdateRequest;
import resenkov.work.parkingreservationservice.service.ParkingAdminService;
import resenkov.work.parkingreservationservice.service.ReservationPolicyService;

import java.util.List;

@RestController
@RequestMapping("/reservation/admin")
@Validated
public class ParkingAdminController {

    private final ParkingAdminService parkingAdminService;
    private final ReservationPolicyService reservationPolicyService;

    public ParkingAdminController(ParkingAdminService parkingAdminService,
                                  ReservationPolicyService reservationPolicyService) {
        this.parkingAdminService = parkingAdminService;
        this.reservationPolicyService = reservationPolicyService;
    }

    @GetMapping("/settings")
    public ResponseEntity<ReservationPolicySettingsResponse> getSettings() {
        return ResponseEntity.ok(reservationPolicyService.getSettingsResponse());
    }

    @PutMapping("/settings")
    public ResponseEntity<ReservationPolicySettingsResponse> updateSettings(
            @Valid @RequestBody ReservationPolicySettingsUpdateRequest request) {
        return ResponseEntity.ok(reservationPolicyService.updateSettings(request));
    }

    @GetMapping("/lots")
    public ResponseEntity<List<ParkingLotResponse>> getLots() {
        return ResponseEntity.ok(parkingAdminService.getLots());
    }

    @GetMapping("/lots/{id}")
    public ResponseEntity<ParkingLotResponse> getLot(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(parkingAdminService.getLot(id));
    }

    @PostMapping("/lots")
    public ResponseEntity<ParkingLotResponse> createLot(@Valid @RequestBody ParkingLotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parkingAdminService.createLot(request));
    }

    @PutMapping("/lots/{id}")
    public ResponseEntity<ParkingLotResponse> updateLot(@PathVariable @Positive Long id,
                                                        @Valid @RequestBody ParkingLotRequest request) {
        return ResponseEntity.ok(parkingAdminService.updateLot(id, request));
    }

    @DeleteMapping("/lots/{id}")
    public ResponseEntity<Void> deleteLot(@PathVariable @Positive Long id) {
        parkingAdminService.deleteLot(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/zones")
    public ResponseEntity<List<ParkingZoneResponse>> getZones(@RequestParam(required = false) Long lotId) {
        return ResponseEntity.ok(parkingAdminService.getZones(lotId));
    }

    @GetMapping("/zones/{id}")
    public ResponseEntity<ParkingZoneResponse> getZone(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(parkingAdminService.getZone(id));
    }

    @PostMapping("/zones")
    public ResponseEntity<ParkingZoneResponse> createZone(@Valid @RequestBody ParkingZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parkingAdminService.createZone(request));
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<ParkingZoneResponse> updateZone(@PathVariable @Positive Long id,
                                                          @Valid @RequestBody ParkingZoneRequest request) {
        return ResponseEntity.ok(parkingAdminService.updateZone(id, request));
    }

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable @Positive Long id) {
        parkingAdminService.deleteZone(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/spots")
    public ResponseEntity<List<ParkingSpotResponse>> getSpots(@RequestParam(required = false) Long zoneId,
                                                              @RequestParam(required = false) String zoneCode,
                                                              @RequestParam(required = false) String level,
                                                              @RequestParam(required = false) String code,
                                                              @RequestParam(required = false) Boolean occupied) {
        return ResponseEntity.ok(parkingAdminService.getSpots(zoneId, zoneCode, level, code, occupied));
    }

    @GetMapping("/spots/{id}")
    public ResponseEntity<ParkingSpotResponse> getSpot(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(parkingAdminService.getSpot(id));
    }

    @PostMapping("/spots")
    public ResponseEntity<ParkingSpotResponse> createSpot(@Valid @RequestBody ParkingSpotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parkingAdminService.createSpot(request));
    }

    @PutMapping("/spots/{id}")
    public ResponseEntity<ParkingSpotResponse> updateSpot(@PathVariable @Positive Long id,
                                                          @Valid @RequestBody ParkingSpotRequest request) {
        return ResponseEntity.ok(parkingAdminService.updateSpot(id, request));
    }

    @DeleteMapping("/spots/{id}")
    public ResponseEntity<Void> deleteSpot(@PathVariable @Positive Long id) {
        parkingAdminService.deleteSpot(id);
        return ResponseEntity.noContent().build();
    }
}
