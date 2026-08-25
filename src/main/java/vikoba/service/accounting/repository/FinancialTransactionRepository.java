package vikoba.service.accounting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.accounting.entity.FinancialTransaction;

import java.util.List;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    List<FinancialTransaction> findByGroupIdOrderByTransactionDateDescIdDesc(Long groupId);
    boolean existsByReference(String reference);
}
