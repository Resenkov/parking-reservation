package resenkov.work.parkinguserservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationRequest {
    @NotBlank(message = "Имя обязательно")
    @Size(min = 2, max = 64, message = "Имя должно быть длиной от 2 до 64 символов")
    private String firstName;

    @NotBlank(message = "Фамилия обязательна")
    @Size(min = 2, max = 64, message = "Фамилия должна быть длиной от 2 до 64 символов")
    private String lastName;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Email должен быть корректным")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 100, message = "Пароль должен быть длиной от 8 до 100 символов")
    private String password;
}
