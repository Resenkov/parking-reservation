package resenkov.work.parkinguserservice.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import resenkov.work.parkinguserservice.dto.AuthResponse;
import resenkov.work.parkinguserservice.dto.UpdateUserRequest;
import resenkov.work.parkinguserservice.dto.RegistrationRequest;
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
    public ResponseEntity<User> findByEmail(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String email,
            @RequestHeader(value = "X-User-Email", required = false) String authEmail) {
        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String tokenEmail = jwtUtils.extractUsername(authorization.substring(7));
        String effectiveEmail = tokenEmail != null ? tokenEmail : email;
        if (effectiveEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (authEmail != null && !authEmail.equals(effectiveEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(service.findByEmail(effectiveEmail));
    }

    @PostMapping("/add")
    public ResponseEntity<AuthResponse> addUser(@RequestBody RegistrationRequest request) {
        User user = service.registerUser(request);
        String token = jwtUtils.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PutMapping("/update")
    public ResponseEntity<User> updateUser(@RequestHeader("Authorization") String authorization,
                                           @RequestBody UpdateUserRequest request) {
        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            return new ResponseEntity<> (HttpStatus.NOT_ACCEPTABLE);
        }
        String tokenEmail = jwtUtils.extractUsername(authorization.substring(7));
        if (tokenEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.updateUserByEmail(tokenEmail, request));
    }
    @DeleteMapping("/delete")
    public ResponseEntity deleteUser(@RequestHeader("Authorization") String authorization){
        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String tokenEmail = jwtUtils.extractUsername(authorization.substring(7));
        if (tokenEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        service.deleteUserByEmail(tokenEmail);
        return ResponseEntity.ok().build();
    }
}
