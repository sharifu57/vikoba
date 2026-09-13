package vikoba.service.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_approval_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanApprovalEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "loan_id", nullable = false) private Loan loan;
    @Column(name = "step_order") private Integer stepOrder;
    @Column(nullable = false, length = 30) private String action;
    @Column(name = "actor_member_id", nullable = false) private Long actorMemberId;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(name = "acted_at", nullable = false) private LocalDateTime actedAt;
}
