package resenkov.work.parkingreservationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReservationRequest {
    @NotBlank(message = "Код парковочного места обязателен")
    private String spotCode;

    @NotNull(message = "Время начала бронирования обязательно")
    private LocalDateTime from;

    @NotNull(message = "Время окончания бронирования обязательно")
    private LocalDateTime to;
}
