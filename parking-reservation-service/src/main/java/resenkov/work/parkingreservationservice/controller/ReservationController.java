package resenkov.work.parkingreservationservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.service.ReservationService;

import java.security.Principal;
import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    public static record ReservationRequest(String spotCode, LocalDateTime from, LocalDateTime to) {}

    @PostMapping("/book")
    public ResponseEntity<Reservation> book(@RequestBody ReservationRequest req, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        String email = principal.getName();
        Reservation res = service.createReservation(email, req.spotCode(), req.from(), req.to());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Reservation> confirm(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.confirmReservation(id, principal.getName()));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Reservation> activate(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.activateReservation(id, principal.getName()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Reservation> complete(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.completeReservation(id, principal.getName()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Reservation>> myReservations(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        List<Reservation> list = service.findByUserEmail(principal.getName());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/cancel/{id}")
    public ResponseEntity<Reservation> cancel(@PathVariable Long id, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.cancelReservation(id, principal.getName()));
    }
}
