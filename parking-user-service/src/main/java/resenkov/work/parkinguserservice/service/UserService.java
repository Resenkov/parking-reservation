package resenkov.work.parkinguserservice.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkinguserservice.client.AccountServiceClient;
import resenkov.work.parkinguserservice.dto.RegistrationRequest;
import resenkov.work.parkinguserservice.dto.UpdateUserRequest;
import resenkov.work.parkinguserservice.entity.User;
import resenkov.work.parkinguserservice.exception.DuplicateEmailException;
import resenkov.work.parkinguserservice.repository.UserRepository;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountServiceClient accountServiceClient;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AccountServiceClient accountServiceClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountServiceClient = accountServiceClient;
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден: " + email));
    }

    @Transactional
    public User registerUser(RegistrationRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        User user = new User();
        user.setFirstName(normalizeRequiredValue(request.getFirstName(), "Имя"));
        user.setLastName(normalizeRequiredValue(request.getLastName(), "Фамилия"));
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(new HashSet<>(Set.of("USER")));

        User savedUser = userRepository.save(user);
        try {
            accountServiceClient.createWalletIfAbsent(savedUser.getId(), savedUser.getEmail());
        } catch (RuntimeException ex) {
            userRepository.delete(savedUser);
            throw ex;
        }

        return savedUser;
    }

    @Transactional
    public User updateUserByEmail(String email, UpdateUserRequest request) {
        User user = findByEmail(email);

        if (hasText(request.getFirstName())) {
            user.setFirstName(normalizeRequiredValue(request.getFirstName(), "Имя"));
        }
        if (hasText(request.getLastName())) {
            user.setLastName(normalizeRequiredValue(request.getLastName(), "Фамилия"));
        }
        if (hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword().trim()));
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUserByEmail(String email) {
        User user = findByEmail(email);
        userRepository.delete(user);
    }

    private String normalizeEmail(String email) {
        if (!hasText(email)) {
            throw new IllegalArgumentException("Email обязателен");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredValue(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " обязательно");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
