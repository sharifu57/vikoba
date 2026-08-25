package vikoba.service.contribution.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO for recording a single member contribution
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordContributionRequest {

    /**
     * Member ID to record contribution for
     */
    private Long groupMemberId;

    /**
     * Contribution Period ID
     */
    private Long contributionPeriodId;

    /**
     * Amount paid
     */
    private BigDecimal paidAmount;

    /**
     * Payment method (e.g., Cash, Mobile Money, Bank Transfer)
     */
    private String paymentMethod;

    /**
     * Payment reference (transaction ID, receipt number, etc.)
     */
    private String paymentReference;

    /**
     * Notes/remarks
     */
    private String remarks;
}
