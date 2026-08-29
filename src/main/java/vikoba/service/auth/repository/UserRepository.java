package vikoba.service.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.auth.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    @Query("""
                SELECT u
                FROM User u
                LEFT JOIN FETCH u.member
                WHERE u.phone = :phone
            """)
    Optional<User> findByPhoneWithMember(
            @Param("phone") String phone);

    @Query("""
                SELECT u FROM User u
                WHERE u.member.id = :memberId
            """)
    Optional<User> findByMemberId(@Param("memberId") Long memberId);
}
