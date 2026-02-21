package resenkov.work.parkinguserservice.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
public class UserController {
    private final UserService service;
    private final JwtUtils jwtUtils;

    public UserController(UserService service, JwtUtils jwtUtils) {
        this.service = service;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/email")
    public ResponseEntity<User> findByEmail(@RequestParam(required = false) String email,
                                            Authentication authentication) {
        String effectiveEmail = authentication != null ? authentication.getName() : email;
        if (effectiveEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.findByEmail(effectiveEmail));
    }

    @GetMapping("/me")
    public ResponseEntity<User> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.findByEmail(authentication.getName()));
    }

    @PostMapping("/add")
    public ResponseEntity<AuthResponse> addUser(@RequestBody RegistrationRequest request) {
        User user = service.registerUser(request);
        String token = jwtUtils.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PutMapping("/update")
    public ResponseEntity<User> updateUser(Authentication authentication,
                                           @RequestBody UpdateUserRequest request) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            return new ResponseEntity<> (HttpStatus.NOT_ACCEPTABLE);
        }
        return ResponseEntity.ok(service.updateUserByEmail(authentication.getName(), request));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        service.deleteUserByEmail(authentication.getName());
        return ResponseEntity.ok().build();
    }
}
