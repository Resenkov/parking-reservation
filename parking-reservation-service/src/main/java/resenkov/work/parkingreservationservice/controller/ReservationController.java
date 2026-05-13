package resenkov.work.parkingreservationservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import resenkov.work.parkingreservationservice.dto.ParkingLayoutResponse;
import resenkov.work.parkingreservationservice.dto.ParkingSpotAvailabilityResponse;
import resenkov.work.parkingreservationservice.dto.ReservationCatalogResponse;
import resenkov.work.parkingreservationservice.dto.ReservationPolicySettingsResponse;
import resenkov.work.parkingreservationservice.dto.ReservationRequest;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.service.ReservationPolicyService;
import resenkov.work.parkingreservationservice.service.ReservationService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/reservation")
@Validated
@Log4j2
public class ReservationController {

    private final ReservationService service;
    private final ReservationPolicyService reservationPolicyService;

    public ReservationController(ReservationService service, ReservationPolicyService reservationPolicyService) {
        this.service = service;
        this.reservationPolicyService = reservationPolicyService;
    }

    @PostMapping("/book")
    public ResponseEntity<Reservation> book(@Valid @RequestBody ReservationRequest req, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        Long userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        Reservation reservation = service.createReservation(
                userId,
                authentication.getName(),
                req.getSpotCode(),
                req.getFrom(),
                req.getTo()
        );
        return ResponseEntity.ok(reservation);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Reservation> confirm(@PathVariable @Positive Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.confirmReservation(id, principal.getName()));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Reservation> activate(@PathVariable @Positive Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.activateReservation(id, principal.getName()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Reservation> complete(@PathVariable @Positive Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.completeReservation(id, principal.getName()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Reservation>> myReservations(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.findByUserEmail(principal.getName()));
    }

    @PostMapping("/cancel/{id}")
    public ResponseEntity<Reservation> cancel(@PathVariable @Positive Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.cancelReservation(id, principal.getName()));
    }

    @GetMapping("/public/layout")
    public ResponseEntity<ParkingLayoutResponse> publicLayout(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String level) {
        return ResponseEntity.ok(service.getParkingLayout(from, to, zone, level));
    }

    @GetMapping("/public/settings")
    public ResponseEntity<ReservationPolicySettingsResponse> publicSettings() {
        return ResponseEntity.ok(reservationPolicyService.getSettingsResponse());
    }

    @GetMapping("/spots/available")
    public ResponseEntity<List<ParkingSpotAvailabilityResponse>> availableSpots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String level,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.findAvailableSpots(from, to, zone, level));
    }

    @GetMapping("/catalog")
    public ResponseEntity<ReservationCatalogResponse> catalog(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.getReservationCatalog());
    }

    private Long extractUserId(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details == null) {
            return null;
        }
        if (details instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(details.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
