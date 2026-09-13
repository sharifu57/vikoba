package vikoba.service.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.GroupRole;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_approval_steps", uniqueConstraints = @UniqueConstraint(name = "uk_loan_approval_step", columnNames = {"loan_id", "step_order"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanApprovalStep extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;
    @Column(name = "step_order", nullable = false) private Integer stepOrder;
    @Enumerated(EnumType.STRING) @Column(name = "required_role", nullable = false, length = 50) private GroupRole requiredRole;
    @Column(nullable = false, length = 150) private String label;
    @Column(name = "approved_at") private LocalDateTime approvedAt;
    @Column(name = "approved_by_member_id") private Long approvedByMemberId;
}
