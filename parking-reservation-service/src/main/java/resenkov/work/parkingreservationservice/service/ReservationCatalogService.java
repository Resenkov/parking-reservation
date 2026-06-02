package resenkov.work.parkingreservationservice.service;

import org.springframework.stereotype.Service;
import resenkov.work.parkingreservationservice.dto.ParkingLayoutResponse;
import resenkov.work.parkingreservationservice.dto.ParkingLayoutSpotResponse;
import resenkov.work.parkingreservationservice.dto.ParkingSpotAvailabilityResponse;
import resenkov.work.parkingreservationservice.dto.ReservationCatalogResponse;
import resenkov.work.parkingreservationservice.dto.ReservationLevelOptionResponse;
import resenkov.work.parkingreservationservice.dto.ReservationZoneOptionResponse;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.ParkingZone;
import resenkov.work.parkingreservationservice.entity.ReservationPolicySettings;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ParkingZoneRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReservationCatalogService {

    private final ParkingSpotRepository spotRepository;
    private final ParkingZoneRepository zoneRepository;
    private final ReservationPolicyService reservationPolicyService;
    private final ReservationRules rules;
    private final Clock clock;

    public ReservationCatalogService(ParkingSpotRepository spotRepository,
                                     ParkingZoneRepository zoneRepository,
                                     ReservationPolicyService reservationPolicyService,
                                     ReservationRules rules,
                                     Clock clock) {
        this.spotRepository = spotRepository;
        this.zoneRepository = zoneRepository;
        this.reservationPolicyService = reservationPolicyService;
        this.rules = rules;
        this.clock = clock;
    }

    public ReservationCatalogResponse getReservationCatalog() {
        List<ParkingSpot> spots = spotRepository.findAllByOrderByCodeAsc();

        Map<String, Long> spotCountByLevel = spots.stream()
                .map(ParkingSpot::getLevel)
                .map(rules::normalizeFilter)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> spotCountByZone = spots.stream()
                .map(ParkingSpot::getZone)
                .map(rules::normalizeFilter)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        Map<String, ParkingZone> zoneByCode = zoneRepository.findAllByOrderByCodeAsc().stream()
                .filter(zone -> rules.normalizeFilter(zone.getCode()) != null)
                .collect(Collectors.toMap(
                        zone -> rules.normalizeFilter(zone.getCode()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<ReservationLevelOptionResponse> levels = spotCountByLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String::compareToIgnoreCase))
                .map(entry -> ReservationLevelOptionResponse.builder()
                        .code(entry.getKey())
                        .spotCount(entry.getValue())
                        .build())
                .toList();

        List<ReservationZoneOptionResponse> zones = spotCountByZone.entrySet().stream()
                .map(entry -> {
                    ParkingZone zone = zoneByCode.get(entry.getKey());
                    return ReservationZoneOptionResponse.builder()
                            .code(entry.getKey())
                            .name(zone == null ? null : rules.normalizeNullable(zone.getName()))
                            .level(zone == null ? rules.findLevelForZone(spots, entry.getKey()) : rules.normalizeFilter(zone.getLevel()))
                            .spotCount(entry.getValue())
                            .build();
                })
                .sorted(Comparator
                        .comparing(ReservationZoneOptionResponse::getLevel, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ReservationZoneOptionResponse::getCode, String::compareToIgnoreCase))
                .toList();

        return ReservationCatalogResponse.builder()
                .levels(levels)
                .zones(zones)
                .build();
    }

    public List<ParkingSpotAvailabilityResponse> findAvailableSpots(LocalDateTime from,
                                                                    LocalDateTime to,
                                                                    String zone,
                                                                    String level) {
        ReservationPolicySettings settings = reservationPolicyService.getSettings();
        rules.validateSpotSearchWindow(currentTime(), from, to, settings);

        List<ParkingSpot> spots = spotRepository.findAvailableSpots(
                rules.normalizeFilter(zone),
                rules.normalizeFilter(level),
                from,
                to,
                rules.busyReservationStatuses()
        );

        return spots.stream()
                .map(spot -> ParkingSpotAvailabilityResponse.builder()
                        .id(spot.getId())
                        .code(spot.getCode())
                        .zone(spot.getZone())
                        .level(spot.getLevel())
                        .price(spot.getPrice())
                        .occupied(spot.isOccupied())
                        .build())
                .toList();
    }

    public ParkingLayoutResponse getParkingLayout(LocalDateTime from,
                                                  LocalDateTime to,
                                                  String zone,
                                                  String level) {
        ReservationPolicySettings settings = reservationPolicyService.getSettings();
        LocalDateTime now = currentTime();
        boolean hasInterval = from != null || to != null;

        if (hasInterval && (from == null || to == null)) {
            throw new IllegalArgumentException("Both from and to are required");
        }
        if (hasInterval) {
            rules.validateSpotSearchWindow(now, from, to, settings);
        }

        String normalizedZone = rules.normalizeFilter(zone);
        String normalizedLevel = rules.normalizeFilter(level);
        List<ParkingSpot> allSpots = normalizedZone == null && normalizedLevel == null
                ? spotRepository.findAllByOrderByCodeAsc()
                : spotRepository.search(normalizedZone, normalizedLevel, null);

        Map<Long, ParkingZone> zonesById = getZonesById(allSpots);
        Set<Long> availableSpotIds = hasInterval
                ? spotRepository.findAvailableSpots(normalizedZone, normalizedLevel, from, to, rules.busyReservationStatuses()).stream()
                .map(ParkingSpot::getId)
                .collect(Collectors.toCollection(HashSet::new))
                : Set.of();

        List<ParkingLayoutSpotResponse> spots = allSpots.stream()
                .map(spot -> {
                    ParkingZone parkingZone = spot.getZoneId() == null ? null : zonesById.get(spot.getZoneId());
                    return ParkingLayoutSpotResponse.builder()
                            .id(spot.getId())
                            .code(spot.getCode())
                            .zone(rules.normalizeNullable(spot.getZone()))
                            .zoneName(parkingZone == null ? null : rules.normalizeNullable(parkingZone.getName()))
                            .level(rules.normalizeNullable(spot.getLevel()))
                            .price(spot.getPrice())
                            .occupied(spot.isOccupied())
                            .available(hasInterval ? availableSpotIds.contains(spot.getId()) : !spot.isOccupied())
                            .build();
                })
                .toList();

        return ParkingLayoutResponse.builder()
                .generatedAt(now)
                .from(from)
                .to(to)
                .spots(spots)
                .build();
    }

    private Map<Long, ParkingZone> getZonesById(List<ParkingSpot> spots) {
        Set<Long> zoneIds = spots.stream()
                .map(ParkingSpot::getZoneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (zoneIds.isEmpty()) {
            return Map.of();
        }
        return zoneRepository.findByIdIn(zoneIds).stream()
                .collect(Collectors.toMap(ParkingZone::getId, Function.identity()));
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(clock);
    }
}
