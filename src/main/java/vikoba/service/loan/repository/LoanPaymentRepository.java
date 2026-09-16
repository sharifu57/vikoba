package vikoba.service.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.loan.entity.LoanPayment;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {
}
