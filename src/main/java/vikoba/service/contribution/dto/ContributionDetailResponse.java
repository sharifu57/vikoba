package vikoba.service.contribution.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for displaying member contribution details in list view
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionDetailResponse {

    /**
     * Contribution ID
     */
    private Long id;

    /**
     * Member ID
     */
    private Long groupMemberId;

    /**
     * Member name
     */
    private String memberName;

    /**
     * Member phone number
     */
    private String memberPhone;

    /**
     * Member account number
     */
    private String memberAccountNumber;

    /**
     * Contribution type
     */
    private String contributionType;

    /**
     * Period start date
     */
    private LocalDate periodStart;

    /**
     * Period end date
     */
    private LocalDate periodEnd;

    /**
     * Expected amount
     */
    private BigDecimal expectedAmount;

    /**
     * Paid amount
     */
    private BigDecimal paidAmount;

    /**
     * Balance
     */
    private BigDecimal balance;

    /**
     * Payment status
     */
    private String status;

    /**
     * Date when payment was made
     */
    private LocalDateTime paidAt;

    /**
     * Payment method
     */
    private String paymentMethod;

    /**
     * Payment reference
     */
    private String paymentReference;

    /**
     * Remarks
     */
    private String remarks;
}
