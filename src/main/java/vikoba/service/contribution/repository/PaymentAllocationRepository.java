package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.contribution.entity.PaymentAllocation;

import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByPaymentId(Long paymentId);
}
