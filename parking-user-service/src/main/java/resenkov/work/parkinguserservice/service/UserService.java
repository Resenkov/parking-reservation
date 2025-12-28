package resenkov.work.parkinguserservice.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkinguserservice.dto.RegistrationRequest;
import resenkov.work.parkinguserservice.entity.Account;
import resenkov.work.parkinguserservice.entity.User;
import resenkov.work.parkinguserservice.repository.UserRepository;
import resenkov.work.parkinguserservice.dto.UpdateUserRequest;


@Service
@Transactional
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, AccountService accountService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
    }

    public User findByEmail(String email) {
        return repository.findByEmail(email).orElseThrow(
                ()->new EntityNotFoundException("User not found")
        );
    }

    public User registerUser(RegistrationRequest request) {
        if (repository.existsByEmail(request.getEmail())) {
            throw new EntityNotFoundException();
        }
        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        Account account = accountService.createDefaultAccount();
        user.setAccountId(account);
        return repository.save(user);
    }

    public User updateUser(User user) {
        return repository.save(user);
    }

    public User updateUserByEmail(String email, UpdateUserRequest request) {
        User user = findByEmail(email);
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return repository.save(user);
    }

    public void deleteUserByEmail(String email) {
        User user = findByEmail(email);
        repository.deleteById(user.getId());
    }
}
