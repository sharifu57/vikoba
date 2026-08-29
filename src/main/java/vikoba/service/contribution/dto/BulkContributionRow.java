package vikoba.service.contribution.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO for a single row in bulk contribution upload
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkContributionRow {

    /**
     * Member identifier (can be member ID, account number, or name)
     */
    private String memberIdentifier;

    /**
     * Contribution period (e.g., "January 2024", "2024-01")
     */
    private String contributionPeriod;

    /**
     * Amount paid
     */
    private BigDecimal paidAmount;

    /**
     * Payment method
     */
    private String paymentMethod;

    /**
     * Payment reference
     */
    private String paymentReference;

    /**
     * Notes
     */
    private String remarks;

    /**
     * Row number in the Excel file (for error reporting)
     */
    private Integer rowNumber;

    /**
     * Error message if validation fails
     */
    private String errorMessage;

    /**
     * Whether this row was processed successfully
     */
    private Boolean processed;
}
