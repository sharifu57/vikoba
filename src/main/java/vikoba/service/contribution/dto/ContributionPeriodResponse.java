package vikoba.service.contribution.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for contribution period information
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionPeriodResponse {

    /**
     * Period ID
     */
    private Long id;

    /**
     * Contribution type name
     */
    private String contributionTypeName;

    /**
     * Period start date
     */
    private LocalDate periodStart;

    /**
     * Period end date
     */
    private LocalDate periodEnd;

    /**
     * Expected contribution amount
     */
    private BigDecimal expectedAmount;

    /**
     * Period status
     */
    private String status;

    /**
     * Formatted display text (e.g., "January 2024")
     */
    private String displayText;
}
