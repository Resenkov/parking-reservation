package resenkov.work.parkinguserservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import resenkov.work.parkinguserservice.dto.AuthResponse;
import resenkov.work.parkinguserservice.dto.RegistrationRequest;
import resenkov.work.parkinguserservice.dto.UpdateUserRequest;
import resenkov.work.parkinguserservice.entity.User;
import resenkov.work.parkinguserservice.service.UserService;
import resenkov.work.parkinguserservice.util.JwtUtils;

@RestController
@Log4j2
@RequestMapping("/user")
@Validated
public class UserController {
    private final UserService service;
    private final JwtUtils jwtUtils;

    public UserController(UserService service, JwtUtils jwtUtils) {
        this.service = service;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/email")
    public ResponseEntity<User> findByEmail(@RequestParam(required = false) @Email(message = "Email должен быть корректным") String email,
                                            Authentication authentication) {
        String effectiveEmail = authentication != null ? authentication.getName() : email;
        if (effectiveEmail == null) {
            log.warn("Отклонён запрос получения пользователя: не передан email и отсутствует аутентификация");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Получен запрос на получение пользователя по email={}", effectiveEmail);
        return ResponseEntity.ok(service.findByEmail(effectiveEmail));
    }

    @GetMapping("/me")
    public ResponseEntity<User> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            log.warn("Отклонён запрос /user/me: отсутствует аутентификация");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Получен запрос на профиль текущего пользователя: email={}", authentication.getName());
        return ResponseEntity.ok(service.findByEmail(authentication.getName()));
    }

    @PostMapping("/add")
    public ResponseEntity<AuthResponse> addUser(@Valid @RequestBody RegistrationRequest request) {
        log.info(
                "Получен запрос на регистрацию пользователя: email={}, firstName={}, lastName={}",
                request.getEmail(),
                request.getFirstName(),
                request.getLastName()
        );
        User user = service.registerUser(request);
        String token = jwtUtils.generateToken(user);
        log.info("Регистрация успешно завершена, JWT сформирован: userId={}, email={}", user.getId(), user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PutMapping("/update")
    public ResponseEntity<User> updateUser(Authentication authentication,
                                           @Valid @RequestBody UpdateUserRequest request) {
        if (authentication == null || authentication.getName() == null) {
            log.warn("Отклонён запрос обновления профиля: отсутствует аутентификация");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            log.warn("Отклонён запрос обновления: попытка изменить email пользователем {}", authentication.getName());
            throw new IllegalArgumentException("Изменение email через этот метод запрещено");
        }
        log.info("Получен запрос обновления профиля: email={}", authentication.getName());
        return ResponseEntity.ok(service.updateUserByEmail(authentication.getName(), request));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            log.warn("Отклонён запрос удаления пользователя: отсутствует аутентификация");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Получен запрос удаления пользователя: email={}", authentication.getName());
        service.deleteUserByEmail(authentication.getName());
        log.info("Пользователь удалён: email={}", authentication.getName());
        return ResponseEntity.ok().build();
    }
}
