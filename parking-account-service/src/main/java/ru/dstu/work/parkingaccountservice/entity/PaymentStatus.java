package ru.dstu.work.parkingaccountservice.entity;

public enum PaymentStatus {
    NEW,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
