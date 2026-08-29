package vikoba.service.accounting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.accounting.entity.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByGroupIdOrderByCodeAsc(Long groupId);
    Optional<Account> findByIdAndGroupId(Long id, Long groupId);
    boolean existsByGroupIdAndCode(Long groupId, String code);
}
