package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.organization.entity.Member;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByMemberNumber(String memberNumber);

    Optional<Member> findByPhone(String phone);

    boolean existsByMemberNumber(String memberNumber);

    boolean existsByNationalId(String nationalId);
}
