package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.contribution.entity.PaymentAllocation;
import vikoba.service.common.enums.PaymentAllocationType;
import vikoba.service.common.enums.PaymentStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByPaymentId(Long paymentId);

    @Query("""
            select allocation from PaymentAllocation allocation
            join fetch allocation.payment payment
            join fetch payment.group
            left join fetch payment.groupMember groupMember
            left join fetch groupMember.member
            where allocation.type = :type and payment.status = :status
            """)
    List<PaymentAllocation> findByTypeAndPaymentStatus(
            @Param("type") PaymentAllocationType type,
            @Param("status") PaymentStatus status);
}
