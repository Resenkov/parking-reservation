package resenkov.work.parkingreservationservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import resenkov.work.parkingreservationservice.dto.ReservationRequest;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.service.ReservationService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/reservation")
@Validated
@Log4j2
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/book")
    public ResponseEntity<Reservation> book(@Valid @RequestBody ReservationRequest req, Authentication authentication) {
        if (authentication == null) {
            log.warn("Отклонён запрос бронирования: отсутствует аутентификация");
            return ResponseEntity.status(401).build();
        }
        String email = authentication.getName();
        Long userId = extractUserId(authentication);
        if (userId == null) {
            log.warn("Отклонён запрос бронирования: в JWT отсутствует userId для email={}", email);
            return ResponseEntity.status(401).build();
        }
        log.info(
                "Получен запрос на бронирование места: userId={}, email={}, spotCode={}, from={}, to={}",
                userId,
                email,
                req.getSpotCode(),
                req.getFrom(),
                req.getTo()
        );
        Reservation res = service.createReservation(userId, email, req.getSpotCode(), req.getFrom(), req.getTo());
        log.info("Бронирование обработано: reservationId={}, статус={}", res.getId(), res.getStatus());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Reservation> confirm(@PathVariable @Positive(message = "id должен быть больше 0") Long id,
                                               Principal principal) {
        if (principal == null) {
            log.warn("Отклонён запрос подтверждения брони {}: отсутствует аутентификация", id);
            return ResponseEntity.status(401).build();
        }
        log.info("Получен запрос подтверждения брони: reservationId={}, email={}", id, principal.getName());
        return ResponseEntity.ok(service.confirmReservation(id, principal.getName()));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Reservation> activate(@PathVariable @Positive(message = "id должен быть больше 0") Long id,
                                                Principal principal) {
        if (principal == null) {
            log.warn("Отклонён запрос активации брони {}: отсутствует аутентификация", id);
            return ResponseEntity.status(401).build();
        }
        log.info("Получен запрос активации брони: reservationId={}, email={}", id, principal.getName());
        return ResponseEntity.ok(service.activateReservation(id, principal.getName()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Reservation> complete(@PathVariable @Positive(message = "id должен быть больше 0") Long id,
                                                Principal principal) {
        if (principal == null) {
            log.warn("Отклонён запрос завершения брони {}: отсутствует аутентификация", id);
            return ResponseEntity.status(401).build();
        }
        log.info("Получен запрос завершения брони: reservationId={}, email={}", id, principal.getName());
        return ResponseEntity.ok(service.completeReservation(id, principal.getName()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Reservation>> myReservations(Principal principal) {
        if (principal == null) {
            log.warn("Отклонён запрос списка броней: отсутствует аутентификация");
            return ResponseEntity.status(401).build();
        }
        log.info("Получен запрос списка броней пользователя: email={}", principal.getName());
        List<Reservation> list = service.findByUserEmail(principal.getName());
        log.info("Список броней сформирован: email={}, count={}", principal.getName(), list.size());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/cancel/{id}")
    public ResponseEntity<Reservation> cancel(@PathVariable @Positive(message = "id должен быть больше 0") Long id,
                                              Principal principal) {
        if (principal == null) {
            log.warn("Отклонён запрос отмены брони {}: отсутствует аутентификация", id);
            return ResponseEntity.status(401).build();
        }
        log.info("Получен запрос отмены брони: reservationId={}, email={}", id, principal.getName());
        return ResponseEntity.ok(service.cancelReservation(id, principal.getName()));
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
