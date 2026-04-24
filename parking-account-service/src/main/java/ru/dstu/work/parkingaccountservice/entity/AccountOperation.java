package ru.dstu.work.parkingaccountservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_operation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_operation_id", columnNames = "operationId")
})
@Getter
@Setter
public class AccountOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String operationId;

    @Column(nullable = false)
    private String userEmail;

    private Long userId;

    @Column(nullable = false)
    private Long accountId;

    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 1024)
    private String details;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
