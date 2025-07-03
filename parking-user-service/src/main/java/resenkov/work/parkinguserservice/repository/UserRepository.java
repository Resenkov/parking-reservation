package resenkov.work.parkinguserservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import resenkov.work.parkinguserservice.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findAll();

    Optional<User> findByEmail(String email);
}