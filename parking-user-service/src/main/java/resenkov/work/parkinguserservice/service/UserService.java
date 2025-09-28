package resenkov.work.parkinguserservice.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkinguserservice.entity.User;
import resenkov.work.parkinguserservice.repository.UserRepository;


@Service
@Transactional
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public User findByEmail(String email) {
        return repository.findByEmail(email).orElseThrow(
                ()->new EntityNotFoundException("User not found")
        );
    }

    public User addUser(User user) {
        if (repository.existsByEmail(user.getEmail())) {
            throw new EntityNotFoundException();
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return repository.save(user);
    }

    public User updateUser(User user) {
        return repository.save(user);
    }

    public void deleteUserById(Long id) {
        repository.deleteById(id);
    }
}
