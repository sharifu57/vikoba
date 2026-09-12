package vikoba.service.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "share_purchase_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharePurchaseRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_product_id", nullable = false)
    private ShareProduct shareProduct;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "jamii_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal jamiiAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 40)
    private String paymentMethod;

    @Column(length = 100)
    private String paymentReference;

    @Column(columnDefinition = "text")
    private String proofText;

    @Column(length = 255)
    private String proofFileName;

    @Column(length = 120)
    private String proofContentType;

    @Column(columnDefinition = "bytea")
    private byte[] proofFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SharePurchaseRequestStatus status = SharePurchaseRequestStatus.PENDING;

    @Column(length = 500)
    private String reviewReason;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    @Column(name = "accountant_approved_at")
    private LocalDateTime accountantApprovedAt;

    @Column(name = "chair_approved_at")
    private LocalDateTime chairApprovedAt;
}
