package resenkov.work.parkinguserservice.entity;

import lombok.Getter;

@Getter
public enum AccountStatus {
    BLOCKED("BLOCKED"),
    OPEN("OPEN"),
    CLOSED("CLOSED");

    private final String value;

    AccountStatus(String value) {
        this.value = value;
    }
}
