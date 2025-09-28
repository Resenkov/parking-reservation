package resenkov.work.parkingreservationservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReservationRequest {
    private String spotCode;
    private LocalDateTime from;
    private LocalDateTime to;
}
