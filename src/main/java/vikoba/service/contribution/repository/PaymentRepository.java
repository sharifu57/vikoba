package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.contribution.entity.Payment;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.group g left join fetch p.groupMember gm where p.id = :id")
    Optional<Payment> findLockedById(@Param("id") Long id);

    @Query("""
            select distinct p from Payment p
            join fetch p.group g join fetch p.groupMember gm join fetch gm.member
            where g.id = :groupId and exists (
                select a.id from PaymentAllocation a where a.payment.id = p.id
                and a.type = vikoba.service.common.enums.PaymentAllocationType.LOAN_REPAYMENT
            )
            order by p.paymentDate desc, p.id desc
            """)
    List<Payment> findLoanRepaymentsByGroupId(@Param("groupId") Long groupId);
    @Query("""
            SELECT DISTINCT p FROM Payment p
            JOIN FETCH p.group g
            LEFT JOIN FETCH p.groupMember gm
            LEFT JOIN FETCH gm.member m
            WHERE g.id = :groupId
            ORDER BY p.paymentDate DESC, p.id DESC
            """)
    List<Payment> findByGroupIdWithMember(@Param("groupId") Long groupId);
}
