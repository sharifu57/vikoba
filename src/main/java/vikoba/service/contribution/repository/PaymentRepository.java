package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.contribution.entity.Payment;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
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
