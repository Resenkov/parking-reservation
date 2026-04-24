package resenkov.work.parkinguserservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @Size(min = 2, max = 64, message = "Имя должно быть длиной от 2 до 64 символов")
    private String firstName;

    @Size(min = 2, max = 64, message = "Фамилия должна быть длиной от 2 до 64 символов")
    private String lastName;

    @Size(min = 8, max = 100, message = "Пароль должен быть длиной от 8 до 100 символов")
    private String password;

    @Email(message = "Email должен быть корректным")
    private String email;
}
