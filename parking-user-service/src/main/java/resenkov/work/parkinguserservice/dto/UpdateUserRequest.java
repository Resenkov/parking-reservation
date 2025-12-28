package resenkov.work.parkinguserservice.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;
    private String password;
    private String email;
}
