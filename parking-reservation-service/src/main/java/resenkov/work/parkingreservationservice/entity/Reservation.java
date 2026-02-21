package resenkov.work.parkingreservationservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservation")
@Getter
@Setter
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long spotId;
    private String spotCode;

    private String userEmail;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private LocalDateTime holdExpiresAt;
    private LocalDateTime arrivalDeadline;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal refundAmount;

    private Integer refundPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReservationStatus status;

    public enum ReservationStatus {
        HOLD,
        CONFIRMED,
        ACTIVE,
        COMPLETED,
        CANCELLED,
        EXPIRED,
        NO_SHOW
    }
}
