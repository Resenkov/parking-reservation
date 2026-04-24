package resenkov.work.parkinguserservice.controller;

import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import resenkov.work.parkinguserservice.dto.AuthRequest;
import resenkov.work.parkinguserservice.dto.AuthResponse;
import resenkov.work.parkinguserservice.util.JwtUtils;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Log4j2
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Получен запрос на вход пользователя: email={}", request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        log.info("Пользователь успешно аутентифицирован: email={}", request.getEmail());

        final String token = jwtUtils.generateToken((UserDetails) authentication.getPrincipal());
        log.info("JWT успешно сформирован для пользователя: email={}", request.getEmail());

        return ResponseEntity.ok(new AuthResponse(token));
    }
}
