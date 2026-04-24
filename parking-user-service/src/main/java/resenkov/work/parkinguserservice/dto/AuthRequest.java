package resenkov.work.parkinguserservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Email обязателен")
    @Email(message = "Email должен быть корректным")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 100, message = "Пароль должен быть длиной от 8 до 100 символов")
    private String password;
}
