package ru.dstu.work.parkingaccountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotNull Long userId,
        @NotBlank(message = "Email обязателен")
        @Email(message = "Email должен быть корректным")
        @Size(max = 255, message = "Email не должен быть длиннее 255 символов")
        String email
) {
}
