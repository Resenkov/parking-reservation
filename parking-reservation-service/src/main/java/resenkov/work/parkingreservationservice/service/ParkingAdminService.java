package resenkov.work.parkingreservationservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkingreservationservice.dto.admin.ParkingLotRequest;
import resenkov.work.parkingreservationservice.dto.admin.ParkingLotResponse;
import resenkov.work.parkingreservationservice.dto.admin.ParkingSpotRequest;
import resenkov.work.parkingreservationservice.dto.admin.ParkingSpotResponse;
import resenkov.work.parkingreservationservice.dto.admin.ParkingZoneRequest;
import resenkov.work.parkingreservationservice.dto.admin.ParkingZoneResponse;
import resenkov.work.parkingreservationservice.entity.ParkingLot;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.ParkingZone;
import resenkov.work.parkingreservationservice.repository.ParkingLotRepository;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ParkingZoneRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ParkingAdminService {

    private final ParkingLotRepository lotRepository;
    private final ParkingZoneRepository zoneRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;

    public ParkingAdminService(ParkingLotRepository lotRepository,
                               ParkingZoneRepository zoneRepository,
                               ParkingSpotRepository spotRepository,
                               ReservationRepository reservationRepository) {
        this.lotRepository = lotRepository;
        this.zoneRepository = zoneRepository;
        this.spotRepository = spotRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public List<ParkingLotResponse> getLots() {
        return lotRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toLotResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingLotResponse getLot(Long lotId) {
        return toLotResponse(getLotEntity(lotId));
    }

    @Transactional
    public ParkingLotResponse createLot(ParkingLotRequest request) {
        String normalizedCode = normalizeCode(request.getCode(), "lot code");
        if (lotRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Parking lot code already exists: " + normalizedCode);
        }

        ParkingLot lot = new ParkingLot();
        lot.setCode(normalizedCode);
        lot.setName(normalizeText(request.getName(), "lot name"));
        lot.setAddress(normalizeNullable(request.getAddress()));
        lot.setActive(request.isActive());

        ParkingLot saved = lotRepository.save(lot);
        log.info("Parking lot created: id={}, code={}", saved.getId(), saved.getCode());
        return toLotResponse(saved);
    }

    @Transactional
    public ParkingLotResponse updateLot(Long lotId, ParkingLotRequest request) {
        ParkingLot lot = getLotEntity(lotId);
        String normalizedCode = normalizeCode(request.getCode(), "lot code");
        if (!lot.getCode().equalsIgnoreCase(normalizedCode) && lotRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Parking lot code already exists: " + normalizedCode);
        }

        lot.setCode(normalizedCode);
        lot.setName(normalizeText(request.getName(), "lot name"));
        lot.setAddress(normalizeNullable(request.getAddress()));
        lot.setActive(request.isActive());

        ParkingLot saved = lotRepository.save(lot);
        log.info("Parking lot updated: id={}, code={}", saved.getId(), saved.getCode());
        return toLotResponse(saved);
    }

    @Transactional
    public void deleteLot(Long lotId) {
        ParkingLot lot = getLotEntity(lotId);
        if (zoneRepository.existsByLotId(lotId)) {
            throw new IllegalStateException("Cannot delete parking lot with existing zones");
        }
        lotRepository.delete(lot);
        log.info("Parking lot deleted: id={}, code={}", lot.getId(), lot.getCode());
    }

    @Transactional(readOnly = true)
    public List<ParkingZoneResponse> getZones(Long lotId) {
        List<ParkingZone> zones = lotId == null
                ? zoneRepository.findAllByOrderByCodeAsc()
                : zoneRepository.findByLotIdOrderByCodeAsc(lotId);
        Map<Long, ParkingLot> lots = getLotsByIds(zones.stream().map(ParkingZone::getLotId).collect(Collectors.toSet()));
        return zones.stream()
                .map(zone -> toZoneResponse(zone, lots.get(zone.getLotId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingZoneResponse getZone(Long zoneId) {
        ParkingZone zone = getZoneEntity(zoneId);
        ParkingLot lot = getLotEntity(zone.getLotId());
        return toZoneResponse(zone, lot);
    }

    @Transactional
    public ParkingZoneResponse createZone(ParkingZoneRequest request) {
        ParkingLot lot = getLotEntity(request.getLotId());
        String normalizedCode = normalizeCode(request.getCode(), "zone code");
        if (zoneRepository.existsByLotIdAndCodeIgnoreCase(lot.getId(), normalizedCode)) {
            throw new IllegalArgumentException("Parking zone code already exists in lot: " + normalizedCode);
        }

        ParkingZone zone = new ParkingZone();
        zone.setLotId(lot.getId());
        zone.setCode(normalizedCode);
        zone.setName(normalizeText(request.getName(), "zone name"));
        zone.setLevel(normalizeNullable(request.getLevel()));
        zone.setActive(request.isActive());

        ParkingZone saved = zoneRepository.save(zone);
        log.info("Parking zone created: id={}, lotId={}, code={}", saved.getId(), saved.getLotId(), saved.getCode());
        return toZoneResponse(saved, lot);
    }

    @Transactional
    public ParkingZoneResponse updateZone(Long zoneId, ParkingZoneRequest request) {
        ParkingZone zone = getZoneEntity(zoneId);
        ParkingLot lot = getLotEntity(request.getLotId());
        String normalizedCode = normalizeCode(request.getCode(), "zone code");
        if ((!Objects.equals(zone.getLotId(), lot.getId()) || !zone.getCode().equalsIgnoreCase(normalizedCode))
                && zoneRepository.existsByLotIdAndCodeIgnoreCase(lot.getId(), normalizedCode)) {
            throw new IllegalArgumentException("Parking zone code already exists in lot: " + normalizedCode);
        }

        zone.setLotId(lot.getId());
        zone.setCode(normalizedCode);
        zone.setName(normalizeText(request.getName(), "zone name"));
        zone.setLevel(normalizeNullable(request.getLevel()));
        zone.setActive(request.isActive());

        ParkingZone saved = zoneRepository.save(zone);
        syncSpotMetadata(saved);
        log.info("Parking zone updated: id={}, lotId={}, code={}", saved.getId(), saved.getLotId(), saved.getCode());
        return toZoneResponse(saved, lot);
    }

    @Transactional
    public void deleteZone(Long zoneId) {
        ParkingZone zone = getZoneEntity(zoneId);
        if (spotRepository.countByZoneId(zoneId) > 0) {
            throw new IllegalStateException("Cannot delete parking zone with existing spots");
        }
        zoneRepository.delete(zone);
        log.info("Parking zone deleted: id={}, code={}", zone.getId(), zone.getCode());
    }

    @Transactional(readOnly = true)
    public List<ParkingSpotResponse> getSpots(Long zoneId, String zoneCode, String level, String code, Boolean occupied) {
        List<ParkingSpot> spots = zoneId != null
                ? spotRepository.findByZoneIdOrderByCodeAsc(zoneId)
                : spotRepository.search(zoneCode, level, occupied);

        if (zoneId == null && zoneCode == null && level == null && occupied == null) {
            spots = spotRepository.findAllByOrderByCodeAsc();
        }

        String normalizedCodeFilter = normalizeNullable(code);
        if (normalizedCodeFilter != null) {
            String codeFilter = normalizedCodeFilter.toLowerCase(Locale.ROOT);
            spots = spots.stream()
                    .filter(spot -> spot.getCode() != null && spot.getCode().toLowerCase(Locale.ROOT).contains(codeFilter))
                    .toList();
        }
        if (zoneId != null && occupied != null) {
            spots = spots.stream()
                    .filter(spot -> spot.isOccupied() == occupied)
                    .toList();
        }
        if (zoneId != null && level != null && !level.isBlank()) {
            String levelFilter = level.trim().toLowerCase(Locale.ROOT);
            spots = spots.stream()
                    .filter(spot -> spot.getLevel() != null && spot.getLevel().toLowerCase(Locale.ROOT).equals(levelFilter))
                    .toList();
        }

        return toSpotResponses(spots);
    }

    @Transactional(readOnly = true)
    public ParkingSpotResponse getSpot(Long spotId) {
        ParkingSpot spot = getSpotEntity(spotId);
        return toSpotResponses(List.of(spot)).get(0);
    }

    @Transactional
    public ParkingSpotResponse createSpot(ParkingSpotRequest request) {
        ParkingZone zone = getZoneEntity(request.getZoneId());
        String normalizedCode = normalizeCode(request.getCode(), "spot code");
        if (spotRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Parking spot code already exists: " + normalizedCode);
        }

        ParkingSpot spot = new ParkingSpot();
        applySpotRequest(spot, request, zone);

        ParkingSpot saved = spotRepository.save(spot);
        log.info("Parking spot created: id={}, zoneId={}, code={}", saved.getId(), saved.getZoneId(), saved.getCode());
        return toSpotResponses(List.of(saved)).get(0);
    }

    @Transactional
    public ParkingSpotResponse updateSpot(Long spotId, ParkingSpotRequest request) {
        ParkingSpot spot = getSpotEntity(spotId);
        String normalizedCode = normalizeCode(request.getCode(), "spot code");
        if (!spot.getCode().equalsIgnoreCase(normalizedCode) && spotRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Parking spot code already exists: " + normalizedCode);
        }

        ParkingZone zone = getZoneEntity(request.getZoneId());
        applySpotRequest(spot, request, zone);

        ParkingSpot saved = spotRepository.save(spot);
        log.info("Parking spot updated: id={}, zoneId={}, code={}", saved.getId(), saved.getZoneId(), saved.getCode());
        return toSpotResponses(List.of(saved)).get(0);
    }

    @Transactional
    public void deleteSpot(Long spotId) {
        ParkingSpot spot = getSpotEntity(spotId);
        if (reservationRepository.existsBySpotId(spotId)) {
            throw new IllegalStateException("Cannot delete parking spot with reservation history");
        }
        spotRepository.delete(spot);
        log.info("Parking spot deleted: id={}, code={}", spot.getId(), spot.getCode());
    }

    private void applySpotRequest(ParkingSpot spot, ParkingSpotRequest request, ParkingZone zone) {
        spot.setZoneId(zone.getId());
        spot.setCode(normalizeCode(request.getCode(), "spot code"));
        spot.setPrice(request.getPrice().setScale(2, java.math.RoundingMode.HALF_UP));
        spot.setZone(zone.getCode());
        spot.setLevel(zone.getLevel());
    }

    private void syncSpotMetadata(ParkingZone zone) {
        List<ParkingSpot> spots = spotRepository.findByZoneIdOrderByCodeAsc(zone.getId());
        if (spots.isEmpty()) {
            return;
        }
        for (ParkingSpot spot : spots) {
            spot.setZone(zone.getCode());
            spot.setLevel(zone.getLevel());
        }
        spotRepository.saveAll(spots);
    }

    private ParkingLot getLotEntity(Long lotId) {
        return lotRepository.findById(lotId)
                .orElseThrow(() -> new EntityNotFoundException("Parking lot not found: " + lotId));
    }

    private ParkingZone getZoneEntity(Long zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new EntityNotFoundException("Parking zone not found: " + zoneId));
    }

    private ParkingSpot getSpotEntity(Long spotId) {
        return spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Parking spot not found: " + spotId));
    }

    private List<ParkingSpotResponse> toSpotResponses(List<ParkingSpot> spots) {
        Map<Long, ParkingZone> zones = getZonesByIds(spots.stream()
                .map(ParkingSpot::getZoneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, ParkingLot> lots = getLotsByIds(zones.values().stream()
                .map(ParkingZone::getLotId)
                .collect(Collectors.toSet()));

        return spots.stream()
                .sorted(Comparator.comparing(ParkingSpot::getCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(spot -> {
                    ParkingZone zone = zones.get(spot.getZoneId());
                    ParkingLot lot = zone == null ? null : lots.get(zone.getLotId());
                    return ParkingSpotResponse.builder()
                            .id(spot.getId())
                            .zoneId(spot.getZoneId())
                            .zoneCode(zone != null ? zone.getCode() : spot.getZone())
                            .zoneName(zone != null ? zone.getName() : null)
                            .lotId(lot != null ? lot.getId() : null)
                            .lotCode(lot != null ? lot.getCode() : null)
                            .code(spot.getCode())
                            .occupied(spot.isOccupied())
                            .price(spot.getPrice())
                            .level(spot.getLevel())
                            .build();
                })
                .toList();
    }

    private ParkingLotResponse toLotResponse(ParkingLot lot) {
        return ParkingLotResponse.builder()
                .id(lot.getId())
                .code(lot.getCode())
                .name(lot.getName())
                .address(lot.getAddress())
                .active(lot.isActive())
                .build();
    }

    private ParkingZoneResponse toZoneResponse(ParkingZone zone, ParkingLot lot) {
        return ParkingZoneResponse.builder()
                .id(zone.getId())
                .lotId(zone.getLotId())
                .lotCode(lot != null ? lot.getCode() : null)
                .code(zone.getCode())
                .name(zone.getName())
                .level(zone.getLevel())
                .active(zone.isActive())
                .build();
    }

    private Map<Long, ParkingZone> getZonesByIds(Collection<Long> zoneIds) {
        if (zoneIds.isEmpty()) {
            return Map.of();
        }
        return zoneRepository.findByIdIn(zoneIds).stream()
                .collect(Collectors.toMap(ParkingZone::getId, Function.identity()));
    }

    private Map<Long, ParkingLot> getLotsByIds(Collection<Long> lotIds) {
        if (lotIds.isEmpty()) {
            return Map.of();
        }
        return lotRepository.findAllById(lotIds).stream()
                .collect(Collectors.toMap(ParkingLot::getId, Function.identity()));
    }

    private String normalizeCode(String value, String fieldName) {
        String normalized = normalizeText(value, fieldName).toUpperCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalized;
    }

    private String normalizeText(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
