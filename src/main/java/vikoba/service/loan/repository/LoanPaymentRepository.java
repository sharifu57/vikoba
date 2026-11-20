package vikoba.service.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.loan.entity.LoanPayment;

import java.util.List;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {
    List<LoanPayment> findByLoan_GroupMember_Group_IdOrderByCreatedAtDesc(Long groupId);
}
