package vikoba.service.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.auth.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
